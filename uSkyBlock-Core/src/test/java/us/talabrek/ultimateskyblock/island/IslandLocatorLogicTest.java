package us.talabrek.ultimateskyblock.island;

import dk.lockfuglsang.minecraft.yml.YmlConfiguration;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import us.talabrek.ultimateskyblock.Settings;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.world.WorldManager;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IslandLocatorLogicTest {
    @Test
    public void testNextIslandLocation() throws Exception {
        Settings.island_distance = 1;
        Location p = new Location(null, 0, 0, 0);
        File csvFile = File.createTempFile("newislands", ".csv");
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile))) {
            for (int i = 0; i < 49; i++) {
                p = IslandLocatorLogic.nextIslandLocation(p);
                writer.println(p.getBlockX() + ";" + p.getBlockZ());
            }
        }
        System.out.println("Wrote first 49 island locations to " + csvFile);
    }

    @Test
    public void testNextIslandLocationReservation() throws Exception {
        Settings.island_distance = 10;
        uSkyBlock plugin = createPluginMock();
        IslandLocatorLogic locator = new IslandLocatorLogic(plugin);
        Player player = createPlayerMock();
        Location location1 = locator.getNextIslandLocation(player);
        assertThat(location1, notNullValue());
        Location location2 = locator.getNextIslandLocation(player);
        assertThat(location2, notNullValue());
        assertThat(location1, is(not(location2)));
    }

    @Test
    public void testNextIslandLocationReservationConcurrency() throws Exception {
        Settings.island_distance = 10;
        uSkyBlock plugin = createPluginMock();
        final IslandLocatorLogic locator = new IslandLocatorLogic(plugin);
        final List<Location> locations = new ArrayList<>();
        ThreadGroup threadGroup = new ThreadGroup("My");
        for (int i = 0; i < 10; i++) {
            Thread t = new Thread(threadGroup, new Runnable() {
                @Override
                public void run() {
                    Player player = createPlayerMock();
                    Location location = locator.getNextIslandLocation(player);
                    locations.add(location);
                }
            });
            t.start();
        }
        while (threadGroup.activeCount() > 0) {
            Thread.sleep(10);
        }
        Set<Location> set = new HashSet<>(locations);
        assertThat(locations.size(), greaterThan(0));
        assertThat("duplicate locations detected", set.size(), is(locations.size()));
    }

    @Test
    public void moveReservationsBlockBothIdsUntilReleased() {
        IslandLocatorLogic locator = new IslandLocatorLogic(createPluginMock());

        assertThat(locator.reserveForMove("10,20", "30,40"), is(true));
        assertThat(locator.reserveForMove("10,20", "50,60"), is(false));
        assertThat(locator.isReserved(new Location(null, 10, 0, 20)), is(true));
        assertThat(locator.isReserved(new Location(null, 30, 0, 40)), is(true));

        locator.releaseMoveReservation("10,20", "30,40");
        assertThat(locator.isReserved(new Location(null, 10, 0, 20)), is(false));
        assertThat(locator.isReserved(new Location(null, 30, 0, 40)), is(false));
    }

    private Player createPlayerMock() {
        Player player = mock(Player.class);
        when(player.getLocation()).then(new Answer<Location>() {
            @Override
            public Location answer(InvocationOnMock invocationOnMock) throws Throwable {
                return new Location(null, 100, 100, 100);
            }
        });
        return player;
    }

    private uSkyBlock createPluginMock() {
        uSkyBlock plugin = mock(uSkyBlock.class);
        YmlConfiguration config = new YmlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        OrphanLogic orphanLogic = mock(OrphanLogic.class);
        when(plugin.getOrphanLogic()).thenReturn(orphanLogic);
        WorldManager worldManager = mock(WorldManager.class);
        when(worldManager.isSkyWorld(any(World.class))).thenReturn(true);
        when(plugin.getWorldManager()).thenReturn(worldManager);
        return plugin;
    }
}
