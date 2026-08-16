package us.talabrek.ultimateskyblock.island.level;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import us.talabrek.ultimateskyblock.api.model.BlockScore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Mutable collection for storing counts of blocks
 */
public class BlockCountCollection {
    private BlockLevelConfigMap configMap;
    private Map<Material, LongAdder> countMap;
    private Map<BlockData, LongAdder> stateCountMap;
    private final Set<Material> limitedMaterials;
    private final Map<Material, LongAdder> limitedMaterialCountMap;

    public BlockCountCollection(BlockLevelConfigMap configMap) {
        this(configMap, java.util.Collections.emptySet());
    }

    public BlockCountCollection(BlockLevelConfigMap configMap, Set<Material> limitedMaterials) {
        this.configMap = configMap;
        this.limitedMaterials = limitedMaterials;
        countMap = new ConcurrentHashMap<>();
        stateCountMap = new ConcurrentHashMap<>();
        limitedMaterialCountMap = new ConcurrentHashMap<>();
    }

    public int add(Material type) {
        addLimited(type);
        BlockLevelConfig blockLevelConfig = configMap.get(type);
        Material key = blockLevelConfig.getKey();
        LongAdder count = countMap.computeIfAbsent(key, k -> new LongAdder());
        count.add(1);
        return count.intValue();
    }

    public void addLimited(Material type) {
        if (limitedMaterials.contains(type)) {
            limitedMaterialCountMap.computeIfAbsent(type, ignored -> new LongAdder()).increment();
        }
    }

    public void addState(BlockData blockState) {
        LongAdder count = stateCountMap.computeIfAbsent(blockState, k -> new LongAdder());
        count.add(1);
    }

    public List<BlockScore> calculateScore(double pointsPerLevel) {
        return countMap.entrySet().stream()
                .map(e -> configMap.get(e.getKey()).calculateScore(e.getValue().intValue(), pointsPerLevel))
                .filter(f -> f.getScore() != 0)
                .sorted(new BlockScoreComparator()).collect(Collectors.toList());
    }

    public Map<BlockData, Integer> getLimitedStateCounts() {
        HashMap<BlockData, Integer> result = new HashMap<>();
        for (Entry<BlockData, LongAdder> e : stateCountMap.entrySet()) {
            result.put(e.getKey(), e.getValue().intValue());
        }
        return result;
    }

    public Map<Material, Integer> getLimitedMaterialCounts() {
        HashMap<Material, Integer> result = new HashMap<>();
        for (Entry<Material, LongAdder> entry : limitedMaterialCountMap.entrySet()) {
            result.put(entry.getKey(), (int) Math.min(Integer.MAX_VALUE, entry.getValue().longValue()));
        }
        return result;
    }
}
