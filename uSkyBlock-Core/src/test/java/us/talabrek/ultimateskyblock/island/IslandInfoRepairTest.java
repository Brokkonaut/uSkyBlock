package us.talabrek.ultimateskyblock.island;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.io.File;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class IslandInfoRepairTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void useTemporaryIslandDirectory() {
        IslandInfo.setDirectory(temporaryFolder.getRoot());
    }

    @After
    public void restoreIslandDirectory() {
        IslandInfo.setDirectory(new File("."));
    }

    @Test
    public void repairOperationsPersistRolesAndExactMemberCountsWithoutEvents() throws Exception {
        IslandInfo island = new IslandInfo("100,200", mock(uSkyBlock.class));
        island.setConfig(new YamlConfiguration());
        UUID leader = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID member = UUID.fromString("22222222-2222-2222-2222-222222222222");

        island.repairAddMemberReference(leader, "Leader", true);
        island.repairAddMemberReference(member, "Member", false);

        YamlConfiguration stored = YamlConfiguration.loadConfiguration(
                new File(temporaryFolder.getRoot(), "100,200.yml"));
        assertEquals(2, stored.getInt("party.currentSize"));
        assertEquals(leader.toString(), stored.getString("party.leader-uuid"));
        assertTrue(stored.getBoolean("party.members." + leader + ".canInviteOthers"));
        assertFalse(stored.getBoolean("party.members." + member + ".canInviteOthers"));

        island.repairRemoveMemberReference(member.toString());

        stored = YamlConfiguration.loadConfiguration(new File(temporaryFolder.getRoot(), "100,200.yml"));
        assertEquals(1, stored.getInt("party.currentSize"));
        assertFalse(stored.contains("party.members." + member));
    }
}
