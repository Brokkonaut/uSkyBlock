package us.talabrek.ultimateskyblock.menu;

import dk.lockfuglsang.minecraft.yml.YmlConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;
import us.talabrek.ultimateskyblock.uSkyBlock;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;

import static dk.lockfuglsang.minecraft.po.I18nUtil.tr;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.Gson;

/**
 * Created by R4zorax on 03/10/2015.
 */
public class AbstractConfigMenu {
    private static final Pattern UUID_PATTERN = Pattern.compile("^.*Id:\\\"(?<uuid>[^\\\"]+)\\\".*");
    private YmlConfiguration menuConfig;

    public AbstractConfigMenu(YmlConfiguration menuConfig) {
        this.menuConfig = menuConfig;
    }

    public YmlConfiguration getMenuConfig() {
        return menuConfig;
    }

    protected static void ensureCapacity(List<ItemStack> menuList, int index) {
        if (index >= menuList.size()) {
            menuList.addAll(Arrays.asList(new ItemStack[Math.max(index - menuList.size(), 9)]));
        }
    }

    protected ItemStack createItem(Material icon, String title, List<String> lore) {
        ItemStack itemStack = new ItemStack(icon, 1);
        ItemMeta meta = itemStack.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(lore);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    protected static int getIndex(int row, int col) {
        return row*9 + col;
    }

    boolean isBlackListed(String file, String currentPath) {
        return menuConfig.getStringList(file + ".blacklist").contains(currentPath);
    }

    boolean isReadonly(String file, String path) {
        return menuConfig.getStringList(file + ".readonly").contains(path);
    }

    protected int getPage(String s) {
        String format = tr("\u00a77Page {0}");
        try {
            Object[] parsed = new MessageFormat(format).parse(s);
            if (parsed != null && parsed.length > 0) {
                return Integer.parseInt("" + parsed[0]);
            }
        } catch (ParseException e) {
            // Ignore
        }
        return 1;
    }

    protected ItemStack createItem(String item) {
        if (item == null) {
            return null;
        }
        ItemStack itemStack = new ItemStack(Material.PLAYER_HEAD, 1);
        String[] parts = item.split(" ");
        if (parts.length != 3) {
            return null;
        }
        String name = parts[0];
        UUID uuid = UUID.fromString(parts[1]);
        String texturesString = parts[2];

        if (itemStack.getItemMeta() instanceof SkullMeta head) {
            PlayerProfile profile = Bukkit.createProfile(uuid, name);
            profile.setProperty(new ProfileProperty("textures", texturesString));
            head.setPlayerProfile(profile);
            try {
                head.setDisplayName(tr(name));
            } catch (Exception ex) {
                // ignore
                head.setDisplayName(name);
            }
            itemStack.setItemMeta(head);
        }
        return itemStack;
    }
}
