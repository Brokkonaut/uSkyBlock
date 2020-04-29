package us.talabrek.ultimateskyblock.uuid;

import de.iani.playerUUIDCache.PlayerUUIDCacheAPI;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * PlayerDB using the PlayerUUIDCache plugin
 */
public class PlayerUUIDCachePlayerDB extends BukkitPlayerDB {
    private final PlayerUUIDCacheAPI playerUUIDCache;

    public PlayerUUIDCachePlayerDB(Plugin playerUUIDCache) {
        this.playerUUIDCache = (PlayerUUIDCacheAPI) playerUUIDCache;
    }

    @Override
    public UUID getUUIDFromName(String name) {
        return getUUIDFromName(name, false);
    }

    @Override
    public UUID getUUIDFromName(String name, boolean lookup) {
        if (UNKNOWN_PLAYER_NAME.equalsIgnoreCase(name)) {
            return UNKNOWN_PLAYER_UUID;
        }
        Player onlinePlayer = getPlayer(name);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId();
        }
        OfflinePlayer offlinePlayer = playerUUIDCache.getPlayer(name, lookup);
        return offlinePlayer == null ? UNKNOWN_PLAYER_UUID : offlinePlayer.getUniqueId();
    }

    @Override
    public String getName(UUID uuid) {
        if (UNKNOWN_PLAYER_UUID.equals(uuid)) {
            return UNKNOWN_PLAYER_NAME;
        }
        Player onlinePlayer = getPlayer(uuid);
        if (onlinePlayer != null) {
            return onlinePlayer.getName();
        }
        OfflinePlayer offlinePlayer = playerUUIDCache.getPlayer(uuid);
        return offlinePlayer == null ? UNKNOWN_PLAYER_NAME : offlinePlayer.getName();
    }

    @Override
    public OfflinePlayer getOfflinePlayer(UUID uuid) {
        return playerUUIDCache.getPlayer(uuid);
    }

    @Override
    public Player getPlayer(String name) {
        return Bukkit.getPlayerExact(name);
    }
}
