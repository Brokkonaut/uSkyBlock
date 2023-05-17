package us.talabrek.ultimateskyblock.block;

import dk.lockfuglsang.minecraft.util.ItemStackUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static dk.lockfuglsang.minecraft.po.I18nUtil.tr;

public class BlockCollection {
    private static final Map<Material, Material> defaultMaterialReplacements = new HashMap<>();
    static {
        for (Material m : Material.values()) {
            if (m.name().contains("_WALL_")) {
                try {
                    Material replacement = Material.valueOf(m.name().replace("_WALL_", "_"));
                    defaultMaterialReplacements.put(m, replacement);
                } catch (IllegalArgumentException ignored) {
                    // ignored
                }
            }
        }
    }

    Map<Material, Integer> blockCount;

    public BlockCollection() {
        this.blockCount = new EnumMap<>(Material.class);
    }

    public synchronized void add(Block block) {
        Material blockType = block.getType();
        blockType = defaultMaterialReplacements.getOrDefault(blockType, blockType);
        int currentValue = blockCount.getOrDefault(blockType, 0);
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
            Material blockType = item.getType();
            blockType = defaultMaterialReplacements.getOrDefault(blockType, blockType);
            int diff = item.getAmount() - count(blockType);
            if (diff > 0) {
                sb.append(tr(" \u00a7f{0}x \u00a77{1}", diff, ItemStackUtil.getItemName(item)));
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
