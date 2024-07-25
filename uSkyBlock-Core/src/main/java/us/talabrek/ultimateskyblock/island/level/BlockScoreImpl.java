package us.talabrek.ultimateskyblock.island.level;

import dk.lockfuglsang.minecraft.util.FormatUtil;
import dk.lockfuglsang.minecraft.util.ItemStackUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;


public class BlockScoreImpl implements us.talabrek.ultimateskyblock.api.model.BlockScore {

    private final Material block;
    private final int count;
    private final double score;
    private final State state;
    private final String name;

    public BlockScoreImpl(Material block, int count, double score, State state) {
        this(block, count, score, state, null);
    }

    public BlockScoreImpl(Material block, int count, double score, State state, String name) {
        this.block = block;
        this.count = count;
        this.score = score;
        this.state = state;
        this.name = name != null ? name : FormatUtil.camelcase(block.name());
    }

    @Override
    public String toString() {
        return "BlockScore{" +
                "name=" + name +
                ", block=" + block +
                ", count=" + count +
                ", score=" + score +
                ", state=" + state +
                '}';
    }


    @Override
    public Material getBlock() {
        return block;
    }


    @Override
    public int getCount() {
        return count;
    }


    @Override
    public double getScore() {
        return score;
    }


    @Override
    public State getState() {
        return state;
    }


    @Override
    public String getName() {
        return name;
    }
}
