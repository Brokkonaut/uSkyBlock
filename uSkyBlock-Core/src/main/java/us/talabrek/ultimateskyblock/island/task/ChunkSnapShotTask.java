package us.talabrek.ultimateskyblock.island.task;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import us.talabrek.ultimateskyblock.api.async.Callback;
import us.talabrek.ultimateskyblock.async.IncrementalRunnable;
import us.talabrek.ultimateskyblock.handler.WorldEditHandler;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Incremental task for snapshotting chunks.
 */
public class ChunkSnapShotTask extends IncrementalRunnable {
    private final Location location;
    private final ProtectedRegion region;
    private final List<BlockVector2> chunks;
    private List<ChunkSnapshot> snapshots = new ArrayList<>();
    private final Set<EntityType> trackedEntityTypes;
    private final Map<EntityType, Integer> entityCounts;

    public ChunkSnapShotTask(uSkyBlock plugin, Location location, ProtectedRegion region, final Callback<List<ChunkSnapshot>> callback) {
        this(plugin, location, region, java.util.Collections.emptySet(), new HashMap<>(), callback);
    }

    public ChunkSnapShotTask(uSkyBlock plugin, Location location, ProtectedRegion region,
                             Set<EntityType> trackedEntityTypes, Map<EntityType, Integer> entityCounts,
                             final Callback<List<ChunkSnapshot>> callback) {
        super(plugin, callback);
        this.location = location;
        this.region = region;
        this.trackedEntityTypes = trackedEntityTypes;
        this.entityCounts = entityCounts;
        if (region != null) {
            chunks = new ArrayList<>(WorldEditHandler.getChunks(new CuboidRegion(region.getMinimumPoint(), region.getMaximumPoint())));
        } else {
            chunks = new ArrayList<>();
        }
        callback.setState(snapshots);
    }

    @Override
    protected boolean execute() {
        while (!chunks.isEmpty()) {
            BlockVector2 chunkVector = chunks.remove(0);
            Chunk chunk = location.getWorld().getChunkAt(chunkVector.x(), chunkVector.z());
            snapshots.add(chunk.getChunkSnapshot(false, false, false));
            for (Entity entity : chunk.getEntities()) {
                Location entityLocation = entity.getLocation();
                BlockVector3 position = BlockVector3.at(
                        entityLocation.getBlockX(), entityLocation.getBlockY(), entityLocation.getBlockZ());
                if (region.contains(position) && trackedEntityTypes.contains(entity.getType())) {
                    entityCounts.compute(entity.getType(), (ignored, count) -> count == null ? 1
                            : (int) Math.min(Integer.MAX_VALUE, (long) count + 1));
                }
            }
            if (!tick()) {
                break;
            }
        }
        return chunks.isEmpty();
    }

}
