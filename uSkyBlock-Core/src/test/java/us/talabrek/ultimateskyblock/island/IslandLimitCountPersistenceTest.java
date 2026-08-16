package us.talabrek.ultimateskyblock.island;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.Test;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IslandLimitCountPersistenceTest {

    @Test
    public void distinguishesUnknownFromExplicitZeroAndPersistsAdjustments() throws IOException {
        uSkyBlock plugin = mock(uSkyBlock.class);
        IslandInfo island = new IslandInfo("limit-count-" + UUID.randomUUID(), plugin);
        island.setConfig(new YamlConfiguration());
        File existingFile = File.createTempFile("usb-limit-count", ".yml");
        existingFile.deleteOnExit();
        island.setFile(existingFile);

        assertThat(island.getLimitBlockCount(Material.HOPPER), nullValue());
        assertThat(island.getLimitEntityCount(EntityType.HOPPER_MINECART), nullValue());

        Map<Material, Integer> blocks = new EnumMap<>(Material.class);
        Map<EntityType, Integer> entities = new EnumMap<>(EntityType.class);
        entities.put(EntityType.HOPPER_MINECART, 3);
        island.replaceLimitBlockCounts(blocks, Collections.singleton(Material.HOPPER));
        island.replaceLimitEntityCounts(entities, Collections.singleton(EntityType.HOPPER_MINECART));

        assertThat(island.getLimitBlockCount(Material.HOPPER), is(0));
        assertThat(island.getLimitEntityCount(EntityType.HOPPER_MINECART), is(3));

        island.adjustLimitBlockCount(Material.HOPPER, 1);
        island.adjustLimitEntityCount(EntityType.HOPPER_MINECART, -1);
        assertThat(island.getLimitBlockCount(Material.HOPPER), is(1));
        assertThat(island.getLimitEntityCount(EntityType.HOPPER_MINECART), is(2));
        assertThat(island.isDirty(), is(true));
    }

    @Test
    public void removesNoLongerConfiguredTypesOnSave() throws IOException {
        uSkyBlock plugin = mock(uSkyBlock.class);
        BlockLimitLogic blockLogic = mock(BlockLimitLogic.class);
        CombinedLimitLogic combinedLogic = mock(CombinedLimitLogic.class);
        when(plugin.getBlockLimitLogic()).thenReturn(blockLogic);
        when(plugin.getCombinedLimitLogic()).thenReturn(combinedLogic);
        when(blockLogic.getTrackedMaterials()).thenReturn(Collections.singleton(Material.HOPPER));
        when(combinedLogic.getTrackedEntityTypes()).thenReturn(Collections.singleton(EntityType.HOPPER_MINECART));
        IslandInfo island = new IslandInfo("limit-prune-" + UUID.randomUUID(), plugin);
        YamlConfiguration config = new YamlConfiguration();
        config.set("limits.counts.blocks.HOPPER", 1);
        config.set("limits.counts.blocks.CHEST", 2);
        config.set("limits.counts.entities.HOPPER_MINECART", 3);
        config.set("limits.counts.entities.COW", 4);
        island.setConfig(config);
        File existingFile = File.createTempFile("usb-limit-prune", ".yml");
        existingFile.deleteOnExit();
        island.setFile(existingFile);

        island.save();
        island.saveToFile();

        assertThat(config.contains("limits.counts.blocks.HOPPER"), is(true));
        assertThat(config.contains("limits.counts.blocks.CHEST"), is(false));
        assertThat(config.contains("limits.counts.entities.HOPPER_MINECART"), is(true));
        assertThat(config.contains("limits.counts.entities.COW"), is(false));
    }
}
