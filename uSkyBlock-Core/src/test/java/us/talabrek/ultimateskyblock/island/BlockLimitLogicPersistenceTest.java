package us.talabrek.ultimateskyblock.island;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BlockLimitLogicPersistenceTest {

    @Test
    public void preservesIndividualUnknownAndLimitSemanticsWhileTrackingCombinedMaterials() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("options.island.block-limits.enabled", true);
        config.set("options.island.block-limits.hopper", "1");
        uSkyBlock plugin = mock(uSkyBlock.class);
        when(plugin.getConfig()).thenReturn(config);
        BlockLimitLogic logic = new BlockLimitLogic(plugin, Collections.singleton(Material.CHEST));
        IslandInfo island = mock(IslandInfo.class);
        Location location = mock(Location.class);
        BlockData hopper = mock(BlockData.class);
        when(island.getIslandLocation()).thenReturn(location);
        when(plugin.getIslandInfo(location)).thenReturn(island);
        when(hopper.getMaterial()).thenReturn(Material.HOPPER);
        when(island.getLimitBlockCount(Material.HOPPER)).thenReturn(null);

        assertThat(logic.getLimit(Material.HOPPER), is(1));
        assertThat(logic.canPlace(hopper, island).canPlace(), is(BlockLimitLogic.CanPlace.UNCERTAIN));
        when(island.getLimitBlockCount(Material.HOPPER)).thenReturn(0);
        assertThat(logic.canPlace(hopper, island).canPlace(), is(BlockLimitLogic.CanPlace.YES));
        when(island.getLimitBlockCount(Material.HOPPER)).thenReturn(1);
        assertThat(logic.canPlace(hopper, island).canPlace(), is(BlockLimitLogic.CanPlace.NO));
        assertThat(logic.isTrackedMaterial(Material.CHEST), is(true));

        BlockData chest = mock(BlockData.class);
        when(chest.getMaterial()).thenReturn(Material.CHEST);
        logic.incBlockCount(location, chest);
        verify(island).adjustLimitBlockCount(Material.CHEST, 1);
    }
}
