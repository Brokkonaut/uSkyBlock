package us.talabrek.ultimateskyblock.integrity;

import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.Action;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.ActionType;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.IslandRecord;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.Issue;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.PlayerRecord;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Builds deterministic repair actions from side-effect-free file snapshots. */
public class IntegrityPlanner {

    public Result createPlan(Map<UUID, PlayerRecord> players, Map<String, IslandRecord> islands) {
        Session session = new Session(players, islands);
        while (!session.step()) {
            // The synchronous entry point is retained for tests and other in-memory callers.
        }
        return session.getResult();
    }

    /** Stateful planner used by the server-thread scan task to yield between records. */
    public static class Session {
        private enum Stage { ISLANDS, PLAYERS, FINAL_ISLANDS, BACKLINKS, RELEASES, DONE }

        private final Map<UUID, PlayerRecord> players;
        private final Map<String, IslandRecord> islands;
        private final List<Issue> issues = new ArrayList<>();
        private final List<Action> actions = new ArrayList<>();
        private final Set<String> released = new LinkedHashSet<>();
        private final Set<UUID> ignoredPlayers = new LinkedHashSet<>();
        private final Set<String> membershipRepaired = new LinkedHashSet<>();
        private final Map<String, Set<UUID>> projectedMembers = new LinkedHashMap<>();
        private final Iterator<IslandRecord> islandScan;
        private final Iterator<PlayerRecord> playerScan;
        private final Iterator<IslandRecord> finalIslandScan;
        private final Iterator<PlayerRecord> backlinkScan;
        private Iterator<String> releaseScan;
        private Stage stage = Stage.ISLANDS;
        private Result result;

        public Session(Map<UUID, PlayerRecord> players, Map<String, IslandRecord> islands) {
            this.players = players;
            this.islands = islands;
            this.islandScan = islands.values().iterator();
            this.playerScan = players.values().iterator();
            this.finalIslandScan = islands.values().iterator();
            this.backlinkScan = players.values().iterator();
        }

        /** Processes at most one player or island record and returns whether planning is complete. */
        public boolean step() {
            switch (stage) {
                case ISLANDS:
                    if (islandScan.hasNext()) {
                        processIsland(islandScan.next());
                    } else {
                        stage = Stage.PLAYERS;
                    }
                    break;
                case PLAYERS:
                    if (playerScan.hasNext()) {
                        processPlayer(playerScan.next());
                    } else {
                        stage = Stage.FINAL_ISLANDS;
                    }
                    break;
                case FINAL_ISLANDS:
                    if (finalIslandScan.hasNext()) {
                        finalizeIsland(finalIslandScan.next());
                    } else {
                        stage = Stage.BACKLINKS;
                    }
                    break;
                case BACKLINKS:
                    if (backlinkScan.hasNext()) {
                        processBacklinks(backlinkScan.next());
                    } else {
                        releaseScan = released.iterator();
                        stage = Stage.RELEASES;
                    }
                    break;
                case RELEASES:
                    if (releaseScan.hasNext()) {
                        String islandId = releaseScan.next();
                        actions.add(Action.of(ActionType.RELEASE_ISLAND, islandId, null, null, null,
                                false, -1, "release leaderless island " + islandId));
                    } else {
                        result = new Result(issues, actions, released, projectedMembers);
                        stage = Stage.DONE;
                    }
                    break;
                default:
                    return true;
            }
            return stage == Stage.DONE;
        }

        public Result getResult() {
            if (result == null) {
                throw new IllegalStateException("Integrity planning is not complete");
            }
            return result;
        }

        private void processIsland(IslandRecord island) {
            if (island.isIgnored()) {
                if (island.getLeaderId() != null) {
                    ignoredPlayers.add(island.getLeaderId());
                }
                ignoredPlayers.addAll(island.getMembers().keySet());
            }
            PlayerRecord leader = island.getLeaderId() != null ? players.get(island.getLeaderId()) : null;
            boolean validLeader = leader != null && island.getId().equals(leader.getIslandId());
            if (!validLeader) {
                issues.add(issue(island, "Island " + island.getId() + " has no leader whose player data points to it"));
                if (!island.isIgnored()) {
                    released.add(island.getId());
                }
            }

            Set<UUID> projected = new LinkedHashSet<>(island.getMembers().keySet());
            projectedMembers.put(island.getId(), projected);
            for (String invalidKey : island.getInvalidMemberKeys()) {
                issues.add(issue(island, "Island " + island.getId() + " has invalid member key " + invalidKey));
                if (!island.isIgnored() && validLeader) {
                    actions.add(Action.of(ActionType.REMOVE_MEMBER_REFERENCE, island.getId(), null, invalidKey,
                            null, false, -1, "remove invalid member key " + invalidKey + " from " + island.getId()));
                    membershipRepaired.add(island.getId());
                }
            }

            for (UUID memberId : island.getMembers().keySet()) {
                PlayerRecord member = players.get(memberId);
                if (member == null || !island.getId().equals(member.getIslandId())) {
                    issues.add(issue(island, "Member " + memberId + " on island " + island.getId()
                            + " points to " + (member != null ? member.getIslandId() : "no player data")));
                    if (!island.isIgnored() && validLeader) {
                        projected.remove(memberId);
                    }
                }
            }

            if (validLeader && (island.isLeaderIdentityNeedsPersist()
                    || !leader.getName().equalsIgnoreCase(island.getLeaderName()))) {
                issues.add(issue(island, "Island " + island.getId() + " has incomplete leader identity data"));
                if (!island.isIgnored()) {
                    actions.add(Action.of(ActionType.SET_LEADER_IDENTITY, island.getId(), island.getLeaderId(), null,
                            leader.getName(), true, -1, "normalize leader identity on " + island.getId()));
                }
            }
        }

