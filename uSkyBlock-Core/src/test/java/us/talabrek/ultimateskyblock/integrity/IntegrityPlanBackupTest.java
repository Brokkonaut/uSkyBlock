package us.talabrek.ultimateskyblock.integrity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.Action;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.ActionType;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.IslandRecord;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.PlayerRecord;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IntegrityPlanBackupTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void releaseBacksUpExactlyMutatedMetadataFiles() throws Exception {
        File root = temporaryFolder.getRoot();
        File playerFile = touch(root, "players/11111111-1111-1111-1111-111111111111.yml");
        File islandFile = touch(root, "islands/100,200.yml");
        File completionFile = touch(root, "completion/100,200.yml");
        File unrelatedCompletion = touch(root, "completion/300,400.yml");
        File orphans = touch(root, "orphans.yml");
        UUID playerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Map<UUID, PlayerRecord> players = new LinkedHashMap<>();
        players.put(playerId, new PlayerRecord(playerId, "Leader", "100,200", playerFile));
        Map<String, IslandRecord> islands = new LinkedHashMap<>();
        islands.put("100,200", new IslandRecord("100,200", playerId, "Leader", false, false,
                Collections.emptyMap(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
                0, islandFile));
        Action clear = Action.of(ActionType.CLEAR_PLAYER_ASSIGNMENT, "100,200", playerId, null,
                "Leader", false, -1, "clear");
        Action release = Action.of(ActionType.RELEASE_ISLAND, "100,200", null, null,
                null, false, -1, "release");
        IntegrityPlan plan = new IntegrityPlan("test", players, islands, Collections.emptyList(),
                java.util.Arrays.asList(clear, release), null, new File(root, "report.log"));

        Set<File> affected = plan.getAffectedFiles(root, true);

        assertEquals(4, affected.size());
        assertTrue(affected.contains(playerFile));
        assertTrue(affected.contains(islandFile));
        assertTrue(affected.contains(completionFile));
        assertTrue(affected.contains(orphans));
        assertTrue(unrelatedCompletion.exists());
    }

    @Test
    public void playerChallengeSharingDoesNotBackUpIslandCompletion() throws Exception {
        File root = temporaryFolder.newFolder("player-sharing");
        File islandFile = touch(root, "islands/100,200.yml");
        File completionFile = touch(root, "completion/100,200.yml");
        File orphans = touch(root, "orphans.yml");
        Map<String, IslandRecord> islands = new LinkedHashMap<>();
        islands.put("100,200", new IslandRecord("100,200", null, "", false, false,
                Collections.emptyMap(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
                0, islandFile));
        Action release = Action.of(ActionType.RELEASE_ISLAND, "100,200", null, null,
                null, false, -1, "release");
        IntegrityPlan plan = new IntegrityPlan("test", Collections.emptyMap(), islands,
                Collections.emptyList(), Collections.singletonList(release), null, new File(root, "report.log"));

        Set<File> affected = plan.getAffectedFiles(root, false);

        assertEquals(2, affected.size());
        assertTrue(affected.contains(islandFile));
        assertTrue(affected.contains(orphans));
        assertTrue(completionFile.exists());
    }

    private static File touch(File root, String relative) throws Exception {
        File file = new File(root, relative);
        assertTrue(file.getParentFile().exists() || file.getParentFile().mkdirs());
        assertTrue(file.createNewFile());
        return file;
    }
}
