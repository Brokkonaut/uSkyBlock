package us.talabrek.ultimateskyblock.world;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.material.MaterialData;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("deprecation")
public class CustomChunkData implements ChunkData {
    private World world;
    private int x;
    private int z;
    private Object[][] blocks;
    private int maxY;
    private int minY;

    public CustomChunkData(World world, int x, int z) {
        this.world = world;
        this.x = x;
        this.z = z;
        this.maxY = world.getMaxHeight();
        this.minY = world.getMinHeight();
    }

    @Override
    public int getMinHeight() {
        return minY;
    }

    @Override
    public int getMaxHeight() {
        return maxY;
    }

    @Override
    public @NotNull Biome getBiome(int x, int y, int z) {
        return world.getGenerator().getDefaultBiomeProvider(world).getBiome(world, x + this.x, y, z + this.z);
    }

    private void setBlockAt(int x, int y, int z, Object o) {
        int relY = y - minY;
        int sectionY = relY >> 4;
        int localY = relY % 16;
        if (this.blocks == null) {
            this.blocks = new Object[(maxY - minY + 15) >> 4][];
        }
        Object[] section = this.blocks[sectionY];
        if (section == null) {
            section = new Object[16 * 16 * 16];
            this.blocks[sectionY] = section;
        }
        section[x + z * 16 + localY * 16 * 16] = o;
    }

    @Override
    public void setBlock(int x, int y, int z, @NotNull Material material) {
        setBlockAt(x, y, z, material);
    }

    @Override
    public void setBlock(int x, int y, int z, @NotNull MaterialData material) {
        throw new RuntimeException();
    }

    @Override
    public void setBlock(int x, int y, int z, @NotNull BlockData blockData) {
        setBlockAt(x, y, z, blockData.clone());
    }

    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, @NotNull Material material) {
        for (int x = xMin; x < xMax; x++) {
            for (int z = zMin; z < zMax; z++) {
                for (int y = yMin; y < yMax; y++) {
                    setBlockAt(x, y, z, material);
                }
            }
        }
    }

    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, @NotNull MaterialData material) {
        throw new RuntimeException();
    }

    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, @NotNull BlockData blockData) {
        blockData = blockData.clone();
        for (int x = xMin; x < xMax; x++) {
            for (int z = zMin; z < zMax; z++) {
                for (int y = yMin; y < yMax; y++) {
                    setBlockAt(x, y, z, blockData);
                }
            }
        }
    }

    private Object getBlockAt(int x, int y, int z) {
        int relY = y - minY;
        int sectionY = relY >> 4;
        int localY = relY % 16;
        if (this.blocks == null) {
            return null;
        }
        Object[] section = this.blocks[sectionY];
        if (section == null) {
            return null;
        }
        return section[x + z * 16 + localY * 16 * 16];
    }

    @Override
    public @NotNull Material getType(int x, int y, int z) {
        Object current = getBlockAt(x, y, z);
        if (current == null) {
            return Material.AIR;
        } else if (current instanceof Material) {
            return (Material) current;
        }
        return ((BlockData) current).getMaterial();
    }

    @Override
    public @NotNull MaterialData getTypeAndData(int x, int y, int z) {
        throw new RuntimeException();
    }

    @Override
    public @NotNull BlockData getBlockData(int x, int y, int z) {
        Object current = getBlockAt(x, y, z);
        if (current == null) {
            return Material.AIR.createBlockData();
        } else if (current instanceof Material) {
            return ((Material) current).createBlockData();
        }
        return ((BlockData) current).clone();
    }

    @Override
    public byte getData(int x, int y, int z) {
        throw new RuntimeException();
    }

}
