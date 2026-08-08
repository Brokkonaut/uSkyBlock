package us.talabrek.ultimateskyblock.handler.task;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.Location;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class IslandRegionCopyTaskTest {

    @Test
    public void plansCompleteNonOverlappingChunkSlices() {
        CuboidRegion region = new CuboidRegion(BlockVector3.at(-17, -64, -1),
                BlockVector3.at(17, 319, 16));

        List<CuboidRegion> slices = IslandRegionCopyTask.splitIntoChunkSlices(region);

        assertThat(slices.size(), is(12));
        long volume = 0;
        for (CuboidRegion slice : slices) {
            assertThat(slice.getMinimumPoint().x() >> 4, is(slice.getMaximumPoint().x() >> 4));
            assertThat(slice.getMinimumPoint().z() >> 4, is(slice.getMaximumPoint().z() >> 4));
            volume += slice.getVolume();
        }
        assertThat(volume, is(region.getVolume()));
    }

    @Test
    public void usesFullOffsetInOverworldAndHorizontalOffsetInNether() {
        Location source = new Location(null, 100, 150, -200);
        Location destination = new Location(null, -300, 170, 400);

        assertThat(IslandRegionCopyTask.calculateOffset(source, destination, true),
                is(BlockVector3.at(-400, 20, 600)));
        assertThat(IslandRegionCopyTask.calculateOffset(source, destination, false),
                is(BlockVector3.at(-400, 0, 600)));
    }
}
