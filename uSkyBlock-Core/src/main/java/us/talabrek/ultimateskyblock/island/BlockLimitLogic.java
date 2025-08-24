package us.talabrek.ultimateskyblock.island;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import us.talabrek.ultimateskyblock.api.model.BlockScore;
import us.talabrek.ultimateskyblock.island.level.IslandScore;
import us.talabrek.ultimateskyblock.uSkyBlock;
import dk.lockfuglsang.minecraft.util.ItemStackUtil;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class BlockLimitLogic {
    public enum CanPlace { YES, UNCERTAIN, NO}
    public record TryPlaceError (String material, int limit) {}
    public record TryPlaceResult (CanPlace canPlace, TryPlaceError error) {}

    private static final Logger log = Logger.getLogger(BlockLimitLogic.class.getName());
    private uSkyBlock plugin;
    private Map<Material, Integer> blockLimits = new HashMap<>();
    private Map<BlockData, Integer> blockStateLimits = new HashMap<>();
    private Map<Material, Set<BlockData>> blockStateLimitMaterials = new HashMap<>();
    // TODO: R4zorax - 13-07-2018: Persist this somehow - and use a guavacache
    private Map<Location, Map<Material, Integer>> blockCounts = new HashMap<>();
    private Map<Location, Map<BlockData, Integer>> blockStateCounts = new HashMap<>();
    private Map<Material, String> blockLimitCustomNames = new HashMap<>();
    private Map<BlockData, String> blockStateLimitCustomNames = new HashMap<>();

    private final boolean limitsEnabled;

    public BlockLimitLogic(uSkyBlock plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        limitsEnabled = config.getBoolean("options.island.block-limits.enabled", false);
        if (limitsEnabled) {
            ConfigurationSection section = config.getConfigurationSection("options.island.block-limits");
            Set<String> keys = section.getKeys(false);
            for (String key : keys) {
                if (key.equals("enabled")) {
                    continue;
                }
                String limitAndName = section.getString(key, "");
                int stateStart = key.indexOf('[');
                String stateString = null;
                if(stateStart >= 0) {
                    stateString = key.substring(stateStart);
                    key = key.substring(0, stateStart);
                }
                Material material = Material.getMaterial(key.toUpperCase());
                int nameSeperator = limitAndName.indexOf(";");
                String customName = null;
                if (nameSeperator >= 0) {
                    customName = limitAndName.substring(nameSeperator + 1);
                    limitAndName = limitAndName.substring(0, nameSeperator);
                }
                int limit = -1;
                try {
                    limit = Integer.parseInt(limitAndName);
                } catch (NumberFormatException ignored) {
                }
                if (limit < 0) {
                    log.warning("Value for block-limit for " + key + " is negative or not an integer");
                    continue;
                }
                if (material != null) {
                    if (stateString != null) {
                        try {
                            BlockData data = material.createBlockData(stateString);
                            blockStateLimits.put(data, limit);
                            blockStateLimitMaterials.computeIfAbsent(material, m -> new HashSet<>()).add(data);
                            if (customName != null) {
                                blockStateLimitCustomNames.put(data, customName);
                            }
                        } catch (IllegalArgumentException e) {
                            log.warning("Invalid block state for block-limit for material " + key + ": " + stateString);
                        }
                    } else {
                        blockLimits.put(material, limit);
                        if (customName != null) {
                            blockLimitCustomNames.put(material, customName);
                        }
                    }
                } else {
                    log.warning("Unknown material " + key + " supplied for block-limit");
                }
            }
        }
    }

    public int getLimit(Material type) {
        return blockLimits.getOrDefault(type, Integer.MAX_VALUE);
    }

    public Map<Material,Integer> getLimits() {
        return Collections.unmodifiableMap(blockLimits);
    }

    public Map<BlockData, Integer> getBlockStateLimits() {
        return Collections.unmodifiableMap(blockStateLimits);
    }
    
    public String getBlockLimitCustomName(Material type) {
        String name = blockLimitCustomNames.get(type);
        return name != null ? name : ItemStackUtil.getMaterialName(type);
    }

    public String getBlockStateLimitCustomName(BlockData type) {
        String name = blockStateLimitCustomNames.get(type);
        return name != null ? name : type.getAsString(true);
    }

    public void updateBlockCount(Location islandLocation, IslandScore score) {
        if (!limitsEnabled) {
            return;
        }
        Map<Material, Integer> countMap = asBlockCount(score);
        blockCounts.put(islandLocation, countMap);
        blockStateCounts.put(islandLocation, score.getLimitedStateCounts());
    }

    private Map<Material,Integer> asBlockCount(IslandScore score) {
        Map<Material, Integer> countMap = new ConcurrentHashMap<>();
        for (BlockScore blockScore : score.getTop()) {
            Material type = blockScore.getBlock();
            if (blockLimits.containsKey(type)) {
                int initalValue = countMap.getOrDefault(type, 0);
                initalValue += blockScore.getCount();
                countMap.put(type, initalValue);
            }
        }
        return countMap;
    }

    public int getCount(Material type, Location islandLocation) {
        if (!limitsEnabled || !blockLimits.containsKey(type)) {
            return -1;
        }
        Map<Material, Integer> islandCount = blockCounts.getOrDefault(islandLocation, null);
        if (islandCount == null) {
            return -2;
        }
        return islandCount.getOrDefault(type, 0);
    }

    public int getBlockStateCount(BlockData type, Location islandLocation) {
        if (!limitsEnabled || !blockStateLimits.containsKey(type)) {
            return -1;
        }
        Map<BlockData, Integer> islandCount = blockStateCounts.getOrDefault(islandLocation, null);
        if (islandCount == null) {
            return -2;
        }
        return islandCount.getOrDefault(type, 0);
    }

    public TryPlaceResult canPlace(BlockData state, IslandInfo islandInfo) {
        Material type = state.getMaterial();
        int count = getCount(type, islandInfo.getIslandLocation());
        if (count == -2) {
            return new TryPlaceResult(CanPlace.UNCERTAIN, null);
        }
        boolean ok = true;
        if (count != -1) {
            int materiallimit = blockLimits.getOrDefault(type, Integer.MAX_VALUE);
            ok = count < materiallimit;
            if (!ok) {
                return new TryPlaceResult(CanPlace.NO, new TryPlaceError(getBlockLimitCustomName(type), materiallimit));
            }
        }
        Set<BlockData> limitedBlockStates = getLimitedBlockStatesForMaterial(type);
        if (limitedBlockStates != null && !limitedBlockStates.isEmpty()) {
            for (BlockData limitedBlockState : limitedBlockStates) {
                if (state.matches(limitedBlockState)) {
                    int limit = blockStateLimits.get(limitedBlockState);
                    int existing = getBlockStateCount(limitedBlockState, islandInfo.getIslandLocation());
                    if (existing == -2) {
                        return new TryPlaceResult(CanPlace.UNCERTAIN, null);
                    }
                    if (existing >= 0 && existing >= limit) {
                        return new TryPlaceResult(CanPlace.NO, new TryPlaceError(getBlockStateLimitCustomName(limitedBlockState), limit));
                    }
                }
            }
        }
        return new TryPlaceResult(CanPlace.YES, null);
    }

    public void incBlockCount(Location islandLocation, BlockData state) {
        if (!limitsEnabled) {
            return;
        }
        Material type = state.getMaterial();
        if (blockLimits.containsKey(type)) {
            Map<Material, Integer> islandCount = blockCounts.computeIfAbsent(islandLocation, l -> new ConcurrentHashMap<>());
            islandCount.merge(type, 1, Integer::sum);
        }
        Set<BlockData> limitedBlockStates = getLimitedBlockStatesForMaterial(type);
        if (limitedBlockStates != null && !limitedBlockStates.isEmpty()) {
            for (BlockData limitedBlockState : limitedBlockStates) {
                if (state.matches(limitedBlockState)) {
                    Map<BlockData, Integer> islandCount = blockStateCounts.computeIfAbsent(islandLocation, l -> new ConcurrentHashMap<>());
                    islandCount.merge(limitedBlockState, 1, Integer::sum);
                }
            }
        }
    }

    public void decBlockCount(Location islandLocation, BlockData state) {
        if (!limitsEnabled) {
            return;
        }
        Material type = state.getMaterial();
        if (blockLimits.containsKey(type)) {
            Map<Material, Integer> islandCount = blockCounts.computeIfAbsent(islandLocation, l -> new ConcurrentHashMap<>());
            islandCount.merge(type, -1, Integer::sum);
        }
        Set<BlockData> limitedBlockStates = getLimitedBlockStatesForMaterial(type);
        if (limitedBlockStates != null && !limitedBlockStates.isEmpty()) {
            for (BlockData limitedBlockState : limitedBlockStates) {
                if (state.matches(limitedBlockState)) {
                    Map<BlockData, Integer> islandCount = blockStateCounts.computeIfAbsent(islandLocation, l -> new ConcurrentHashMap<>());
                    islandCount.merge(limitedBlockState, -1, Integer::sum);
                }
            }
        }
    }

    public Set<BlockData> getLimitedBlockStatesForMaterial(Material type) {
        return blockStateLimitMaterials.get(type);
    }

    public boolean hasLimitedBlockStatesForMaterial(Material type) {
        Set<BlockData> limits = getLimitedBlockStatesForMaterial(type);
        return limits != null && !limits.isEmpty();
    }
}
