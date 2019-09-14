package us.talabrek.ultimateskyblock.island.level;

import org.bukkit.Material;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class BlockLevelConfigMap {
    private final BlockLevelConfigBuilder defaultBuilder;
    private final Map<Material, Set<BlockLevelConfig>> searchMap = new HashMap<>();

    public BlockLevelConfigMap(Collection<BlockLevelConfig> configCollection, BlockLevelConfigBuilder defaultBuilder) {
        this.defaultBuilder = defaultBuilder;
        configCollection.stream().forEach(m -> m.accept(new ExplodeMapVisitor(m)));
    }

    public synchronized BlockLevelConfig get(Material material) {
        // search map
        Set<BlockLevelConfig> searchSet = searchMap.getOrDefault(material, new HashSet<>());
        BlockLevelConfig existing = search(searchSet, material);
        if (existing != null) {
            return existing;
        }
        BlockLevelConfig newConfig = defaultBuilder.copy().base(material).build();
        searchSet.add(newConfig);
        searchMap.put(material, searchSet);
        return newConfig;
    }

    private BlockLevelConfig search(Set<BlockLevelConfig> searchSet, Material material) {
        List<BlockLevelConfig> match = searchSet.stream()
                .filter(p -> p.matches(material))
                .distinct()
                .sorted((a,b) -> -a.getKey().compareTo(b.getKey())) // best match = longest string = desc ordering
                .collect(Collectors.toList());
        if (!match.isEmpty()) {
            return match.get(0);
        }
        return null;
    }

    public BlockLevelConfig getDefault() {
        return defaultBuilder.copy().base(Material.AIR).build();
    }

    public List<BlockLevelConfig> values() {
        return searchMap.values().stream()
                .flatMap(Collection::stream)
                .distinct()
                .sorted(Comparator.comparing(BlockLevelConfig::getKey))
                .collect(Collectors.toList());
    }

    private class ExplodeMapVisitor implements BlockMatchVisitor {
        private BlockLevelConfig config;

        public ExplodeMapVisitor(BlockLevelConfig config) {
            this.config = config;
        }

        @Override
        public void visit(Material node) {
            Set<BlockLevelConfig> searchSet = searchMap.getOrDefault(node, new HashSet<>());
            searchSet.add(config);
            searchMap.put(node, searchSet);
        }
    }
}
