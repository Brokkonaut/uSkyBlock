package us.talabrek.ultimateskyblock.integrity;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IntegrityApplyTaskTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void fileListIsCheckedOnlyOnceAcrossIncrementalVerifyRuns() throws Exception {
        File dataFolder = temporaryFolder.newFolder("data");
        File playerDirectory = new File(dataFolder, "players");
        File islandDirectory = new File(dataFolder, "islands");
        assertTrue(playerDirectory.mkdirs());
        assertTrue(islandDirectory.mkdirs());
        Files.write(new File(playerDirectory, "first.yml").toPath(),
                Collections.singletonList("value: first"), StandardCharsets.UTF_8);
        Files.write(new File(playerDirectory, "second.yml").toPath(),
                Collections.singletonList("value: second"), StandardCharsets.UTF_8);

        YamlConfiguration config = new YamlConfiguration();
        config.set("async.maxMs", 0);
        uSkyBlock plugin = mock(uSkyBlock.class);
        plugin.directoryPlayers = playerDirectory;
        plugin.directoryIslands = islandDirectory;
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getConfig()).thenReturn(config);

        IntegrityManifest manifest = mock(IntegrityManifest.class);
        when(manifest.hasSameFileList(any())).thenReturn(true);
        when(manifest.matchesFile(any(File.class))).thenReturn(true);
        IntegrityPlan plan = new IntegrityPlan("test", Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyList(), Collections.emptyList(), manifest, new File(dataFolder, "report.log"));
        IntegrityApplyTask task = new IntegrityApplyTask(plugin, mock(CommandSender.class), plan,
                new IntegrityReport(dataFolder, "test"), result -> { });

        assertFalse(task.execute());
        assertFalse(task.execute());

        verify(manifest, times(1)).hasSameFileList(any());
        verify(manifest, times(2)).matchesFile(any(File.class));
    }
}
