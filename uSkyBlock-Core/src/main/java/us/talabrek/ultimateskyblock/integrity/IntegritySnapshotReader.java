package us.talabrek.ultimateskyblock.integrity;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.IslandRecord;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.PlayerRecord;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.util.UUIDUtil;
import us.talabrek.ultimateskyblock.uuid.PlayerDB;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reads player and island YAML without touching runtime caches or mutating configuration. */
public class IntegritySnapshotReader {
    private final uSkyBlock plugin;

    public IntegritySnapshotReader(uSkyBlock plugin) {
        this.plugin = plugin;
    }

    public PlayerRecord readPlayer(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.load(file);
        UUID configId = UUIDUtil.fromString(config.getString("player.uuid"));
        UUID fileId = UUIDUtil.fromString(stripExtension(file.getName()));
        UUID uuid = configId != null ? configId : fileId;
        if (uuid == null) {
            throw new InvalidConfigurationException("Player file has no unambiguous UUID: " + file);
        }
        if (configId != null && fileId != null && !configId.equals(fileId)) {
            throw new InvalidConfigurationException("Player UUID differs from filename in " + file);
        }
        boolean hasIslandCoordinates = config.contains("player.islandX") || config.contains("player.islandY")
                || config.contains("player.islandZ");
        if (hasIslandCoordinates && (!config.isInt("player.islandX") || !config.isInt("player.islandY")
                || !config.isInt("player.islandZ"))) {
            throw new InvalidConfigurationException("Player file has invalid island coordinates: " + file);
        }
        int islandY = config.getInt("player.islandY", 0);
        String islandId = islandY != 0
                ? config.getInt("player.islandX") + "," + config.getInt("player.islandZ") : null;
        String name = plugin.getPlayerDB().getName(uuid);
        if (name == null || name.isEmpty() || PlayerDB.UNKNOWN_PLAYER_NAME.equals(name)) {
            name = config.getString("player.displayName", uuid.toString());
        }
        return new PlayerRecord(uuid, name, islandId, new LinkedHashSet<>(config.getStringList("trustedOn")),
                new LinkedHashSet<>(config.getStringList("bannedFrom")), file);
    }

    public IslandRecord readIsland(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.load(file);
        String islandId = stripExtension(file.getName());
        String leaderName = config.getString("party.leader", "");
        String rawLeaderId = config.getString("party.leader-uuid");
        UUID leaderId = UUIDUtil.fromString(rawLeaderId);
        boolean identityNeedsPersist = leaderId == null;
        if (leaderId == null && leaderName != null && !leaderName.isEmpty()) {
            leaderId = plugin.getPlayerDB().getUUIDFromName(leaderName);
        }
        if (PlayerDB.UNKNOWN_PLAYER_UUID.equals(leaderId)) {
            leaderId = null;
        }
        if (leaderId != null) {
            String canonicalName = plugin.getPlayerDB().getName(leaderId);
            if (canonicalName != null && !canonicalName.isEmpty()
                    && !canonicalName.equalsIgnoreCase(leaderName)) {
                leaderName = canonicalName;
                identityNeedsPersist = true;
            }
        }

        Map<UUID, String> members = new LinkedHashMap<>();
        Set<String> invalidMemberKeys = new LinkedHashSet<>();
        ConfigurationSection memberSection = config.getConfigurationSection("party.members");
        if (memberSection != null) {
            for (String key : memberSection.getKeys(false)) {
                UUID memberId = UUIDUtil.fromString(key);
                if (memberId == null) {
                    invalidMemberKeys.add(key);
                } else {
                    members.put(memberId, key);
                }
            }
        }
        return new IslandRecord(islandId, leaderId, leaderName, identityNeedsPersist,
                config.getBoolean("general.ignore", false), members, invalidMemberKeys,
                readUuidList(config, "trust.list"), readUuidList(config, "banned.list"),
                config.isInt("party.currentSize") ? config.getInt("party.currentSize") : -1, file);
    }

    private Set<UUID> readUuidList(YamlConfiguration config, String path) {
        Set<UUID> result = new LinkedHashSet<>();
        for (String value : config.getStringList(path)) {
            UUID uuid = UUIDUtil.fromString(value);
            if (uuid != null) {
                result.add(uuid);
            }
        }
        return result;
    }

    private String stripExtension(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".yml")
                ? name.substring(0, name.length() - 4) : name;
    }
}
