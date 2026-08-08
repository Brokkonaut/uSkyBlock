package us.talabrek.ultimateskyblock.handler.task;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.SideEffectSet;
import org.bukkit.Location;
import org.bukkit.World;
import us.talabrek.ultimateskyblock.async.IncrementalRunnable;
import us.talabrek.ultimateskyblock.handler.WorldEditHandler;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Copies complete island regions in chunk-sized slices without removing the source.
 * Source cleanup is deliberately a separate step so a copy failure never releases the old island.
 */
public class IslandRegionCopyTask extends IncrementalRunnable {
    private final Queue<CopySlice> slices = new ArrayDeque<>();
    private Throwable failure;

    public IslandRegionCopyTask(uSkyBlock plugin, Location source, Location destination,
                                Region overworldRegion, Region netherRegion, Runnable onCompletion) {
        super(plugin, onCompletion);
        addRegion(source.getWorld(), overworldRegion, calculateOffset(source, destination, true));
        if (netherRegion != null) {
            addRegion(plugin.getWorldManager().getNetherWorld(), netherRegion,
                    calculateOffset(source, destination, false));
        }
    }

    private void addRegion(World world, Region region, BlockVector3 offset) {
        for (CuboidRegion slice : splitIntoChunkSlices(region)) {
            slices.add(new CopySlice(world, slice, offset));
        }
    }

    static BlockVector3 calculateOffset(Location source, Location destination, boolean includeY) {
        return BlockVector3.at(destination.getBlockX() - source.getBlockX(),
                includeY ? destination.getBlockY() - source.getBlockY() : 0,
                destination.getBlockZ() - source.getBlockZ());
    }

    static List<CuboidRegion> splitIntoChunkSlices(Region region) {
        List<CuboidRegion> result = new ArrayList<>();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        for (int chunkX = min.x() >> 4; chunkX <= max.x() >> 4; chunkX++) {
            for (int chunkZ = min.z() >> 4; chunkZ <= max.z() >> 4; chunkZ++) {
                int minX = Math.max(min.x(), chunkX << 4);
                int maxX = Math.min(max.x(), (chunkX << 4) + 15);
                int minZ = Math.max(min.z(), chunkZ << 4);
                int maxZ = Math.min(max.z(), (chunkZ << 4) + 15);
                CuboidRegion slice = new CuboidRegion(region.getWorld(),
                        BlockVector3.at(minX, min.y(), minZ), BlockVector3.at(maxX, max.y(), maxZ));
                result.add(slice);
            }
        }
        return result;
    }

    @Override
    protected boolean execute() {
        while (!slices.isEmpty()) {
            CopySlice slice = slices.remove();
            try {
                copy(slice);
            } catch (Throwable throwable) {
                failure = throwable;
                slices.clear();
                return true;
            }
            if (!tick()) {
                break;
            }
        }
        return slices.isEmpty();
    }

    private void copy(CopySlice slice) throws WorldEditException {
        BukkitWorld world = new BukkitWorld(slice.world);
        slice.world.getChunkAt(slice.region.getMinimumPoint().x() >> 4,
                slice.region.getMinimumPoint().z() >> 4);
        BlockVector3 destination = slice.region.getMinimumPoint().add(slice.offset);
        try (EditSession editSession = WorldEditHandler.createEditSession(world, -1)) {
            editSession.setSideEffectApplier(SideEffectSet.none());
            ForwardExtentCopy copy = new ForwardExtentCopy(world, slice.region,
                    slice.region.getMinimumPoint(), editSession, destination);
            copy.setCopyingBiomes(true);
            copy.setCopyingEntities(true);
            Operations.complete(copy);
        }
    }

    public Throwable getFailure() {
        return failure;
    }

    private static class CopySlice {
        private final World world;
        private final Region region;
        private final BlockVector3 offset;

        private CopySlice(World world, Region region, BlockVector3 offset) {
            this.world = world;
            this.region = region;
            this.offset = offset;
        }
    }
}
