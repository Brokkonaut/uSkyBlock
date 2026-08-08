package us.talabrek.ultimateskyblock.integrity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Immutable result of an integrity scan. */
public class IntegrityPlan {
    private final String id;
    private final Map<UUID, PlayerRecord> players;
    private final Map<String, IslandRecord> islands;
    private final List<Issue> issues;
    private final List<Action> actions;
    private final IntegrityManifest manifest;
    private final File reportFile;

    public IntegrityPlan(String id, Map<UUID, PlayerRecord> players, Map<String, IslandRecord> islands,
                         List<Issue> issues, List<Action> actions, IntegrityManifest manifest, File reportFile) {
        this.id = id;
        this.players = Collections.unmodifiableMap(new LinkedHashMap<>(players));
        this.islands = Collections.unmodifiableMap(new LinkedHashMap<>(islands));
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
        this.actions = Collections.unmodifiableList(new ArrayList<>(actions));
        this.manifest = manifest;
        this.reportFile = reportFile;
    }

    public String getId() {
        return id;
    }

    public Map<UUID, PlayerRecord> getPlayers() {
        return players;
    }

    public Map<String, IslandRecord> getIslands() {
        return islands;
    }

    public List<Issue> getIssues() {
        return issues;
    }

    public List<Action> getActions() {
        return actions;
    }

    public IntegrityManifest getManifest() {
        return manifest;
    }

    public File getReportFile() {
        return reportFile;
    }

    public Set<File> getAffectedFiles(File dataFolder, boolean islandChallengeSharing) {
        Set<File> affected = new LinkedHashSet<>();
        for (Action action : actions) {
            addAffectedFiles(action, dataFolder, islandChallengeSharing, affected);
        }
        return affected;
    }

    void addAffectedFiles(Action action, File dataFolder, boolean islandChallengeSharing, Set<File> affected) {
        PlayerRecord player = action.playerId != null ? players.get(action.playerId) : null;
        IslandRecord island = action.islandId != null ? islands.get(action.islandId) : null;
        if (player != null && player.file.exists()) {
            affected.add(player.file);
        }
        if (island != null && island.file.exists()) {
            affected.add(island.file);
        }
        if (action.type == ActionType.RELEASE_ISLAND) {
            if (islandChallengeSharing) {
                File completion = new File(new File(dataFolder, "completion"), action.islandId + ".yml");
                if (completion.exists()) {
                    affected.add(completion);
                }
            }
            File orphans = new File(dataFolder, "orphans.yml");
            if (orphans.exists()) {
                affected.add(orphans);
            }
        }
    }

    public static class PlayerRecord {
        private final UUID uuid;
        private final String name;
        private final String islandId;
        private final Set<String> trustedOn;
        private final Set<String> bannedFrom;
        private final File file;

        public PlayerRecord(UUID uuid, String name, String islandId, File file) {
            this(uuid, name, islandId, Collections.emptySet(), Collections.emptySet(), file);
        }

        public PlayerRecord(UUID uuid, String name, String islandId, Set<String> trustedOn,
                            Set<String> bannedFrom, File file) {
            this.uuid = uuid;
            this.name = name;
            this.islandId = islandId;
            this.trustedOn = Collections.unmodifiableSet(new LinkedHashSet<>(trustedOn));
            this.bannedFrom = Collections.unmodifiableSet(new LinkedHashSet<>(bannedFrom));
            this.file = file;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public String getIslandId() {
            return islandId;
        }

        public Set<String> getTrustedOn() {
            return trustedOn;
        }

        public Set<String> getBannedFrom() {
            return bannedFrom;
        }

        public File getFile() {
            return file;
        }
    }

    public static class IslandRecord {
        private final String id;
        private final UUID leaderId;
        private final String leaderName;
        private final boolean leaderIdentityNeedsPersist;
        private final boolean ignored;
        private final Map<UUID, String> members;
        private final Set<String> invalidMemberKeys;
        private final Set<UUID> trustees;
        private final Set<UUID> banned;
        private final int currentSize;
        private final File file;

        public IslandRecord(String id, UUID leaderId, String leaderName, boolean leaderIdentityNeedsPersist,
                            boolean ignored, Map<UUID, String> members, Set<String> invalidMemberKeys,
                            Set<UUID> trustees, Set<UUID> banned, int currentSize, File file) {
            this.id = id;
            this.leaderId = leaderId;
            this.leaderName = leaderName;
            this.leaderIdentityNeedsPersist = leaderIdentityNeedsPersist;
            this.ignored = ignored;
            this.members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
            this.invalidMemberKeys = Collections.unmodifiableSet(new LinkedHashSet<>(invalidMemberKeys));
            this.trustees = Collections.unmodifiableSet(new LinkedHashSet<>(trustees));
            this.banned = Collections.unmodifiableSet(new LinkedHashSet<>(banned));
            this.currentSize = currentSize;
            this.file = file;
        }

        public String getId() {
            return id;
        }

        public UUID getLeaderId() {
            return leaderId;
        }

        public String getLeaderName() {
            return leaderName;
        }

        public boolean isLeaderIdentityNeedsPersist() {
            return leaderIdentityNeedsPersist;
        }

        public boolean isIgnored() {
            return ignored;
        }

        public Map<UUID, String> getMembers() {
            return members;
        }

        public Set<String> getInvalidMemberKeys() {
            return invalidMemberKeys;
        }

        public Set<UUID> getTrustees() {
            return trustees;
        }

        public Set<UUID> getBanned() {
            return banned;
        }

        public int getCurrentSize() {
            return currentSize;
        }

        public File getFile() {
            return file;
        }
    }

    public static class Issue {
        private final String message;
        private final boolean ignored;

        public Issue(String message, boolean ignored) {
            this.message = message;
            this.ignored = ignored;
        }

        public String getMessage() {
            return message;
        }

        public boolean isIgnored() {
            return ignored;
        }
    }

    public enum ActionType {
        CLEAR_PLAYER_ASSIGNMENT,
        REMOVE_MEMBER_REFERENCE,
        ADD_MEMBER_REFERENCE,
        SET_LEADER_IDENTITY,
        NORMALIZE_PARTY_SIZE,
        REMOVE_TRUST_REFERENCE,
        REMOVE_BAN_REFERENCE,
        RELEASE_ISLAND
    }

    public static class Action {
        private final ActionType type;
        private final String islandId;
        private final UUID playerId;
        private final String memberKey;
        private final String playerName;
        private final boolean leader;
        private final int expectedSize;
        private final String description;

        private Action(ActionType type, String islandId, UUID playerId, String memberKey, String playerName,
                       boolean leader, int expectedSize, String description) {
            this.type = type;
            this.islandId = islandId;
            this.playerId = playerId;
            this.memberKey = memberKey;
            this.playerName = playerName;
            this.leader = leader;
            this.expectedSize = expectedSize;
            this.description = description;
        }

        public static Action of(ActionType type, String islandId, UUID playerId, String memberKey,
                                String playerName, boolean leader, int expectedSize, String description) {
            return new Action(type, islandId, playerId, memberKey, playerName, leader, expectedSize, description);
        }

        public ActionType getType() {
            return type;
        }

        public String getIslandId() {
            return islandId;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public String getMemberKey() {
            return memberKey;
        }

        public String getPlayerName() {
            return playerName;
        }

        public boolean isLeader() {
            return leader;
        }

        public int getExpectedSize() {
            return expectedSize;
        }

        public String getDescription() {
            return description;
        }
    }
}
