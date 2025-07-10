package us.talabrek.ultimateskyblock.util;

import dk.lockfuglsang.minecraft.util.ItemStackAndAmount;
import java.util.Collection;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class MultiInventoryWrapper {
    private final Inventory[] inventories;

    public MultiInventoryWrapper(Inventory inventory) {
        this.inventories = new Inventory[] { inventory };
    }

    public MultiInventoryWrapper(Inventory firstInventory, Inventory secondInventory) {
        this.inventories = new Inventory[] { firstInventory, secondInventory };
    }

    public MultiInventoryWrapper(Inventory... inventories) {
        this.inventories = inventories.clone();
    }

    public int getAmount(ItemStack stack) {
        Material type = stack.getType();
        int found = 0;
        for (Inventory inventory : inventories) {
            for (ItemStack invStack : inventory.getStorageContents()) {
                if (invStack != null && invStack.getType() == type && invStack.isSimilar(stack)) {
                    found += stack.getAmount();
                }
            }
        }
        return found;
    }

    public boolean contains(ItemStackAndAmount stack) {
        Material type = stack.stack().getType();
        int found = 0;
        for (Inventory inventory : inventories) {
            for (ItemStack invStack : inventory.getStorageContents()) {
                if (invStack != null && invStack.getType() == type && invStack.isSimilar(stack.stack())) {
                    found += invStack.getAmount();
                    if (found >= stack.amount()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean removeIfAllAvailable(Collection<ItemStackAndAmount> items) {
        ItemStack[][] allContents = new ItemStack[inventories.length][];
        boolean[] modified = new boolean[inventories.length];

        // first we see if we find all items, we clone the inventory contents to avoid modification for now
        for (ItemStackAndAmount item : items) {
            if (item != null && item.stack() != null && !item.stack().getType().isAir()) {
                int remaining = item.amount();

                inventoryLoop: for (int inventoryNr = 0; remaining > 0 && inventoryNr < inventories.length; inventoryNr++) {
                    ItemStack[] contents = allContents[inventoryNr];
                    // if the inventory was not loaded, load it now
                    if (contents == null) {
                        contents = deepCopy(inventories[inventoryNr].getStorageContents());
                        allContents[inventoryNr] = contents;
                    }
                    int similarStackPos = -1;
                    while (remaining > 0) {
                        similarStackPos = getFirstSimilar(item.stack(), contents, similarStackPos + 1);
                        if (similarStackPos < 0) {
                            continue inventoryLoop; // no more found here, try the next inventory
                        }
                        modified[inventoryNr] = true;
                        ItemStack content = contents[similarStackPos];
                        int here = content.getAmount();
                        if (here > remaining) {
                            content.setAmount(here - remaining);
                            remaining = 0;
                        } else {
                            contents[similarStackPos] = null;
                            remaining -= here;
                        }
                    }
                }

                if (remaining > 0) {
                    return false;
                }
            }
        }

        // all items were found, update inventories
        for (int inventoryNr = 0; inventoryNr < inventories.length; inventoryNr++) {
            if (modified[inventoryNr]) {
                inventories[inventoryNr].setStorageContents(allContents[inventoryNr]);
            }
        }
        return true;
    }

    private int getFirstSimilar(ItemStack item, ItemStack[] contents, int start) {
        for (int i = start; i < contents.length; i++) {
            ItemStack content = contents[i];
            if (content != null && content.isSimilar(item)) {
                return i;
            }
        }
        return -1;
    }

    private ItemStack[] deepCopy(ItemStack[] of) {
        ItemStack[] result = new ItemStack[of.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = of[i] == null ? null : of[i].clone();
        }
        return result;
    }
}
