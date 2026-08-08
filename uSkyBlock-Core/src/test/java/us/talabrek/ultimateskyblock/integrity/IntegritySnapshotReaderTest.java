package us.talabrek.ultimateskyblock.integrity;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.IslandRecord;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.uuid.PlayerDB;

import java.io.File;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IntegritySnapshotReaderTest {
    private static final UUID LEADER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private PlayerDB playerDB;
    private IntegritySnapshotReader reader;

    @Before
    public void setup() {
        uSkyBlock plugin = mock(uSkyBlock.class);
        playerDB = mock(PlayerDB.class);
        when(plugin.getPlayerDB()).thenReturn(playerDB);
        reader = new IntegritySnapshotReader(plugin);
    }

    @Test
    public void validLeaderUuidWinsOverStaleNameAndInvalidMembersAreRecorded() throws Exception {
        when(playerDB.getName(LEADER)).thenReturn("CurrentLeader");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("party.leader", "OldLeader");
        yaml.set("party.leader-uuid", LEADER.toString());
        yaml.set("party.members." + LEADER + ".name", "CurrentLeader");
        yaml.set("party.members.not-a-uuid.name", "Broken");
        yaml.set("party.currentSize", 9);
        File file = new File(temporaryFolder.getRoot(), "100,200.yml");
        yaml.save(file);

        IslandRecord record = reader.readIsland(file);

        assertEquals(LEADER, record.getLeaderId());
        assertEquals("CurrentLeader", record.getLeaderName());
        assertTrue(record.isLeaderIdentityNeedsPersist());
        assertTrue(record.getInvalidMemberKeys().contains("not-a-uuid"));
        assertEquals(9, record.getCurrentSize());
        verify(playerDB, never()).getUUIDFromName("OldLeader");
    }

    @Test
    public void missingLeaderUuidIsResolvedThroughPlayerDatabase() throws Exception {
        when(playerDB.getUUIDFromName("Leader")).thenReturn(LEADER);
        when(playerDB.getName(LEADER)).thenReturn("Leader");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("party.leader", "Leader");
        File file = new File(temporaryFolder.getRoot(), "100,200.yml");
        yaml.save(file);

        IslandRecord record = reader.readIsland(file);

        assertEquals(LEADER, record.getLeaderId());
        assertTrue(record.isLeaderIdentityNeedsPersist());
    }

    @Test(expected = InvalidConfigurationException.class)
    public void mismatchingPlayerFilenameAndUuidIsFatal() throws Exception {
        UUID fileId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("player.uuid", LEADER.toString());
        File file = new File(temporaryFolder.getRoot(), fileId + ".yml");
        yaml.save(file);

        reader.readPlayer(file);
    }

    @Test(expected = InvalidConfigurationException.class)
    public void playerFileWithoutAnyUuidIsFatal() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "ambiguous-name.yml");
        new YamlConfiguration().save(file);

        reader.readPlayer(file);
    }

    @Test(expected = InvalidConfigurationException.class)
    public void malformedPlayerIslandCoordinatesAreFatal() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("player.uuid", LEADER.toString());
        yaml.set("player.islandX", "broken");
        yaml.set("player.islandY", 150);
        yaml.set("player.islandZ", 200);
        File file = new File(temporaryFolder.getRoot(), LEADER + ".yml");
        yaml.save(file);

        reader.readPlayer(file);
    }
}
