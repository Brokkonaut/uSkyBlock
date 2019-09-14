package us.talabrek.ultimateskyblock.island.level;

import org.bukkit.Material;

public interface BlockMatchVisitor {
    void visit(Material node);
}
