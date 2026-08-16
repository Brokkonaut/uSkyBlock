package us.talabrek.ultimateskyblock.island;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An immutable, weighted limit shared by blocks and entities on an island.
 */
public record CombinedLimit(
        String key,
        String name,
        long limit,
        Map<Material, Integer> blocks,
        Map<EntityType, Integer> entities) {

    public CombinedLimit {
        blocks = Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
        entities = Collections.unmodifiableMap(new LinkedHashMap<>(entities));
    }
}
