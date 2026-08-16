package us.talabrek.ultimateskyblock.island;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import us.talabrek.ultimateskyblock.api.async.Callback;
import us.talabrek.ultimateskyblock.api.event.IslandInfoEvent;
import us.talabrek.ultimateskyblock.handler.WorldGuardHandler;
import us.talabrek.ultimateskyblock.island.level.IslandScore;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.util.EntityUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static dk.lockfuglsang.minecraft.po.I18nUtil.tr;

/**
 * Loads, evaluates and tracks limits which combine multiple weighted block and entity types.
 */
public class CombinedLimitLogic {
    public enum State { ALLOW, UNKNOWN, DENY }

    public record CheckResult(State state, CombinedLimit limit) {
        public static CheckResult allow() {
            return new CheckResult(State.ALLOW, null);
        }
    }

    public record Usage(boolean known, long value) {}

    private static final String CONFIG_PATH = "options.island.combined-limits";

    private final uSkyBlock plugin;
    private final Logger log;
    private final List<CombinedLimit> limits = new ArrayList<>();
    private final Map<Material, List<CombinedLimit>> limitsByBlock = new EnumMap<>(Material.class);
    private final Map<EntityType, List<CombinedLimit>> limitsByEntity = new EnumMap<>(EntityType.class);
    private final Set<Material> trackedBlockTypes = new LinkedHashSet<>();
    private final Set<EntityType> trackedEntityTypes = new LinkedHashSet<>();
    private final Set<String> scansInProgress = ConcurrentHashMap.newKeySet();
    private final Map<String, List<Runnable>> scanCompletionHandlers = new ConcurrentHashMap<>();
    private final Map<UUID, String> entityOwners = new ConcurrentHashMap<>();

    public CombinedLimitLogic(uSkyBlock plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger() != null ? plugin.getLogger() : Logger.getLogger(CombinedLimitLogic.class.getName());
        load(plugin.getConfig().getConfigurationSection(CONFIG_PATH));
    }

