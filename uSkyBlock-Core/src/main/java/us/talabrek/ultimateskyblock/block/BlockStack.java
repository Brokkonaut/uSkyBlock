package us.talabrek.ultimateskyblock.block;

import com.google.common.base.Preconditions;
import org.bukkit.Material;

public class BlockStack {
    private Material block;
    private int amount;

    public BlockStack(Material block, int amount) {
        Preconditions.checkArgument(block != null && block.isBlock(), "block must be a block");
        this.block = block;
        this.amount = amount;
    }

    public Material getBlock() {
        return block;
    }

    public int getAmount() {
        return amount;
    }
}
