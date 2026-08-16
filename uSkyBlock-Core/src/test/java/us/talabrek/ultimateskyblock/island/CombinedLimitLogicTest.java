package us.talabrek.ultimateskyblock.island;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import us.talabrek.ultimateskyblock.api.async.Callback;
import us.talabrek.ultimateskyblock.handler.WorldGuardHandler;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.UUID;
import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CombinedLimitLogicTest {

    @Test
    public void parsesStructuredWeightedLimit() {
        CombinedLimitLogic logic = createLogic(validConfig());

        assertThat(logic.getLimits().size(), is(1));
        CombinedLimit limit = logic.getLimits().get(0);
        assertThat(limit.name(), is("Hopper-System"));
        assertThat(limit.limit(), is(50L));
        assertThat(limit.blocks(), hasEntry(Material.HOPPER, 1));
        assertThat(limit.entities(), hasEntry(EntityType.HOPPER_MINECART, 2));
        assertThat(logic.getTrackedBlockTypes(), contains(Material.HOPPER));
        assertThat(logic.getTrackedEntityTypes(), contains(EntityType.HOPPER_MINECART));
    }

    @Test
    public void checksProspectiveWeightedValueAtBoundary() {
        CombinedLimitLogic logic = createLogic(validConfig());
        IslandInfo island = mock(IslandInfo.class);
        when(island.getLimitBlockCount(Material.HOPPER)).thenReturn(49);
        when(island.getLimitEntityCount(EntityType.HOPPER_MINECART)).thenReturn(0);

        assertThat(logic.checkBlock(Material.HOPPER, island).state(), is(CombinedLimitLogic.State.ALLOW));
        assertThat(logic.checkEntity(EntityType.HOPPER_MINECART, island).state(), is(CombinedLimitLogic.State.DENY));

        when(island.getLimitBlockCount(Material.HOPPER)).thenReturn(50);
        assertThat(logic.checkBlock(Material.HOPPER, island).state(), is(CombinedLimitLogic.State.DENY));
    }

    @Test
    public void allowsUnknownCountAndReportsUnknown() {
        CombinedLimitLogic logic = createLogic(validConfig());
        IslandInfo island = mock(IslandInfo.class);
        when(island.getLimitBlockCount(Material.HOPPER)).thenReturn(null);
        when(island.getLimitEntityCount(EntityType.HOPPER_MINECART)).thenReturn(4);

        assertThat(logic.checkEntity(EntityType.HOPPER_MINECART, island).state(), is(CombinedLimitLogic.State.UNKNOWN));
        assertThat(logic.getUsage(logic.getLimits().get(0), island).known(), is(false));
    }

    @Test
    public void rejectsEntireGroupForInvalidFactorOrFractionalLimit() {
        YamlConfiguration config = validConfig();
        config.set("options.island.combined-limits.hopper-system.blocks.HOPPER", 0);
        config.set("options.island.combined-limits.fractional.limit", 2.5);
        config.set("options.island.combined-limits.fractional.entities.COW", 1);
        config.set("options.island.combined-limits.negative.limit", -1);
        config.set("options.island.combined-limits.negative.blocks.CHEST", 1);
        config.set("options.island.combined-limits.unknown-block.limit", 1);
        config.set("options.island.combined-limits.unknown-block.blocks.NOT_A_BLOCK", 1);
        config.set("options.island.combined-limits.unknown-entity.limit", 1);
        config.set("options.island.combined-limits.unknown-entity.entities.NOT_AN_ENTITY", 1);

        CombinedLimitLogic logic = createLogic(config);

        assertThat(logic.getLimits().isEmpty(), is(true));
    }

    @Test
    public void usesKeyAsNameAndChecksAllOverlappingGroups() {
        YamlConfiguration config = validConfig();
        String path = "options.island.combined-limits.tighter";
        config.set(path + ".limit", 9);
        config.set(path + ".blocks.HOPPER", 1);
        CombinedLimitLogic logic = createLogic(config);
        IslandInfo island = mock(IslandInfo.class);
        when(island.getLimitBlockCount(Material.HOPPER)).thenReturn(9);
        when(island.getLimitEntityCount(EntityType.HOPPER_MINECART)).thenReturn(0);

        assertThat(logic.getLimits().get(1).name(), is("tighter"));
        assertThat(logic.checkBlock(Material.HOPPER, island).limit().key(), is("tighter"));
    }

    @Test
    public void deniesFurtherAdditionsWhenWeightedValueSaturatesLong() {
        YamlConfiguration config = new YamlConfiguration();
        String path = "options.island.combined-limits.huge";
        config.set(path + ".limit", Long.MAX_VALUE);
        config.set(path + ".blocks.HOPPER", Integer.MAX_VALUE);
        config.set(path + ".blocks.CHEST", Integer.MAX_VALUE);
        config.set(path + ".entities.HOPPER_MINECART", Integer.MAX_VALUE);
        CombinedLimitLogic logic = createLogic(config);
        IslandInfo island = mock(IslandInfo.class);
        when(island.getLimitBlockCount(Material.HOPPER)).thenReturn(Integer.MAX_VALUE);
        when(island.getLimitBlockCount(Material.CHEST)).thenReturn(Integer.MAX_VALUE);
        when(island.getLimitEntityCount(EntityType.HOPPER_MINECART)).thenReturn(Integer.MAX_VALUE);

        assertThat(logic.getUsage(logic.getLimits().get(0), island).value(), is(Long.MAX_VALUE));
        assertThat(logic.checkBlock(Material.HOPPER, island).state(), is(CombinedLimitLogic.State.DENY));
    }

    @Test
    public void tracksEntitySpawnOnlyOnceAndPermanentRemovalOnce() {
        YamlConfiguration config = validConfig();
        uSkyBlock plugin = mock(uSkyBlock.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CombinedLimitLogicTest"));
        CombinedLimitLogic logic = new CombinedLimitLogic(plugin);
        Entity entity = mock(Entity.class);
        Location location = mock(Location.class);
        IslandInfo island = mock(IslandInfo.class);
        UUID entityId = UUID.randomUUID();
        when(entity.getType()).thenReturn(EntityType.HOPPER_MINECART);
        when(entity.getUniqueId()).thenReturn(entityId);
        when(entity.getLocation()).thenReturn(location);
        when(island.getName()).thenReturn("island");
        when(plugin.getIslandInfo(location)).thenReturn(island);
        when(plugin.getIslandInfo("island")).thenReturn(island);

        logic.trackEntitySpawn(entity);
        logic.trackEntitySpawn(entity);
        logic.trackEntityRemoval(entity);
        logic.trackEntityRemoval(entity);

        verify(island, times(1)).adjustLimitEntityCount(EntityType.HOPPER_MINECART, 1);
        verify(island, times(1)).adjustLimitEntityCount(EntityType.HOPPER_MINECART, -1);
    }

    @Test
    public void transfersEntityCounterWithoutBlockingMovement() {
        YamlConfiguration config = validConfig();
        uSkyBlock plugin = mock(uSkyBlock.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CombinedLimitLogicTest"));
        CombinedLimitLogic logic = new CombinedLimitLogic(plugin);
        Entity entity = mock(Entity.class);
        Location from = mock(Location.class);
        Location to = mock(Location.class);
        IslandInfo source = mock(IslandInfo.class);
        IslandInfo destination = mock(IslandInfo.class);
        when(entity.getType()).thenReturn(EntityType.HOPPER_MINECART);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.getLocation()).thenReturn(from);
        when(source.getName()).thenReturn("source");
        when(destination.getName()).thenReturn("destination");
        when(plugin.getIslandInfo(from)).thenReturn(source);
        when(plugin.getIslandInfo(to)).thenReturn(destination);
        when(plugin.getIslandInfo("source")).thenReturn(source);
        logic.trackLoadedEntity(entity);
        clearInvocations(source, destination);

        logic.transferEntity(entity, from, to);
        logic.transferEntity(entity, from, to);

        verify(source, times(1)).adjustLimitEntityCount(EntityType.HOPPER_MINECART, -1);
        verify(destination, times(1)).adjustLimitEntityCount(EntityType.HOPPER_MINECART, 1);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void deduplicatesUnknownCountScansPerIsland() {
        YamlConfiguration config = validConfig();
        uSkyBlock plugin = mock(uSkyBlock.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CombinedLimitLogicTest"));
        CombinedLimitLogic logic = new CombinedLimitLogic(plugin);
        IslandInfo island = mock(IslandInfo.class);
        Location location = mock(Location.class);
        when(island.getName()).thenReturn("island");
        when(island.getIslandLocation()).thenReturn(location);

        try (MockedStatic<WorldGuardHandler> worldGuard = org.mockito.Mockito.mockStatic(WorldGuardHandler.class)) {
            worldGuard.when(() -> WorldGuardHandler.getIslandRegionAt(location)).thenReturn(mock(ProtectedRegion.class));
            logic.requestScan(island);
            logic.requestScan(island);

            ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);
            verify(plugin, times(1)).calculateScoreAsync(isNull(), eq("island"), callback.capture());
            assertThat(logic.isScanInProgress("island"), is(true));
            callback.getValue().run();
            assertThat(logic.isScanInProgress("island"), is(false));
        }
    }

    @Test
    public void rendersUnknownWeightedSummaryAndRawMenuBreakdown() {
        CombinedLimitLogic logic = createLogic(validConfig());
        IslandInfo island = mock(IslandInfo.class);
        when(island.getLimitBlockCount(Material.HOPPER)).thenReturn(null);
        when(island.getLimitEntityCount(EntityType.HOPPER_MINECART)).thenReturn(4);
        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();

        assertThat(serializer.serialize(logic.getSummary(island)), containsString("Hopper-System"));
        assertThat(serializer.serialize(logic.getSummary(island)), containsString("?"));
        assertThat(serializer.serialize(logic.getMenuDetails(island)), containsString("HOPPER: ? x 1"));
    }

    private YamlConfiguration validConfig() {
        YamlConfiguration config = new YamlConfiguration();
        String path = "options.island.combined-limits.hopper-system";
        config.set(path + ".name", "Hopper-System");
        config.set(path + ".limit", 50);
        config.set(path + ".blocks.HOPPER", 1);
        config.set(path + ".entities.HOPPER_MINECART", 2);
        return config;
    }

    private CombinedLimitLogic createLogic(YamlConfiguration config) {
        uSkyBlock plugin = mock(uSkyBlock.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CombinedLimitLogicTest"));
        return new CombinedLimitLogic(plugin);
    }
}
