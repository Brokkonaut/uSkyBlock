package us.talabrek.ultimateskyblock.challenge;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.io.File;
import java.util.Map;
import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChallengeCompletionLogicTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void moveReplacesLoadedDestinationAndDoesNotRecreateSourceOnFlush() throws Exception {
        Fixture fixture = fixture("island");
        ChallengeCompletionLogic logic = fixture.createLogic();
        long cooldown = System.currentTimeMillis() + 60_000;
        logic.getIslandChallenges("source").put("cobblestone",
                new ChallengeCompletion("cobblestone", cooldown, 7, 3));
        logic.getIslandChallenges("destination").put("stale",
                new ChallengeCompletion("stale", -1, 99, 99));

        assertThat(logic.moveIslandChallenges("source", "destination"), is(true));
        assertCompletion(logic.getIslandChallenges("destination").get("cobblestone"), cooldown, 7, 3);
        assertThat(logic.getIslandChallenges("destination").get("stale"), nullValue());

        logic.shutdown();
        assertThat(fixture.completionFile("source").exists(), is(false));
        assertThat(fixture.completionFile("destination").exists(), is(true));

        ChallengeCompletionLogic reloaded = fixture.createLogic();
        assertCompletion(reloaded.getIslandChallenges("destination").get("cobblestone"), cooldown, 7, 3);
        assertThat(reloaded.getIslandChallenges("source").isEmpty(), is(true));
    }

    @Test
    public void moveLoadsSourceFromDiskAndOverwritesDestinationFile() throws Exception {
        Fixture fixture = fixture("island");
        long cooldown = System.currentTimeMillis() + 120_000;
        ChallengeCompletionLogic writer = fixture.createLogic();
        writer.getIslandChallenges("source").put("cobblestone",
                new ChallengeCompletion("cobblestone", cooldown, 11, 4));
        writer.getIslandChallenges("destination").put("stale",
                new ChallengeCompletion("stale", -1, 2, 2));
        writer.shutdown();

        ChallengeCompletionLogic logic = fixture.createLogic();
        assertThat(logic.moveIslandChallenges("source", "destination"), is(true));
        logic.shutdown();

        ChallengeCompletionLogic reloaded = fixture.createLogic();
        Map<String, ChallengeCompletion> destination = reloaded.getIslandChallenges("destination");
        assertCompletion(destination.get("cobblestone"), cooldown, 11, 4);
        assertThat(destination.get("stale").getTimesCompleted(), is(0));
        assertThat(fixture.completionFile("source").exists(), is(false));
    }

    @Test
    public void playerSharedProgressIsNotMoved() throws Exception {
        Fixture fixture = fixture("player");
        YamlConfiguration source = new YamlConfiguration();
        source.set("cobblestone.timesCompleted", 5);
        source.save(fixture.completionFile("source"));
        YamlConfiguration destination = new YamlConfiguration();
        destination.set("stale.timesCompleted", 8);
        destination.save(fixture.completionFile("destination"));

        ChallengeCompletionLogic logic = fixture.createLogic();
        assertThat(logic.moveIslandChallenges("source", "destination"), is(true));

        assertThat(fixture.completionFile("source").exists(), is(true));
        assertThat(YamlConfiguration.loadConfiguration(fixture.completionFile("source"))
                .getInt("cobblestone.timesCompleted"), is(5));
        assertThat(YamlConfiguration.loadConfiguration(fixture.completionFile("destination"))
                .getInt("stale.timesCompleted"), is(8));
    }

    private void assertCompletion(ChallengeCompletion completion, long cooldown, int total, int inCooldown) {
        assertThat(completion.getCooldownUntil(), is(cooldown));
        assertThat(completion.getTimesCompleted(), is(total));
        assertThat(completion.getTimesCompletedInCooldown(), is(inCooldown));
    }

    private Fixture fixture(String sharing) throws Exception {
        File dataFolder = temporaryFolder.newFolder();
        uSkyBlock plugin = mock(uSkyBlock.class);
        YamlConfiguration pluginConfig = new YamlConfiguration();
        pluginConfig.set("options.advanced.completionCache", "maximumSize=200");
        when(plugin.getConfig()).thenReturn(pluginConfig);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger(getClass().getName()));
        when(plugin.getIslandInfo(any(String.class))).thenReturn(null);

        ChallengeLogic challengeLogic = mock(ChallengeLogic.class);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, ChallengeCompletion> map = invocation.getArgument(0);
            map.put("cobblestone", new ChallengeCompletion("cobblestone"));
            map.put("stale", new ChallengeCompletion("stale"));
            return null;
        }).when(challengeLogic).populateChallenges(any());
        when(plugin.getChallengeLogic()).thenReturn(challengeLogic);

        YamlConfiguration challengeConfig = new YamlConfiguration();
        challengeConfig.set("challengeSharing", sharing);
        return new Fixture(plugin, challengeConfig, dataFolder);
    }

    private static class Fixture {
        private final uSkyBlock plugin;
        private final YamlConfiguration challengeConfig;
        private final File dataFolder;

        private Fixture(uSkyBlock plugin, YamlConfiguration challengeConfig, File dataFolder) {
            this.plugin = plugin;
            this.challengeConfig = challengeConfig;
            this.dataFolder = dataFolder;
        }

        private ChallengeCompletionLogic createLogic() {
            return new ChallengeCompletionLogic(plugin, challengeConfig);
        }

        private File completionFile(String id) {
            File completionFolder = new File(dataFolder, "completion");
            completionFolder.mkdirs();
            return new File(completionFolder, id + ".yml");
        }
    }
}
