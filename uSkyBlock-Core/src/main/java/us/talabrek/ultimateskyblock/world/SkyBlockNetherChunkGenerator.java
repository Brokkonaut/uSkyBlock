package us.talabrek.ultimateskyblock.world;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import us.talabrek.ultimateskyblock.Settings;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class SkyBlockNetherChunkGenerator extends ChunkGenerator {
    private static final List<Biome> NETHER_WASTES = Collections.singletonList(Biome.NETHER_WASTES);
    private static final BiomeProvider NETHER_WASTES_PROVIDER = new BiomeProvider() {
        @Override
        public @NotNull List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
            return NETHER_WASTES;
        }

        @Override
        public @NotNull Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
            return Biome.NETHER_WASTES;
        }
    };

    @Override
    public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random, int cx, int cz, @NotNull ChunkData chunkData) {
        int y = 0;
        // Solid floor
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunkData.setBlock(x, y, z, Material.BEDROCK);
            }
        }
        // Bedrock with holes in it
        for (y = 1; y <= 5; y++) {
            double yThreshold = 0.10 * y;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (random.nextDouble() >= yThreshold) { // 10%-50% air
                        chunkData.setBlock(x, y, z, Material.BEDROCK);
                    } else {
                        chunkData.setBlock(x, y, z, Material.LAVA);
                    }
                }
            }
        }
        for (y = 6; y <= Settings.nether_lava_level; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    chunkData.setBlock(x, y, z, Material.LAVA);
                }
            }
        }
        y = 120;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (random.nextDouble() >= 0.20) { // 20% air
                    chunkData.setBlock(x, y, z, Material.NETHERRACK);
                }
            }
        }
        y = 121;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunkData.setBlock(x, y, z, Material.NETHERRACK);
            }
        }
        for (y = 122; y <= 126; y++) {
            double yThreashold = 0.20 * (127 - y);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (random.nextDouble() >= yThreashold) { // 20%-100% bedrock
                        chunkData.setBlock(x, y, z, Material.BEDROCK);
                    } else {
                        chunkData.setBlock(x, y, z, Material.NETHERRACK);
                    }
                }
            }
        }
        y = 127;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunkData.setBlock(x, y, z, Material.BEDROCK);
            }
        }
    }

    @Override
    public @Nullable BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return NETHER_WASTES_PROVIDER;
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return Collections.emptyList();
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5d, Settings.island_height, 0.5d);
    }
}