    private void load(ConfigurationSection root) {
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                warn(key, "must be a configuration section");
                continue;
            }
            CombinedLimit limit = readLimit(key, section);
            if (limit == null) {
                continue;
            }
            limits.add(limit);
            limit.blocks().keySet().forEach(type -> {
                trackedBlockTypes.add(type);
                limitsByBlock.computeIfAbsent(type, ignored -> new ArrayList<>()).add(limit);
            });
            limit.entities().keySet().forEach(type -> {
                trackedEntityTypes.add(type);
                limitsByEntity.computeIfAbsent(type, ignored -> new ArrayList<>()).add(limit);
            });
        }
    }

    private CombinedLimit readLimit(String key, ConfigurationSection section) {
        Object configuredLimit = section.get("limit");
        Long max = asWholeLong(configuredLimit);
        if (max == null || max < 0) {
            warn(key, "limit must be a non-negative integer");
            return null;
        }
        if (section.contains("blocks") && !section.isConfigurationSection("blocks")) {
            warn(key, "blocks must be a configuration section");
            return null;
        }
        if (section.contains("entities") && !section.isConfigurationSection("entities")) {
            warn(key, "entities must be a configuration section");
            return null;
        }
        Map<Material, Integer> blocks = readBlocks(key, section.getConfigurationSection("blocks"));
        Map<EntityType, Integer> entities = readEntities(key, section.getConfigurationSection("entities"));
        if (blocks == null || entities == null) {
            return null;
        }
        if (blocks.isEmpty() && entities.isEmpty()) {
            warn(key, "must contain at least one block or entity");
            return null;
        }
        String name = section.getString("name", key);
        if (name == null || name.isBlank()) {
            name = key;
        }
        return new CombinedLimit(key, name, max, blocks, entities);
    }

    private Map<Material, Integer> readBlocks(String group, ConfigurationSection section) {
        Map<Material, Integer> result = new LinkedHashMap<>();
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            Integer factor = readFactor(section, key);
            boolean supportedBlock = material != null && (Bukkit.getServer() == null || material.isBlock());
            if (!supportedBlock || factor == null) {
                warn(group, !supportedBlock ? "unknown or unsupported block material " + key
                        : "factor for material " + key + " must be a positive integer");
                return null;
            }
            result.put(material, factor);
        }
        return result;
    }

    private Map<EntityType, Integer> readEntities(String group, ConfigurationSection section) {
        Map<EntityType, Integer> result = new LinkedHashMap<>();
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            EntityType type;
            try {
                type = EntityType.valueOf(key.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                type = null;
            }
            Integer factor = readFactor(section, key);
            if (type == null || type == EntityType.PLAYER || type == EntityType.UNKNOWN || factor == null) {
                warn(group, type == null || type == EntityType.PLAYER || type == EntityType.UNKNOWN
                        ? "unknown or unsupported entity type " + key
                        : "factor for entity " + key + " must be a positive integer");
                return null;
            }
            result.put(type, factor);
        }
        return result;
    }

    private Integer readFactor(ConfigurationSection section, String key) {
        Long factor = asWholeLong(section.get(key));
        if (factor == null) {
            return null;
        }
        return factor > 0 && factor <= Integer.MAX_VALUE ? factor.intValue() : null;
    }

    private Long asWholeLong(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        long result = number.longValue();
        double asDouble = number.doubleValue();
        return Double.isFinite(asDouble) && asDouble == (double) result ? result : null;
    }

    private void warn(String group, String message) {
        log.warning("Invalid combined limit '" + group + "': " + message + "; the group is disabled");
    }

    public List<CombinedLimit> getLimits() {
        return Collections.unmodifiableList(limits);
    }

    public Set<Material> getTrackedBlockTypes() {
        return Collections.unmodifiableSet(trackedBlockTypes);
    }

    public Set<EntityType> getTrackedEntityTypes() {
        return Collections.unmodifiableSet(trackedEntityTypes);
    }

    public boolean hasBlockLimits() {
        return !trackedBlockTypes.isEmpty();
    }

    public boolean hasEntityLimits() {
        return !trackedEntityTypes.isEmpty();
    }

    public boolean tracks(Material material) {
        return trackedBlockTypes.contains(material);
    }

    public boolean tracks(EntityType entityType) {
        return trackedEntityTypes.contains(entityType);
    }

    public CheckResult checkBlock(Material material, IslandInfo islandInfo) {
        return check(limitsByBlock.get(material), islandInfo, material, null);
    }

    public CheckResult checkEntity(EntityType entityType, IslandInfo islandInfo) {
        return check(limitsByEntity.get(entityType), islandInfo, null, entityType);
    }

    private CheckResult check(List<CombinedLimit> candidates, IslandInfo islandInfo, Material addedBlock, EntityType addedEntity) {
        if (candidates == null || candidates.isEmpty()) {
            return CheckResult.allow();
        }
        CombinedLimit unknown = null;
        for (CombinedLimit limit : candidates) {
            Usage usage = getUsage(limit, islandInfo);
            if (!usage.known()) {
                unknown = limit;
                continue;
            }
            int factor = addedBlock != null ? limit.blocks().get(addedBlock) : limit.entities().get(addedEntity);
            long prospective = saturatingAdd(usage.value(), factor);
            if (usage.value() == Long.MAX_VALUE || prospective > limit.limit()) {
                return new CheckResult(State.DENY, limit);
            }
        }
        return unknown != null ? new CheckResult(State.UNKNOWN, unknown) : CheckResult.allow();
    }

    public Usage getUsage(CombinedLimit limit, IslandInfo islandInfo) {
        long value = 0;
        for (Map.Entry<Material, Integer> entry : limit.blocks().entrySet()) {
            Integer count = islandInfo.getLimitBlockCount(entry.getKey());
            if (count == null) {
                return new Usage(false, 0);
            }
            value = saturatingAdd(value, saturatingMultiply(count, entry.getValue()));
        }
        for (Map.Entry<EntityType, Integer> entry : limit.entities().entrySet()) {
            Integer count = islandInfo.getLimitEntityCount(entry.getKey());
            if (count == null) {
                return new Usage(false, 0);
            }
            value = saturatingAdd(value, saturatingMultiply(count, entry.getValue()));
        }
        return new Usage(true, value);
    }

    private long saturatingMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private long saturatingAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    public void requestScan(IslandInfo islandInfo) {
        requestScan(islandInfo, null, null);
    }

    public synchronized void requestScan(IslandInfo islandInfo, Player player, Runnable completionHandler) {
        String islandName = islandInfo.getName();
        if (WorldGuardHandler.getIslandRegionAt(islandInfo.getIslandLocation()) == null) {
            if (completionHandler != null) {
                completionHandler.run();
            }
            return;
        }
        if (completionHandler != null) {
            scanCompletionHandlers.computeIfAbsent(islandName,
                    ignored -> Collections.synchronizedList(new ArrayList<>())).add(completionHandler);
        }
        if (!scansInProgress.add(islandName)) {
            return;
        }
        Callback<us.talabrek.ultimateskyblock.api.model.IslandScore> callback =
                new Callback<us.talabrek.ultimateskyblock.api.model.IslandScore>() {
            @Override
            public void run() {
                completeScan(islandName);
            }
        };
        try {
            if (player != null) {
                plugin.fireAsyncEvent(new IslandInfoEvent(player, islandInfo.getIslandLocation(), callback));
            } else {
                plugin.calculateScoreAsync(null, islandName, callback);
            }
        } catch (RuntimeException e) {
            completeScan(islandName);
            throw e;
        }
    }

    private synchronized void completeScan(String islandName) {
        scansInProgress.remove(islandName);
        List<Runnable> handlers = scanCompletionHandlers.remove(islandName);
        if (handlers != null) {
            for (Runnable handler : handlers) {
                try {
                    handler.run();
                } catch (RuntimeException e) {
                    log.warning("Combined-limit scan completion for island '" + islandName + "' failed: "
                            + e.getMessage());
                }
            }
        }
    }

    public boolean isScanInProgress(String islandName) {
        return scansInProgress.contains(islandName);
    }

    public void updateCounts(Location islandLocation, IslandScore score) {
        IslandInfo islandInfo = plugin.getIslandInfo(islandLocation);
        if (islandInfo != null) {
            islandInfo.replaceLimitCounts(score.getLimitedMaterialCounts(),
                    plugin.getBlockLimitLogic().getTrackedMaterials(), score.getLimitedEntityCounts(), trackedEntityTypes);
        }
    }

    public boolean trackEntitySpawn(Entity entity) {
        if (!tracks(entity.getType())) {
            return false;
        }
        IslandInfo islandInfo = plugin.getIslandInfo(entity.getLocation());
        if (islandInfo == null) {
            return false;
        }
        String previous = entityOwners.putIfAbsent(entity.getUniqueId(), islandInfo.getName());
        if (previous == null) {
            islandInfo.adjustLimitEntityCount(entity.getType(), 1);
            return true;
        }
        return false;
    }

    public void trackLoadedEntity(Entity entity) {
        if (!tracks(entity.getType())) {
            return;
        }
        IslandInfo islandInfo = plugin.getIslandInfo(entity.getLocation());
        if (islandInfo != null) {
            entityOwners.putIfAbsent(entity.getUniqueId(), islandInfo.getName());
        }
    }

    public void trackEntityRemoval(Entity entity) {
        if (!tracks(entity.getType())) {
            return;
        }
        String islandName = entityOwners.remove(entity.getUniqueId());
        IslandInfo islandInfo = islandName != null ? plugin.getIslandInfo(islandName) : null;
        if (islandInfo != null) {
            islandInfo.adjustLimitEntityCount(entity.getType(), -1);
        }
    }

    public void transferEntity(Entity entity, Location from, Location to) {
        if (!tracks(entity.getType()) || from == null || to == null) {
            return;
        }
        IslandInfo sourceAtLocation = plugin.getIslandInfo(from);
        IslandInfo destination = plugin.getIslandInfo(to);
        String sourceName = entityOwners.get(entity.getUniqueId());
        if (sourceName == null && sourceAtLocation != null) {
            sourceName = sourceAtLocation.getName();
        }
        String destinationName = destination != null ? destination.getName() : null;
        if (sourceName == null ? destinationName == null : sourceName.equals(destinationName)) {
            return;
        }
        IslandInfo source = sourceName != null ? plugin.getIslandInfo(sourceName) : null;
        if (source != null) {
            source.adjustLimitEntityCount(entity.getType(), -1);
        }
        if (destination != null) {
            destination.adjustLimitEntityCount(entity.getType(), 1);
            entityOwners.put(entity.getUniqueId(), destinationName);
        } else {
            entityOwners.remove(entity.getUniqueId());
        }
    }

    public Component getSummary(IslandInfo islandInfo) {
        Component result = Component.empty();
        for (CombinedLimit limit : limits) {
            Usage usage = getUsage(limit, islandInfo);
            String current = usage.known() ? Long.toString(usage.value()) : "?";
            String coloredCurrent = !usage.known() || usage.value() >= limit.limit() ? tr("\u00a7c{0}", current) : current;
            Component line = LegacyComponentSerializer.legacySection().deserialize(
                    tr("\u00a77{0}: \u00a7a{1}\u00a77 (max. {2})", limit.name(), coloredCurrent, limit.limit()));
            line = line.hoverEvent(HoverEvent.showText(createHover(limit, islandInfo)));
            if (!result.equals(Component.empty())) {
                result = result.append(Component.newline());
            }
            result = result.append(line);
        }
        return result;
    }

    public Component getMenuDetails(IslandInfo islandInfo) {
        Component result = Component.empty();
        for (CombinedLimit limit : limits) {
            Component details = Component.text(limit.name() + ":\n").append(createHover(limit, islandInfo));
            if (!result.equals(Component.empty())) {
                result = result.append(Component.newline());
            }
            result = result.append(details);
        }
        return result;
    }

    private Component createHover(CombinedLimit limit, IslandInfo islandInfo) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : limit.blocks().entrySet()) {
            Integer count = islandInfo.getLimitBlockCount(entry.getKey());
            lines.add(formatPart(entry.getKey().name(), count, entry.getValue()));
        }
        for (Map.Entry<EntityType, Integer> entry : limit.entities().entrySet()) {
            Integer count = islandInfo.getLimitEntityCount(entry.getKey());
            lines.add(formatPart(EntityUtil.getEntityDisplayName(entry.getKey()), count, entry.getValue()));
        }
        return Component.text(String.join("\n", lines));
    }

    private String formatPart(String name, Integer count, int factor) {
        return count == null ? name + ": ? x " + factor : name + ": " + count + " x " + factor + " = " + saturatingMultiply(count, factor);
    }
}