        private void processPlayer(PlayerRecord player) {
            if (player.getIslandId() == null) {
                return;
            }
            IslandRecord island = islands.get(player.getIslandId());
            if (island == null || released.contains(player.getIslandId())) {
                issues.add(new Issue("Player " + player.getUuid() + " points to missing or released island "
                        + player.getIslandId(), ignoredPlayers.contains(player.getUuid())));
                if (!ignoredPlayers.contains(player.getUuid())) {
                    actions.add(Action.of(ActionType.CLEAR_PLAYER_ASSIGNMENT, player.getIslandId(), player.getUuid(),
                            null, player.getName(), false, -1,
                            "clear invalid island assignment " + player.getIslandId() + " from " + player.getUuid()));
                }
                return;
            }
            Set<UUID> projected = projectedMembers.get(island.getId());
            if (!projected.contains(player.getUuid())) {
                boolean leader = player.getUuid().equals(island.getLeaderId());
                issues.add(issue(island, "Player " + player.getUuid() + " points to island " + island.getId()
                        + " but is missing from its members"));
                if (!island.isIgnored()) {
                    projected.add(player.getUuid());
                    actions.add(Action.of(ActionType.ADD_MEMBER_REFERENCE, island.getId(), player.getUuid(), null,
                            player.getName(), leader, -1,
                            "add " + player.getUuid() + " to island " + island.getId()
                                    + (leader ? " as leader" : " as member")));
                    membershipRepaired.add(island.getId());
                }
            }
        }

        private void finalizeIsland(IslandRecord island) {
            if (released.contains(island.getId())) {
                return;
            }
            Set<UUID> projected = projectedMembers.get(island.getId());
            if (island.isIgnored()) {
                if (island.getCurrentSize() != projected.size()) {
                    issues.add(issue(island, "Island " + island.getId() + " has party.currentSize="
                            + island.getCurrentSize() + " but should have " + projected.size()));
                }
                return;
            }
            for (UUID originalMember : island.getMembers().keySet()) {
                if (!projected.contains(originalMember)) {
                    actions.add(Action.of(ActionType.REMOVE_MEMBER_REFERENCE, island.getId(), originalMember,
                            originalMember.toString(), null, false, -1,
                            "remove stale member " + originalMember + " from " + island.getId()));
                    membershipRepaired.add(island.getId());
                }
            }
            if (island.getCurrentSize() != projected.size()) {
                issues.add(issue(island, "Island " + island.getId() + " has party.currentSize="
                        + island.getCurrentSize() + " but should have " + projected.size()));
                if (!membershipRepaired.contains(island.getId())) {
                    actions.add(Action.of(ActionType.NORMALIZE_PARTY_SIZE, island.getId(), null, null, null,
                            false, projected.size(), "set party.currentSize of " + island.getId()
                                    + " to " + projected.size()));
                }
            }
        }

        private void processBacklinks(PlayerRecord player) {
            for (String islandId : player.getTrustedOn()) {
                if (released.contains(islandId)) {
                    actions.add(Action.of(ActionType.REMOVE_TRUST_REFERENCE, islandId, player.getUuid(), null,
                            player.getName(), false, -1,
                            "remove trust backlink for released island " + islandId + " from " + player.getUuid()));
                }
            }
            for (String islandId : player.getBannedFrom()) {
                if (released.contains(islandId)) {
                    actions.add(Action.of(ActionType.REMOVE_BAN_REFERENCE, islandId, player.getUuid(), null,
                            player.getName(), false, -1,
                            "remove ban backlink for released island " + islandId + " from " + player.getUuid()));
                }
            }
        }

        private Issue issue(IslandRecord island, String message) {
            return new Issue(message, island.isIgnored());
        }
    }

    public static class Result {
        private final List<Issue> issues;
        private final List<Action> actions;
        private final Set<String> releasedIslands;
        private final Map<String, Set<UUID>> projectedMembers;

        private Result(List<Issue> issues, List<Action> actions, Set<String> releasedIslands,
                       Map<String, Set<UUID>> projectedMembers) {
            this.issues = issues;
            this.actions = actions;
            this.releasedIslands = releasedIslands;
            this.projectedMembers = projectedMembers;
        }

        public List<Issue> getIssues() {
            return issues;
        }

        public List<Action> getActions() {
            return actions;
        }

        public Set<String> getReleasedIslands() {
            return releasedIslands;
        }

        public Map<String, Set<UUID>> getProjectedMembers() {
            return projectedMembers;
        }
    }
}
