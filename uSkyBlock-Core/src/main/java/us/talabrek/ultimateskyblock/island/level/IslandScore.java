package us.talabrek.ultimateskyblock.island.level;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import us.talabrek.ultimateskyblock.api.model.BlockScore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The summary of island calculation.
 */
public class IslandScore implements us.talabrek.ultimateskyblock.api.model.IslandScore {
    private final double score;
    private final List<BlockScore> top;
    private boolean isSorted = false;
    private final Map<BlockData, Integer> limitedStateCounts;
    private final Map<Material, Integer> limitedMaterialCounts;
    private final Map<EntityType, Integer> limitedEntityCounts;

    public IslandScore(double score, List<BlockScore> top, Map<BlockData, Integer> limitedStateCounts) {
        this(score, top, limitedStateCounts, Collections.emptyMap(), Collections.emptyMap());
    }

    public IslandScore(double score, List<BlockScore> top, Map<BlockData, Integer> limitedStateCounts,
                       Map<Material, Integer> limitedMaterialCounts,
                       Map<EntityType, Integer> limitedEntityCounts) {
        this.score = score;
        this.top = joinTop(top);
        this.limitedStateCounts = new HashMap<>(limitedStateCounts);
        this.limitedMaterialCounts = new HashMap<>(limitedMaterialCounts);
        this.limitedEntityCounts = new HashMap<>(limitedEntityCounts);
    }

    /**
     * Consolidates the top, so scores with the same name is combined.
     */
    private List<BlockScore> joinTop(List<BlockScore> top) {
        Map<String, BlockScore> scoreMap = new HashMap<>();
        for (BlockScore score : top) {
            BlockScore existing = scoreMap.get(score.getName());
            if (existing == null) {
                scoreMap.put(score.getName(), score);
            } else {
                scoreMap.put(score.getName(), add(score, existing));
            }
        }
        return new ArrayList<>(scoreMap.values());
    }

    private BlockScoreImpl add(BlockScore score, BlockScore existing) {
        BlockScore.State state = score.getState();
        if (score.getState().ordinal() > existing.getState().ordinal()) {
            state = existing.getState();
        }
        return new BlockScoreImpl(existing.getBlock(),
                score.getCount() + existing.getCount(),
                score.getScore() + existing.getScore(), state, score.getName());
    }


    @Override
    public double getScore() {
        return score;
    }

    public List<BlockScore> getTop() {
        return top;
    }

    @Override
    public List<BlockScore> getTop(int num) {
        return getTop(0, num);
    }

    @Override
    public List<BlockScore> getTop(int offset, int num) {
        if (num <= 0) {
            throw new IllegalArgumentException("Number must be a positive integer.");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be a non-negative integer.");
        }
        if (!isSorted) {
            Collections.sort(top, new BlockScoreComparator());
            isSorted = true;
        }
        return top.subList(offset, Math.min(offset+num, top.size()));
    }

    @Override
    public int getSize() {
        return top.size();
    }

    public Map<BlockData, Integer> getLimitedStateCounts() {
        return limitedStateCounts;
    }

    public Map<Material, Integer> getLimitedMaterialCounts() {
        return Collections.unmodifiableMap(limitedMaterialCounts);
    }

    public Map<EntityType, Integer> getLimitedEntityCounts() {
        return Collections.unmodifiableMap(limitedEntityCounts);
    }
}
