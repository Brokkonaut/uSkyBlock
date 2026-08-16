package us.talabrek.ultimateskyblock.event;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockedStatic;
import us.talabrek.ultimateskyblock.handler.WorldGuardHandler;
import us.talabrek.ultimateskyblock.island.CombinedLimit;
import us.talabrek.ultimateskyblock.island.CombinedLimitLogic;
import us.talabrek.ultimateskyblock.island.IslandInfo;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.world.WorldManager;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SpawnEventsTest {
    private SpawnEvents spawnEvents;
    private WorldManager worldManager;
    private uSkyBlock fakePlugin;
    private CombinedLimitLogic combinedLimitLogic;

    @Before
    public void setUp() {
        fakePlugin = Mockito.mock(uSkyBlock.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("options.island.spawn-limits.enabled", false);
        worldManager = mock(WorldManager.class);
        combinedLimitLogic = mock(CombinedLimitLogic.class);

        when(fakePlugin.getWorldManager()).thenReturn(worldManager);
        when(fakePlugin.getConfig()).thenReturn(config);
        when(fakePlugin.getCombinedLimitLogic()).thenReturn(combinedLimitLogic);

        spawnEvents = new SpawnEvents(fakePlugin);
    }

    @Test
    public void onPhantomSpawn_noPhantom() {
        Zombie entity = mock(Zombie.class);
        CreatureSpawnEvent event = new CreatureSpawnEvent(entity, CreatureSpawnEvent.SpawnReason.NATURAL);
        spawnEvents.onPhantomSpawn(event);

        assertFalse(event.isCancelled());
    }

    @Test
    public void onPhantomSpawn_overworldAllowed() {
        when(worldManager.isSkyWorld(any(World.class))).thenReturn(true);

        CreatureSpawnEvent event = new CreatureSpawnEvent(getFakePhantom(),
                CreatureSpawnEvent.SpawnReason.NATURAL);
        spawnEvents.setPhantomsInOverworld(true);
        spawnEvents.onPhantomSpawn(event);

        assertFalse(event.isCancelled());
    }

    @Test
    public void onPhantomSpawn_overworldNotAllowed() {
        when(worldManager.isSkyWorld(any(World.class))).thenReturn(true);

        CreatureSpawnEvent event = new CreatureSpawnEvent(getFakePhantom(),
                CreatureSpawnEvent.SpawnReason.NATURAL);
        spawnEvents.setPhantomsInOverworld(false);
        spawnEvents.onPhantomSpawn(event);

        assertTrue(event.isCancelled());
    }

    @Test
    public void onPhantomSpawn_notInSkyworld() {
        when(worldManager.isSkyWorld(any(World.class))).thenReturn(false);

        CreatureSpawnEvent event = new CreatureSpawnEvent(getFakePhantom(),
                CreatureSpawnEvent.SpawnReason.NATURAL);
        spawnEvents.setPhantomsInOverworld(false);
        spawnEvents.onPhantomSpawn(event);

        assertFalse(event.isCancelled());
    }

    @Test
    public void onPhantomSpawn_notNaturalSpawned() {
        when(worldManager.isSkyWorld(any(World.class))).thenReturn(true);

        CreatureSpawnEvent event = new CreatureSpawnEvent(getFakePhantom(),
                CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);
        spawnEvents.setPhantomsInOverworld(false);
        spawnEvents.onPhantomSpawn(event);

        assertFalse(event.isCancelled());
    }

    @Test
    public void onPhantomSpawn_netherAllowed() {
        when(worldManager.isSkyNether(any(World.class))).thenReturn(true);

        CreatureSpawnEvent event = new CreatureSpawnEvent(getFakePhantom(),
                CreatureSpawnEvent.SpawnReason.NATURAL);
        spawnEvents.setPhantomsInNether(true);
        spawnEvents.onPhantomSpawn(event);

        assertFalse(event.isCancelled());
    }

    @Test
    public void onPhantomSpawn_netherNotAllowed() {
        when(worldManager.isSkyNether(any(World.class))).thenReturn(true);

        CreatureSpawnEvent event = new CreatureSpawnEvent(getFakePhantom(),
                CreatureSpawnEvent.SpawnReason.NATURAL);
        spawnEvents.setPhantomsInNether(false);
        spawnEvents.onPhantomSpawn(event);

        assertTrue(event.isCancelled());
    }

    @Test
    public void onPhantomSpawn_notInNetherWorld() {
        when(worldManager.isSkyNether(any(World.class))).thenReturn(false);

        CreatureSpawnEvent event = new CreatureSpawnEvent(getFakePhantom(),
                CreatureSpawnEvent.SpawnReason.NATURAL);
        spawnEvents.setPhantomsInNether(false);
        spawnEvents.onPhantomSpawn(event);

        assertFalse(event.isCancelled());
    }

    @Test
    public void genericEntitySpawnIsDeniedByCombinedLimit() {
        World world = mock(World.class);
        Location location = mock(Location.class);
        Entity entity = mock(Entity.class);
        IslandInfo island = mock(IslandInfo.class);
        CombinedLimit limit = new CombinedLimit("hopper-system", "Hopper-System", 50,
                Collections.emptyMap(), Map.of(EntityType.HOPPER_MINECART, 2));
        when(entity.getType()).thenReturn(EntityType.HOPPER_MINECART);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.getLocation()).thenReturn(location);
        when(location.getWorld()).thenReturn(world);
        when(worldManager.isSkyAssociatedWorld(world)).thenReturn(true);
        when(combinedLimitLogic.tracks(EntityType.HOPPER_MINECART)).thenReturn(true);
        when(combinedLimitLogic.checkEntity(EntityType.HOPPER_MINECART, island))
                .thenReturn(new CombinedLimitLogic.CheckResult(CombinedLimitLogic.State.DENY, limit));
        when(fakePlugin.getIslandInfo("island")).thenReturn(island);

        try (MockedStatic<WorldGuardHandler> worldGuard = Mockito.mockStatic(WorldGuardHandler.class)) {
            worldGuard.when(() -> WorldGuardHandler.getIslandNameAt(location)).thenReturn("island");
            EntitySpawnEvent event = new EntitySpawnEvent(entity);
            spawnEvents.onEntitySpawn(event);
            assertTrue(event.isCancelled());
        }
    }

    @Test
    public void onlyPermanentEntityRemovalChangesCounter() {
        Entity entity = mock(Entity.class);

        spawnEvents.onEntityRemove(new EntityRemoveEvent(entity, EntityRemoveEvent.Cause.UNLOAD));
        verify(combinedLimitLogic, never()).trackEntityRemoval(entity);

        spawnEvents.onEntityRemove(new EntityRemoveEvent(entity, EntityRemoveEvent.Cause.DEATH));
        verify(combinedLimitLogic).trackEntityRemoval(entity);
    }

    @Test
    public void operatorEntityPlacementBypassesCombinedLimit() {
        World world = mock(World.class);
        Location location = mock(Location.class);
        Entity entity = mock(Entity.class);
        Player player = mock(Player.class);
        UUID entityId = UUID.randomUUID();
        when(entity.getType()).thenReturn(EntityType.HOPPER_MINECART);
        when(entity.getUniqueId()).thenReturn(entityId);
        when(entity.getLocation()).thenReturn(location);
        when(entity.getWorld()).thenReturn(world);
        when(location.getWorld()).thenReturn(world);
        when(player.isOp()).thenReturn(true);
        when(worldManager.isSkyAssociatedWorld(world)).thenReturn(true);
        when(combinedLimitLogic.tracks(EntityType.HOPPER_MINECART)).thenReturn(true);
        when(combinedLimitLogic.trackEntitySpawn(entity)).thenReturn(true);
        EntityPlaceEvent placeEvent = new EntityPlaceEvent(entity, player, mock(Block.class),
                BlockFace.UP, EquipmentSlot.HAND);

        spawnEvents.onEntityPlace(placeEvent);
        spawnEvents.onEntityPlaceCount(placeEvent);
        EntitySpawnEvent spawnEvent = new EntitySpawnEvent(entity);
        spawnEvents.onEntitySpawn(spawnEvent);

        assertFalse(spawnEvent.isCancelled());
        verify(combinedLimitLogic, never()).checkEntity(eq(EntityType.HOPPER_MINECART), any(IslandInfo.class));
    }

    @Test
    public void cancelledSpawnDoesNotChangeRawCounter() {
        Entity entity = mock(Entity.class);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        EntitySpawnEvent event = new EntitySpawnEvent(entity);
        event.setCancelled(true);

        spawnEvents.onEntitySpawnCount(event);

        verify(combinedLimitLogic, never()).trackEntitySpawn(entity);
    }

    private Phantom getFakePhantom() {
        World fakeWorld = mock(World.class);
        when(fakeWorld.getName()).thenReturn("skyworld");
        Phantom entity = mock(Phantom.class);
        when(entity.getWorld()).thenReturn(fakeWorld);

        return entity;
    }
}
