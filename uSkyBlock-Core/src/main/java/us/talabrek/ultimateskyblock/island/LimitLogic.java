package us.talabrek.ultimateskyblock.island;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Golem;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WaterMob;
import us.talabrek.ultimateskyblock.handler.WorldGuardHandler;
import us.talabrek.ultimateskyblock.util.EntityUtil;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import static dk.lockfuglsang.minecraft.po.I18nUtil.marktr;
import static dk.lockfuglsang.minecraft.po.I18nUtil.tr;

public class LimitLogic {
    public enum CreatureType { UNKNOWN, ANIMAL, MONSTER, VILLAGER, GOLEM, COPPERGOLEM, WATERANIMAL }
    static {
        marktr("UNKNOWN");
        marktr("ANIMAL");
        marktr("MONSTER");
        marktr("VILLAGER");
        marktr("GOLEM");
        marktr("WATERANIMAL");
        marktr("COPPERGOLEM");
    }

    private final uSkyBlock plugin;

    public LimitLogic(uSkyBlock plugin) {
        this.plugin = plugin;
    }

    public Map<CreatureType, Integer> getCreatureCount(us.talabrek.ultimateskyblock.api.IslandInfo islandInfo) {
        Map<CreatureType, Integer> mapCount = new HashMap<>();
        for (CreatureType type : CreatureType.values()) {
            mapCount.put(type, 0);
        }
        Location islandLocation = islandInfo.getIslandLocation();
        ProtectedRegion islandRegionAt = WorldGuardHandler.getIslandRegionAt(islandLocation);
        if (islandRegionAt != null) {
            // Nether and Overworld regions are more or less equal (same x,z coords)
            List<LivingEntity> creatures = WorldGuardHandler.getCreaturesInRegion(plugin.getWorldManager().getWorld(),
                    islandRegionAt);
            World nether = plugin.getWorldManager().getNetherWorld();
            if (nether != null) {
                creatures.addAll(WorldGuardHandler.getCreaturesInRegion(nether, islandRegionAt));
            }
            for (LivingEntity creature : creatures) {
                if (!creature.hasAI()) {
                    continue;
                }
                CreatureType key = getCreatureType(creature);
                if (!mapCount.containsKey(key)) {
                    mapCount.put(key, 0);
                }
                mapCount.put(key, mapCount.get(key) + 1);
            }
        }
        return mapCount;
    }


    public Map<CreatureType, Map<EntityType, Integer>> getDetailedCreatureCount(us.talabrek.ultimateskyblock.api.IslandInfo islandInfo) {
        Map<CreatureType, Map<EntityType, Integer>> mapCount = new HashMap<>();
        for (CreatureType type : CreatureType.values()) {
            TreeMap<EntityType, Integer> entries = new TreeMap<>((e1, e2) -> e1 == null ? (e2 == null ? 0 : -1) : (e2 == null ? 1 : e1.name().compareTo(e2.name())));
            entries.put(null, 0);
            mapCount.put(type, entries);
        }
        Location islandLocation = islandInfo.getIslandLocation();
        ProtectedRegion islandRegionAt = WorldGuardHandler.getIslandRegionAt(islandLocation);
        if (islandRegionAt != null) {
            // Nether and Overworld regions are more or less equal (same x,z coords)
            List<LivingEntity> creatures = WorldGuardHandler.getCreaturesInRegion(plugin.getWorldManager().getWorld(),
                    islandRegionAt);
            World nether = plugin.getWorldManager().getNetherWorld();
            if (nether != null) {
                creatures.addAll(WorldGuardHandler.getCreaturesInRegion(nether, islandRegionAt));
            }
            for (LivingEntity creature : creatures) {
                if (!creature.hasAI()) {
                    continue;
                }
                CreatureType key = getCreatureType(creature);
                Map<EntityType, Integer> typeCount = mapCount.get(key);
                typeCount.put(null, typeCount.get(null) + 1);
                typeCount.put(creature.getType(), typeCount.getOrDefault(creature.getType(), 0) + 1);
            }
        }
        return mapCount;
    }
    
    public Map<CreatureType, Integer> getCreatureMax(us.talabrek.ultimateskyblock.api.IslandInfo islandInfo) {
        Map<CreatureType, Integer> max = new LinkedHashMap<>();
        for (CreatureType creatureType : CreatureType.values()) {
            max.put(creatureType, getMax(islandInfo, creatureType));
        }
        return max;
    }

    public CreatureType getCreatureType(LivingEntity creature) {
        if (creature instanceof WaterMob
                || creature instanceof Guardian
                || creature instanceof Axolotl) {
            return CreatureType.WATERANIMAL;
        } else if (creature instanceof Monster
                || creature instanceof Slime
                || creature instanceof Ghast) {
            return CreatureType.MONSTER;
        } else if (creature instanceof Animals) {
            return CreatureType.ANIMAL;
        } else if (creature instanceof Villager) {
            return CreatureType.VILLAGER;
        } else if (creature instanceof CopperGolem) {
            return CreatureType.COPPERGOLEM;
        } else if (creature instanceof Golem) {
            return CreatureType.GOLEM;
        }
        return CreatureType.UNKNOWN;
    }

