package us.talabrek.ultimateskyblock.island.level;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class BlockLevelConfigBuilder {
    private Material baseBlock;
    private Set<Material> additionalBlocks = new HashSet<>();
    private double scorePerBlock = 10;
    private int limit = -1;
    private int diminishingReturns = -1;
    private int negativeReturns = -1;

    public BlockLevelConfigBuilder() {
    }

    public BlockLevelConfigBuilder base(Material baseBlock) {
        this.baseBlock = baseBlock;
        return this;
    }

    public BlockLevelConfigBuilder additionalBlocks(Material... blocks) {
        this.additionalBlocks.addAll(Arrays.asList(blocks));
        return this;
    }

    public BlockLevelConfigBuilder scorePerBlock(double scorePerBlock) {
        this.scorePerBlock = scorePerBlock;
        return this;
    }

    public BlockLevelConfigBuilder limit(int limit) {
        this.limit = limit;
        return this;
    }

    public BlockLevelConfigBuilder diminishingReturns(int diminishingReturns) {
        this.diminishingReturns = diminishingReturns;
        return this;
    }

    public BlockLevelConfigBuilder negativeReturns(int negativeReturns) {
        this.negativeReturns = negativeReturns;
        return this;
    }

    public BlockLevelConfigBuilder copy() {
        return new BlockLevelConfigBuilder()
                .base(baseBlock)
                .additionalBlocks(additionalBlocks.toArray(new Material[0]))
                .scorePerBlock(scorePerBlock)
                .limit(limit)
                .diminishingReturns(diminishingReturns)
                .negativeReturns(negativeReturns);
    }

    public BlockLevelConfig build() {
        if (baseBlock == null) {
            throw new IllegalArgumentException("No base has been set for BlockLevelConfigBuilder");
        }
        additionalBlocks = additionalBlocks.stream().filter(f -> f != baseBlock).collect(Collectors.toSet());
        return new BlockLevelConfig(baseBlock, additionalBlocks, scorePerBlock, limit, diminishingReturns, negativeReturns);
    }
}
