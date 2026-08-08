package us.talabrek.ultimateskyblock.world;

import org.apache.commons.lang.Validate;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Class responsible for regenerating chunks.
 */
public class ChunkRegenerator {
    private final uSkyBlock plugin;
    private final ChunkGenerator chunkGen;
    private final World world;
    private BukkitTask task;

    ChunkRegenerator(@NotNull World world) {
        Validate.notNull(world, "World cannot be null");

        this.plugin = uSkyBlock.getInstance();
        this.world = world;
        this.chunkGen = plugin.getDefaultWorldGenerator(world.getName(), "");
    }

    /**
     * Regenerates the given list of {@link Chunk}s at the configured chunks/tick speed (default: 4).
     * @param chunkList List of chunks to regenerate.
     * @param onCompletion Runnable to schedule on completion, or null to call no runnable.
     */
    public void regenerateChunks(@NotNull List<Chunk> chunkList, @Nullable Runnable onCompletion) {
        regenerateChunks(chunkList, failure -> {
            if (failure == null && onCompletion != null) {
                onCompletion.run();
            }
        });
    }

    /**
     * Regenerates chunks and reports an exception instead of silently abandoning the scheduled operation.
     */
    public void regenerateChunks(@NotNull List<Chunk> chunkList,
                                 @NotNull Consumer<Throwable> onCompletion) {
        Validate.notNull(chunkList, "ChunkList cannot be empty");
        Validate.notNull(onCompletion, "OnCompletion cannot be null");

        final int CHUNKS_PER_TICK = plugin.getConfig().getInt("options.advanced.chunkRegenSpeed", 4);
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        task = scheduler.runTaskTimer(plugin, () -> {
            try {
                for (int i = 0; i <= CHUNKS_PER_TICK; i++) {
                    if (!chunkList.isEmpty()) {
                        Chunk chunk = chunkList.remove(0);
                        regenerateChunk(chunk);
                    } else {
                        scheduler.runTaskLater(plugin, () -> onCompletion.accept(null), 1L);
                        task.cancel();
                        break;
                    }
                }
            } catch (Throwable failure) {
                task.cancel();
                scheduler.runTaskLater(plugin, () -> onCompletion.accept(failure), 1L);
            }
        }, 0L, 1L);
    }

    /**
     * Regenerates the given {@link Chunk}, removing all it's entities except players and setting the default biome.
     * @param chunk Chunk to regenerate.
     */
    public void regenerateChunk(@NotNull Chunk chunk) {
        Validate.notNull(chunk, "Chunk cannot be null");

        spawnTeleportPlayers(chunk);

        Random random = new Random();
        ChunkData chunkData = new CustomChunkData(world, chunk.getX(), chunk.getZ());
        chunkGen.generateNoise(world, random, 0, 0, chunkData);
        chunkGen.generateSurface(world, random, 0, 0, chunkData);
        chunkGen.generateBedrock(world, random, 0, 0, chunkData);
        chunkGen.generateCaves(world, random, 0, 0, chunkData);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
                    chunk.getBlock(x, y, z).setBlockData(chunkData.getBlockData(x, y, z));
                    chunk.getBlock(x, y, z).setBiome(chunkGen.getDefaultBiomeProvider(world).getBiome(world, x + chunk.getX() * 16, y, z + chunk.getZ() * 16));
                }
            }
        }
        removeEntities(chunk);
    }

    /**
     * Removes all the entities within the given {@link Chunk}, except for {@link Player}s.
     * @param chunk Chunk to remove entities in.
     */
    private void removeEntities(@NotNull Chunk chunk) {
        Arrays.stream(chunk.getEntities())
                .filter(entity -> !(entity instanceof Player))
                .forEach(Entity::remove);
    }

    /**
     * Teleport all the {@link Player}s within the given {@link Chunk} to spawn.
     * @param chunk Chunk to spawnteleport players in.
     */
    private void spawnTeleportPlayers(@NotNull Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player) {
                uSkyBlock.getInstance().getTeleportLogic().spawnTeleport((Player) entity, true);
            }
        }
    }
}
