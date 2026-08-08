package us.talabrek.ultimateskyblock.integrity;

import org.junit.Test;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.Action;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.ActionType;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.IslandRecord;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.PlayerRecord;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IntegrityPlannerTest {
    private static final UUID LEADER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    public void consistentIslandProducesNoActions() {
        Map<UUID, PlayerRecord> players = players(player(LEADER, "Leader", "100,200"),
                player(MEMBER, "Member", "100,200"));
        IslandRecord island = island("100,200", LEADER, members(LEADER, MEMBER), 2, false);

        IntegrityPlanner.Result result = new IntegrityPlanner().createPlan(players, islands(island));

        assertTrue(result.getIssues().isEmpty());
        assertTrue(result.getActions().isEmpty());
    }

    @Test
    public void staleMemberIsRemovedOnlyFromWrongIsland() {
        Map<UUID, PlayerRecord> players = players(player(LEADER, "Leader", "100,200"),
                player(MEMBER, "Member", "300,400"), player(OTHER, "Other", "300,400"));
        IslandRecord wrong = island("100,200", LEADER, members(LEADER, MEMBER), 2, false);
        IslandRecord desired = island("300,400", OTHER, members(OTHER, MEMBER), 2, false);

        IntegrityPlanner.Result result = new IntegrityPlanner().createPlan(players, islands(wrong, desired));

        assertEquals(1, count(result.getActions(), ActionType.REMOVE_MEMBER_REFERENCE, "100,200", MEMBER));
        assertEquals(0, count(result.getActions(), ActionType.CLEAR_PLAYER_ASSIGNMENT, null, MEMBER));
        assertEquals("300,400", players.get(MEMBER).getIslandId());
    }

    @Test
    public void missingMemberAndLeaderEntriesAreRestoredWithCorrectRoles() {
        Map<UUID, PlayerRecord> players = players(player(LEADER, "Leader", "100,200"),
                player(MEMBER, "Member", "100,200"));
        IslandRecord island = island("100,200", LEADER, Collections.emptyMap(), 0, false);

        List<Action> actions = new IntegrityPlanner().createPlan(players, islands(island)).getActions();

        Action leader = find(actions, ActionType.ADD_MEMBER_REFERENCE, "100,200", LEADER);
        Action member = find(actions, ActionType.ADD_MEMBER_REFERENCE, "100,200", MEMBER);
        assertTrue(leader.isLeader());
        assertFalse(member.isLeader());
        assertEquals(0, count(actions, ActionType.NORMALIZE_PARTY_SIZE, "100,200", null));
    }

    @Test
    public void playerListedOnSeveralIslandsRemainsOnlyOnDesiredIsland() {
        Map<UUID, PlayerRecord> players = players(player(LEADER, "Leader", "100,200"),
                player(MEMBER, "Member", "100,200"), player(OTHER, "Other", "300,400"));
        IslandRecord desired = island("100,200", LEADER, members(LEADER, MEMBER), 2, false);
        IslandRecord duplicate = island("300,400", OTHER, members(OTHER, MEMBER), 2, false);

        List<Action> actions = new IntegrityPlanner().createPlan(players, islands(desired, duplicate)).getActions();

        assertEquals(1, count(actions, ActionType.REMOVE_MEMBER_REFERENCE, "300,400", MEMBER));
        assertEquals(0, count(actions, ActionType.REMOVE_MEMBER_REFERENCE, "100,200", MEMBER));
    }

    @Test
    public void invalidLeaderReleasesIslandAndCleansAllReverseReferences() {
        Map<UUID, PlayerRecord> players = players(player(LEADER, "Leader", "900,900"),
                player(MEMBER, "Member", "100,200", Collections.emptySet(), set("100,200")),
                player(OTHER, "Other", null, set("100,200"), Collections.emptySet()));
        IslandRecord broken = new IslandRecord("100,200", LEADER, "Leader", false, false,
                members(LEADER, MEMBER), Collections.emptySet(), set(OTHER), set(MEMBER), 2,
                new File("100,200.yml"));

        IntegrityPlanner.Result result = new IntegrityPlanner().createPlan(players, islands(broken));

        assertTrue(result.getReleasedIslands().contains("100,200"));
        assertEquals(1, count(result.getActions(), ActionType.CLEAR_PLAYER_ASSIGNMENT, "100,200", MEMBER));
        assertEquals(0, count(result.getActions(), ActionType.CLEAR_PLAYER_ASSIGNMENT, "100,200", LEADER));
        assertEquals(1, count(result.getActions(), ActionType.REMOVE_TRUST_REFERENCE, "100,200", OTHER));
        assertEquals(1, count(result.getActions(), ActionType.REMOVE_BAN_REFERENCE, "100,200", MEMBER));
        assertEquals(1, count(result.getActions(), ActionType.RELEASE_ISLAND, "100,200", null));
    }

    @Test
    public void missingIslandAssignmentIsClearedWithPlayerHomeByApplyAction() {
        Map<UUID, PlayerRecord> players = players(player(MEMBER, "Member", "999,999"));

        List<Action> actions = new IntegrityPlanner().createPlan(players, Collections.emptyMap()).getActions();

        assertEquals(1, count(actions, ActionType.CLEAR_PLAYER_ASSIGNMENT, "999,999", MEMBER));
    }

    @Test
    public void ignoredIslandAndAssociatedPlayersAreNeverChanged() {
        Map<UUID, PlayerRecord> players = players(player(LEADER, "Leader", "999,999"),
                player(MEMBER, "Member", null));
        IslandRecord ignored = island("100,200", LEADER, members(LEADER, MEMBER), 99, true);

        IntegrityPlanner.Result result = new IntegrityPlanner().createPlan(players, islands(ignored));

        assertFalse(result.getIssues().isEmpty());
        assertTrue(result.getIssues().stream().allMatch(IntegrityPlan.Issue::isIgnored));
        assertTrue(result.getActions().isEmpty());
    }

    @Test
    public void invalidMemberKeysAndWrongPartySizeAreNormalized() {
        Map<UUID, PlayerRecord> players = players(player(LEADER, "Leader", "100,200"));
        IslandRecord island = new IslandRecord("100,200", LEADER, "Leader", false, false,
                members(LEADER), set("not-a-uuid"), Collections.emptySet(), Collections.emptySet(), 42,
                new File("100,200.yml"));

        List<Action> actions = new IntegrityPlanner().createPlan(players, islands(island)).getActions();

        assertEquals(1, count(actions, ActionType.REMOVE_MEMBER_REFERENCE, "100,200", null));
        assertEquals(0, count(actions, ActionType.NORMALIZE_PARTY_SIZE, "100,200", null));
    }

    @Test
    public void wrongPartySizeWithoutMembershipChangesGetsDedicatedAction() {
        Map<UUID, PlayerRecord> players = players(player(LEADER, "Leader", "100,200"));
        IslandRecord island = island("100,200", LEADER, members(LEADER), 42, false);

        List<Action> actions = new IntegrityPlanner().createPlan(players, islands(island)).getActions();

        assertEquals(1, count(actions, ActionType.NORMALIZE_PARTY_SIZE, "100,200", null));
        assertEquals(1, find(actions, ActionType.NORMALIZE_PARTY_SIZE, "100,200", null).getExpectedSize());
    }

    private static PlayerRecord player(UUID uuid, String name, String island) {
        return new PlayerRecord(uuid, name, island, new File(uuid + ".yml"));
    }

    private static PlayerRecord player(UUID uuid, String name, String island, Set<String> trusted,
                                       Set<String> banned) {
        return new PlayerRecord(uuid, name, island, trusted, banned, new File(uuid + ".yml"));
    }

    private static IslandRecord island(String id, UUID leader, Map<UUID, String> members, int size,
                                       boolean ignored) {
        return new IslandRecord(id, leader, "Leader", false, ignored, members, Collections.emptySet(),
                Collections.emptySet(), Collections.emptySet(), size, new File(id + ".yml"));
    }

    private static Map<UUID, PlayerRecord> players(PlayerRecord... records) {
        Map<UUID, PlayerRecord> result = new LinkedHashMap<>();
        for (PlayerRecord record : records) {
            result.put(record.getUuid(), record);
        }
        return result;
    }

    private static Map<String, IslandRecord> islands(IslandRecord... records) {
        Map<String, IslandRecord> result = new LinkedHashMap<>();
        for (IslandRecord record : records) {
            result.put(record.getId(), record);
        }
        return result;
    }

    private static Map<UUID, String> members(UUID... uuids) {
        Map<UUID, String> result = new LinkedHashMap<>();
        for (UUID uuid : uuids) {
            result.put(uuid, uuid.toString());
        }
        return result;
    }

    @SafeVarargs
    private static <T> Set<T> set(T... values) {
        Set<T> result = new LinkedHashSet<>();
        Collections.addAll(result, values);
        return result;
    }

    private static int count(List<Action> actions, ActionType type, String island, UUID player) {
        int count = 0;
        for (Action action : actions) {
            if (action.getType() == type && (island == null || island.equals(action.getIslandId()))
                    && (player == null || player.equals(action.getPlayerId()))) {
                count++;
            }
        }
        return count;
    }

    private static Action find(List<Action> actions, ActionType type, String island, UUID player) {
        for (Action action : actions) {
            if (action.getType() == type && island.equals(action.getIslandId())
                    && (player == null || player.equals(action.getPlayerId()))) {
                return action;
            }
        }
        throw new AssertionError("Missing action " + type + " for " + island + "/" + player);
    }
}
