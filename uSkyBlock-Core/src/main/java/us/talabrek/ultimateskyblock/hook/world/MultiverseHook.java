package us.talabrek.ultimateskyblock.hook.world;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.jetbrains.annotations.NotNull;
import org.mvplugins.multiverse.core.MultiverseCore;
import org.mvplugins.multiverse.core.MultiverseCoreApi;
import org.mvplugins.multiverse.core.world.options.ImportWorldOptions;
import org.mvplugins.multiverse.inventories.MultiverseInventoriesApi;
import org.mvplugins.multiverse.inventories.profile.group.WorldGroup;
import org.mvplugins.multiverse.inventories.profile.group.WorldGroupManager;
import org.mvplugins.multiverse.inventories.share.Sharables;
import us.talabrek.ultimateskyblock.Settings;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.hook.PluginHook;
import us.talabrek.ultimateskyblock.util.LocationUtil;

public class MultiverseHook extends PluginHook {
    private MultiverseCoreApi mvCore;
    private MultiverseInventoriesApi mvInventories;

    private static final String GENERATOR_NAME = "uSkyBlock";

    public MultiverseHook(@NotNull uSkyBlock plugin) {
        super(plugin, "Multiverse", "Multiverse");

        if (plugin.getServer().getPluginManager().isPluginEnabled("Multiverse-Core")) {
            this.mvCore = MultiverseCoreApi.get();
        }
        if (plugin.getServer().getPluginManager().isPluginEnabled("Multiverse-Inventories")) {
           this.mvInventories = MultiverseInventoriesApi.get();
        }
    }

    /**
     * Registers the given {@link World} to {@link MultiverseCore} as the skyblock overworld (skyworld).
     * @param world World to register.
     */
    public void registerOverworld(@NotNull World world) {
        if (mvCore == null) {
            return;
        }

        if (!mvCore.getWorldManager().isLoadedWorld(world)) {
            ImportWorldOptions options = ImportWorldOptions.worldName(world.getName()).environment(Environment.NORMAL).generator(GENERATOR_NAME);
            mvCore.getWorldManager().importWorld(options);
        }

        mvCore.getWorldManager().getLoadedWorld(world).peek(mvWorld -> {
            mvWorld.setScale(1.0);

            if (Settings.general_spawnSize > 0 && LocationUtil.isEmptyLocation(mvWorld.getSpawnLocation())) {
                Location spawn = LocationUtil.centerOnBlock(
                        new Location(world, 0.5, Settings.island_height + 0.1, 0.5));
                mvWorld.setAdjustSpawn(false);
                mvWorld.setSpawnLocation(spawn);
                world.setSpawnLocation(spawn);
            }

            if (!Settings.extras_sendToSpawn) {
                mvWorld.setRespawnWorld(mvWorld.getName());
            }
        });
    }

    /**
     * Registers the given {@link World} to {@link MultiverseCore} as the skyblock nether world (skyworld_nether).
     * @param world World to register.
     */
    public void registerNetherworld(@NotNull World world) {
        if (mvCore == null) {
            return;
        }

        if (!mvCore.getWorldManager().isLoadedWorld(world)) {
            ImportWorldOptions options = ImportWorldOptions.worldName(world.getName()).environment(Environment.NETHER).generator(GENERATOR_NAME);
            mvCore.getWorldManager().importWorld(options);
        }

        mvCore.getWorldManager().getLoadedWorld(world).peek(mvWorld -> {
            mvWorld.setScale(1.0);
            if (Settings.general_spawnSize > 0 && LocationUtil.isEmptyLocation(mvWorld.getSpawnLocation())) {
                Location spawn = LocationUtil.centerOnBlock(
                        new Location(world, 0.5, Settings.island_height / 2.0 + 0.1, 0.5));
                mvWorld.setAdjustSpawn(false);
                mvWorld.setSpawnLocation(spawn);
                world.setSpawnLocation(spawn);
            }

            if (!Settings.extras_sendToSpawn) {
                mvWorld.setRespawnWorld(plugin.getWorldManager().getWorld().getName());
            }
        });
        linkNetherInventory(plugin.getWorldManager().getWorld(), world);
    }

    private void linkNetherInventory(@NotNull World... worlds) {
        if (mvCore == null || mvInventories == null) {
            return;
        }

        WorldGroupManager groupManager = mvInventories.getWorldGroupManager();
        WorldGroup worldGroup = groupManager.getGroup("skyblock");
        if (worldGroup == null) {
            worldGroup = groupManager.newEmptyGroup("skyblock");
            worldGroup.getShares().addAll(Sharables.ALL_DEFAULT);
        }
        for (World world : worlds) {
            worldGroup.addWorld(world);
        }
        groupManager.updateGroup(worldGroup);
    }
}
