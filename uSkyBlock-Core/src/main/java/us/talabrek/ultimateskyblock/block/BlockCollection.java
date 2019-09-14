package us.talabrek.ultimateskyblock.block;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import us.talabrek.ultimateskyblock.handler.VaultHandler;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

import static dk.lockfuglsang.minecraft.po.I18nUtil.tr;

public class BlockCollection {
    Map<Material, Integer> blockCount;

    public BlockCollection() {
        this.blockCount = new EnumMap<>(Material.class);
    }

    public synchronized void add(Block block) {
        int currentValue = blockCount.getOrDefault(block.getType(), 0);
        blockCount.put(block.getType(), currentValue + 1);
    }

    /**
     * Returns <code>null</code> if all the items are in the BlockCollection, a String describing the missing items if it's not
     * @param itemStacks
     * @return
     */
    public synchronized String diff(Collection<ItemStack> itemStacks) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack item : itemStacks) {
            int diff = item.getAmount() - count(item.getType());
            if (diff > 0) {
                sb.append(tr(" \u00a7f{0}x \u00a77{1}", diff, VaultHandler.getItemName(item)));
            }
        }
        if (sb.toString().trim().isEmpty()) {
            return null;
        }
        return tr("\u00a7eStill the following blocks short: {0}", sb.toString());
    }

    private int count(Material type) {
        return blockCount.getOrDefault(type, 0);
    }
}
