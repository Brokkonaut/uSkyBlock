package us.talabrek.ultimateskyblock.island.level;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import us.talabrek.ultimateskyblock.island.level.yml.LevelConfigYmlReader;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;

public class LimitedMaterialCountTest {

    @Test
    public void tracksExactLimitedMaterialsIndependentlyFromScoreGrouping() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("general.default", 10d);
        config.set("general.pointsPerLevel", 100);
        BlockLevelConfigMap scoreMap = new LevelConfigYmlReader().readLevelConfig(config);
        BlockCountCollection counts = new BlockCountCollection(scoreMap, Collections.singleton(Material.HOPPER));

        counts.add(Material.HOPPER);
        counts.add(Material.HOPPER);
        counts.add(Material.STONE);

        assertThat(counts.getLimitedMaterialCounts(), hasEntry(Material.HOPPER, 2));
        assertThat(counts.getLimitedMaterialCounts().containsKey(Material.STONE), is(false));
    }

    @Test
    public void canTrackNetherRawCountWithoutChangingScoreCount() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("general.default", 10d);
        config.set("general.pointsPerLevel", 100);
        BlockLevelConfigMap scoreMap = new LevelConfigYmlReader().readLevelConfig(config);
        BlockCountCollection counts = new BlockCountCollection(scoreMap, Collections.singleton(Material.HOPPER));

        counts.addLimited(Material.HOPPER);

        assertThat(counts.getLimitedMaterialCounts(), hasEntry(Material.HOPPER, 1));
        assertThat(counts.calculateScore(100).isEmpty(), is(true));
    }
}
