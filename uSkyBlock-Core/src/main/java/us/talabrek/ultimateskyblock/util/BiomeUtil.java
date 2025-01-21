package us.talabrek.ultimateskyblock.util;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper to support the idiotic compatibility issues reg. biomes
 */
public enum BiomeUtil {;
    private static final Map<String,String> biomeAlias = new HashMap<>();
    static {
        biomeAlias.put("ICE_PLAINS", "ICE_FLATS"); // Bukkit 1.9 -> 1.10
        biomeAlias.put("FLOWER_FOREST", "MUTATED_FOREST"); // Bukkit 1.8 -> 1.9
    }

    public static Biome getBiome(String name) {
        Biome b = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(NamespacedKey.fromString(name.toLowerCase()));
        if(b == null) {
            if (biomeAlias.containsKey(name)) {
                return getBiome(biomeAlias.get(name));
            }
        }
        return b;
    }
}
