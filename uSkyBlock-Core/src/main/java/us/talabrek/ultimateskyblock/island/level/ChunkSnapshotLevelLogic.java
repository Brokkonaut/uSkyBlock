package us.talabrek.ultimateskyblock.island.level;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import us.talabrek.ultimateskyblock.api.async.Callback;
import us.talabrek.ultimateskyblock.handler.WorldGuardHandler;
import us.talabrek.ultimateskyblock.island.BlockLimitLogic;
import us.talabrek.ultimateskyblock.island.task.ChunkSnapShotTask;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Business logic regarding the calculation of level
 */
public class ChunkSnapshotLevelLogic extends CommonLevelLogic {

    public ChunkSnapshotLevelLogic(uSkyBlock plugin, FileConfiguration config) {
        super(plugin, config);
    }

    @Override
    public void calculateScoreAsync(final Location l, final Callback<IslandScore> callback) {
        // TODO: 10/05/2015 - R4zorax: Ensure no overlapping calls to this one happen...
        log.entering(CN, "calculateScoreAsync");
        // is further threading needed here?
        final ProtectedRegion region = WorldGuardHandler.getIslandRegionAt(l);
        if (region == null) {
            return;
        }
        BlockLimitLogic blockLimitLogic = plugin.getBlockLimitLogic();
        new ChunkSnapShotTask(plugin, l, region, new Callback<List<ChunkSnapshot>>() {
            @Override
            public void run() {
                final List<ChunkSnapshot> snapshotsOverworld = getState();
                Location netherLoc = getNetherLocation(l);
                final ProtectedRegion netherRegion = WorldGuardHandler.getNetherRegionAt(netherLoc);
                new ChunkSnapShotTask(plugin, netherLoc, netherRegion, new Callback<List<ChunkSnapshot>>() {
                    @Override
                    public void run() {
                        final List<ChunkSnapshot> snapshotsNether = getState();
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                calculateScoreAndCallback(region, snapshotsOverworld, netherRegion, snapshotsNether, blockLimitLogic, callback);
                            }
                        }.runTaskAsynchronously(plugin);
                    }
                }).runTask(plugin);
            }
        }).runTask(plugin);
    }

    private void calculateScoreAndCallback(ProtectedRegion region, List<ChunkSnapshot> snapshotsOverworld, ProtectedRegion netherRegion, List<ChunkSnapshot> snapshotsNether, BlockLimitLogic blockLimitLogic, Callback<IslandScore> callback) {
        IslandScore islandScore = calculateScore(region, snapshotsOverworld, netherRegion, snapshotsNether, blockLimitLogic);
        callback.setState(islandScore);
        plugin.sync(callback);
        log.exiting(CN, "calculateScoreAsync");
    }

    private IslandScore calculateScore(ProtectedRegion region, List<ChunkSnapshot> snapshotsOverworld, ProtectedRegion netherRegion, List<ChunkSnapshot> snapshotsNether, BlockLimitLogic blockLimitLogic) {
        final BlockCountCollection counts = new BlockCountCollection(scoreMap);
        int minX = region.getMinimumPoint().x();
        int maxX = region.getMaximumPoint().x();
        int minZ = region.getMinimumPoint().z();
        int maxZ = region.getMaximumPoint().z();
        for (int x = minX; x <= maxX; ++x) {
            for (int z = minZ; z <= maxZ; ++z) {
                ChunkSnapshot chunk = getChunkSnapshot(x >> 4, z >> 4, snapshotsOverworld);
                if (chunk == null) {
                    // This should NOT happen!
                    log.log(Level.WARNING, "Missing chunk in snapshot for x,z = " + x + "," + z);
                    continue;
                }
                int cx = (x & 0xf);
                int cz = (z & 0xf);
                for (int y = region.getMinimumPoint().y(); y <= region.getMaximumPoint().y(); y++) {
                    Material blockType = chunk.getBlockType(cx, y, cz);
                    if (blockType == Material.AIR) {
                        continue;
                    }
                    counts.add(blockType);
                    Set<BlockData> limitedBlockStates = blockLimitLogic.getLimitedBlockStatesForMaterial(blockType);
                    if (limitedBlockStates != null && !limitedBlockStates.isEmpty()) {
                        BlockData dataHere = chunk.getBlockData(cx, y, cz);
                        for (BlockData limitedBlockState : limitedBlockStates) {
                            if (dataHere.matches(limitedBlockState)) {
                                counts.addState(limitedBlockState);
                            }
                        }
                    }
                }
            }
        }
        IslandScore islandScore = createIslandScore(counts);
        if (islandScore.getScore() >= activateNetherAtLevel && netherRegion != null && snapshotsNether != null) {
            // Add nether levels
            minX = netherRegion.getMinimumPoint().x();
            maxX = netherRegion.getMaximumPoint().x();
            minZ = netherRegion.getMinimumPoint().z();
            maxZ = netherRegion.getMaximumPoint().z();
            for (int x = minX; x <= maxX; ++x) {
                for (int z = minZ; z <= maxZ; ++z) {
                    ChunkSnapshot chunk = getChunkSnapshot(x >> 4, z >> 4, snapshotsNether);
                    if (chunk == null) {
                        // This should NOT happen!
                        log.log(Level.WARNING, "Missing nether-chunk in snapshot for x,z = " + x + "," + z);
                        continue;
                    }
                    int cx = (x & 0xf);
                    int cz = (z & 0xf);
                    for (int y = 6; y < 120; y++) {
                        Material blockType = chunk.getBlockType(cx, y, cz);
                        if (blockType == Material.AIR) {
                            continue;
                        }
                        counts.add(blockType);
                        Set<BlockData> limitedBlockStates = blockLimitLogic.getLimitedBlockStatesForMaterial(blockType);
                        if (limitedBlockStates != null && !limitedBlockStates.isEmpty()) {
                            BlockData dataHere = chunk.getBlockData(cx, y, cz);
                            for (BlockData limitedBlockState : limitedBlockStates) {
                                if (dataHere.matches(limitedBlockState)) {
                                    counts.addState(limitedBlockState);
                                }
                            }
                        }
                    }
                }
            }
            islandScore = createIslandScore(counts);
        }
        return islandScore;
    }

    private static ChunkSnapshot getChunkSnapshot(int x, int z, List<ChunkSnapshot> snapshots) {
        for (ChunkSnapshot chunk : snapshots) {
            if (chunk.getX() == x && chunk.getZ() == z) {
                return chunk;
            }
        }
        return null;
    }

}
