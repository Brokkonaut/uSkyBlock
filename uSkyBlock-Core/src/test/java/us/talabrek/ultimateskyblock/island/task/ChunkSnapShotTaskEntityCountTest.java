package us.talabrek.ultimateskyblock.island.task;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.junit.Test;
import us.talabrek.ultimateskyblock.api.async.Callback;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChunkSnapShotTaskEntityCountTest {

    @Test
    @SuppressWarnings("unchecked")
    public void countsOnlyConfiguredEntitiesInsideIslandRegion() {
        uSkyBlock plugin = mock(uSkyBlock.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("async.maxMs", 1000);
        when(plugin.getConfig()).thenReturn(config);
        World world = mock(World.class);
        Location islandLocation = new Location(world, 0, 64, 0);
        Chunk chunk = mock(Chunk.class);
        Chunk emptyChunk = mock(Chunk.class);
        when(world.getChunkAt(anyInt(), anyInt())).thenAnswer(invocation ->
                invocation.getArgument(0, Integer.class) == 0 && invocation.getArgument(1, Integer.class) == 0
                        ? chunk : emptyChunk);
        when(chunk.getChunkSnapshot(false, false, false)).thenReturn(mock(ChunkSnapshot.class));
        when(emptyChunk.getChunkSnapshot(false, false, false)).thenReturn(mock(ChunkSnapshot.class));
        when(emptyChunk.getEntities()).thenReturn(new Entity[0]);
        Entity inside = mock(Entity.class);
        Entity outside = mock(Entity.class);
        when(inside.getType()).thenReturn(EntityType.HOPPER_MINECART);
        when(inside.getLocation()).thenReturn(new Location(world, 1, 64, 1));
        when(outside.getType()).thenReturn(EntityType.HOPPER_MINECART);
        when(outside.getLocation()).thenReturn(new Location(world, 1, 300, 1));
        when(chunk.getEntities()).thenReturn(new Entity[] {inside, outside});
        ProtectedRegion region = new ProtectedCuboidRegion("island",
                BlockVector3.at(0, 0, 0), BlockVector3.at(15, 255, 15));
        Map<EntityType, Integer> counts = new EnumMap<>(EntityType.class);
        Callback<java.util.List<ChunkSnapshot>> callback = mock(Callback.class);
        ChunkSnapShotTask task = new ChunkSnapShotTask(plugin, islandLocation, region,
                Set.of(EntityType.HOPPER_MINECART), counts, callback);

        while (!task.execute()) {
            // Continue the incremental task until all region chunks have been visited.
        }

        assertThat(counts, hasEntry(EntityType.HOPPER_MINECART, 1));
    }
}