    public CreatureType getCreatureType(EntityType entityType) {
        if (WaterMob.class.isAssignableFrom(entityType.getEntityClass())
                || Axolotl.class.isAssignableFrom(entityType.getEntityClass())
                || Guardian.class.isAssignableFrom(entityType.getEntityClass())
                ) {
            return CreatureType.WATERANIMAL;
        } else if (Monster.class.isAssignableFrom(entityType.getEntityClass())
                || Slime.class.isAssignableFrom(entityType.getEntityClass())
                || Ghast.class.isAssignableFrom(entityType.getEntityClass())
                ) {
            return CreatureType.MONSTER;
        } else if (Animals.class.isAssignableFrom(entityType.getEntityClass())) {
            return CreatureType.ANIMAL;
        } else if (Villager.class.isAssignableFrom(entityType.getEntityClass())) {
            return CreatureType.VILLAGER;
        } else if (CopperGolem.class.isAssignableFrom(entityType.getEntityClass())) {
            return CreatureType.COPPERGOLEM;
        } else if (Golem.class.isAssignableFrom(entityType.getEntityClass())) {
            return CreatureType.GOLEM;
        }
        return CreatureType.UNKNOWN;
    }

    public boolean canSpawn(EntityType entityType, us.talabrek.ultimateskyblock.api.IslandInfo islandInfo) {
        Map<CreatureType, Integer> creatureCount = getCreatureCount(islandInfo);
        CreatureType creatureType = getCreatureType(entityType);
        int max = getMax(islandInfo, creatureType);
        if (creatureCount.containsKey(creatureType) && creatureCount.get(creatureType) >= max) {
            return false;
        }
        return true;
    }

    private int getMax(us.talabrek.ultimateskyblock.api.IslandInfo islandInfo, CreatureType creatureType) {
        switch (creatureType) {
            case ANIMAL: return islandInfo.getMaxAnimals();
            case MONSTER: return islandInfo.getMaxMonsters();
            case VILLAGER: return islandInfo.getMaxVillagers();
            case GOLEM: return islandInfo.getMaxGolems();
            case COPPERGOLEM: return islandInfo.getMaxCopperGolems();
            case WATERANIMAL: return islandInfo.getMaxWaterAnimals();
        }
        return Integer.MAX_VALUE;
    }

    public Component getSummary(us.talabrek.ultimateskyblock.api.IslandInfo islandInfo) {
        Component result = Component.empty();
        
        Map<LimitLogic.CreatureType, Integer> creatureMax = getCreatureMax(islandInfo);
        Map<CreatureType, Map<EntityType, Integer>> count = getDetailedCreatureCount(islandInfo);
        for (LimitLogic.CreatureType key : creatureMax.keySet()) {
            if (key == CreatureType.UNKNOWN) {
                continue; // Skip
            }
            Map<EntityType, Integer> countForCreatueType = count.get(key);
            int cnt = countForCreatueType.get(null);
            int max = creatureMax.get(key);
            Component line = LegacyComponentSerializer.legacySection().deserialize(tr("\u00a77{0}: \u00a7a{1}\u00a77 (max. {2})", tr(key.name()), cnt >= max ? tr("\u00a7c{0}", cnt) : cnt, max));
            Component hoverText = Component.empty();
            for (Entry<EntityType, Integer> e : countForCreatueType.entrySet()) {
                if (e.getKey() != null) {
                    if (hoverText != Component.empty()) {
                        hoverText = hoverText.append(Component.newline());
                    }
                    hoverText = hoverText.append(Component.text(EntityUtil.getEntityDisplayName(e.getKey()) + ": " + e.getValue()));
                }
            }
            line = line.hoverEvent(HoverEvent.showText(hoverText));
            if (result != Component.empty()) {
                result = result.append(Component.newline());
            }
            result = result.append(line);
        }
        Map<Material, Integer> blockLimits = plugin.getBlockLimitLogic().getLimits();
        for (Map.Entry<Material, Integer> entry : blockLimits.entrySet()) {
            int blockCount = plugin.getBlockLimitLogic().getCount(entry.getKey(), islandInfo.getIslandLocation());
            Component line;
            if (blockCount >= 0) {
                line = LegacyComponentSerializer.legacySection().deserialize(tr("\u00a77{0}: \u00a7a{1}\u00a77 (max. {2})",
                        plugin.getBlockLimitLogic().getBlockLimitCustomName(entry.getKey()),
                        blockCount >= entry.getValue() ? tr("\u00a7c{0}", blockCount) : blockCount,
                        entry.getValue()));
            } else {
                line = LegacyComponentSerializer.legacySection().deserialize(tr("\u00a77{0}: \u00a7a{1}\u00a77 (max. {2})",
                        plugin.getBlockLimitLogic().getBlockLimitCustomName(entry.getKey()),
                        tr("\u00a7c{0}", "?"),
                        entry.getValue()));
            }
            if (result != Component.empty()) {
                result = result.append(Component.newline());
            }
            result = result.append(line);
        }
        Map<BlockData, Integer> blockStateLimits = plugin.getBlockLimitLogic().getBlockStateLimits();
        for (Map.Entry<BlockData, Integer> entry : blockStateLimits.entrySet()) {
            int blockCount = plugin.getBlockLimitLogic().getBlockStateCount(entry.getKey(), islandInfo.getIslandLocation());
            Component line;
            if (blockCount >= 0) {
                line = LegacyComponentSerializer.legacySection().deserialize(tr("\u00a77{0}: \u00a7a{1}\u00a77 (max. {2})",
                        plugin.getBlockLimitLogic().getBlockStateLimitCustomName(entry.getKey()),
                        blockCount >= entry.getValue() ? tr("\u00a7c{0}", blockCount) : blockCount,
                        entry.getValue()));
            } else {
                line = LegacyComponentSerializer.legacySection().deserialize(tr("\u00a77{0}: \u00a7a{1}\u00a77 (max. {2})",
                        plugin.getBlockLimitLogic().getBlockStateLimitCustomName(entry.getKey()),
                        tr("\u00a7c{0}", "?"),
                        entry.getValue()));
            }
            if (result != Component.empty()) {
                result = result.append(Component.newline());
            }
            result = result.append(line);
        }
        return result;
    }
}
