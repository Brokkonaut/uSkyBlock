package us.talabrek.ultimateskyblock.island;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import us.talabrek.ultimateskyblock.Settings;
import us.talabrek.ultimateskyblock.handler.WorldEditHandler;
import us.talabrek.ultimateskyblock.handler.WorldGuardHandler;
import us.talabrek.ultimateskyblock.handler.task.IslandRegionCopyTask;
import us.talabrek.ultimateskyblock.player.PlayerInfo;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.util.IslandUtil;
import us.talabrek.ultimateskyblock.util.LocationUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Coordinates the destructive, multi-step relocation used by /usb island register. */
public class IslandRelocationLogic {
    private final uSkyBlock plugin;
    private final Set<String> movingIslands = new HashSet<>();

    public IslandRelocationLogic(uSkyBlock plugin) {
        this.plugin = plugin;
    }

    public boolean relocate(Player sender, Location requestedTarget, String playerName,
                            Consumer<Result> completion) {
        PlayerInfo playerInfo = plugin.getPlayerInfo(playerName);
        if (playerInfo == null) {
            complete(completion, Status.PLAYER_NOT_FOUND, null);
            return false;
        }
        if (requestedTarget == null || requestedTarget.getWorld() == null
                || !plugin.getWorldManager().isSkyWorld(requestedTarget.getWorld())) {
            complete(completion, Status.NOT_IN_SKYWORLD, null);
            return false;
        }

        String destinationName = WorldGuardHandler.getIslandNameAt(requestedTarget);
        Location destination;
        if (destinationName != null) {
            destination = IslandUtil.getIslandLocation(destinationName);
        } else {
            destination = LocationUtil.alignToDistance(requestedTarget.clone(), Settings.island_distance);
            destinationName = LocationUtil.getIslandName(destination);
            if (plugin.getIslandLocatorLogic().isReserved(destination)) {
                complete(completion, Status.BUSY, null);
                return false;
            }
        }
        if (destination == null || plugin.islandInSpawn(destination)) {
            complete(completion, Status.SPAWN_INTERSECTION, null);
            return false;
        }

        IslandInfo destinationInfo = plugin.getIslandLogic().getIslandInfo(destinationName);
        if (destinationInfo.exists() && destinationInfo.ignore()) {
            complete(completion, Status.IGNORED_ISLAND, null);
            return false;
        }
        if (isMoving(destinationName)) {
            complete(completion, Status.BUSY, null);
            return false;
        }

        if (!playerInfo.getHasIsland()) {
            return registerExistingIsland(playerInfo, destinationName, destination, completion);
        }

        Location source = playerInfo.getIslandLocation();
        if (source == null || source.getWorld() == null || !plugin.getWorldManager().isSkyWorld(source.getWorld())
                || plugin.islandInSpawn(source)) {
            complete(completion, Status.INVALID_SOURCE, null);
            return false;
        }
        String sourceName = playerInfo.locationForParty();
        if (sourceName.equals(destinationName)) {
            complete(completion, Status.ALREADY_ASSIGNED, null);
            return false;
        }
        IslandInfo sourceInfo = plugin.getIslandLogic().getIslandInfo(sourceName);
        if (sourceInfo == null || !sourceInfo.exists()) {
            complete(completion, Status.INVALID_SOURCE, null);
            return false;
        }
        if (sourceInfo.ignore()) {
            complete(completion, Status.IGNORED_ISLAND, null);
            return false;
        }
        if (!sourceInfo.isLeader(Bukkit.getOfflinePlayer(playerInfo.getUniqueId()))) {
            complete(completion, Status.NOT_LEADER, null);
            return false;
        }
        if (!reserve(sourceName, destinationName)) {
            complete(completion, Status.BUSY, null);
            return false;
        }

        String lockId = createLockId(sourceName, destinationName);
        if (!WorldGuardHandler.addMoveLock(lockId, source, destination)) {
            release(sourceName, destinationName);
            complete(completion, Status.LOCK_FAILED, null);
            return false;
        }

        MoveContext context = new MoveContext(sourceName, destinationName, source, destination,
                sourceInfo, lockId, completion);
        plugin.getOrphanLogic().removeOrphan(destinationName);
        try {
            evacuateAndPrepare(context);
            startWorldCopy(context);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to start island relocation", exception);
            finish(context, Status.COPY_FAILED, exception);
            return false;
        }
        return true;
    }

    private boolean registerExistingIsland(PlayerInfo playerInfo, String destinationName, Location destination,
                                           Consumer<Result> completion) {
        try {
            if (!plugin.getIslandLogic().purgeForReplacement(destinationName)) {
                complete(completion, Status.CHALLENGE_FAILED, null);
                return false;
            }
            playerInfo.setHomeLocation(null);
            playerInfo.setIslandLocation(destination);
            playerInfo.setHomeLocation(plugin.getSafeHomeLocation(playerInfo));
            IslandInfo island = plugin.getIslandLogic().createIslandInfo(destinationName, playerInfo.getPlayerName());
            if (!WorldGuardHandler.updateRegionChecked(island)) {
                complete(completion, Status.LOCK_FAILED, null);
                return false;
            }
            plugin.getOrphanLogic().removeOrphan(destinationName);
            playerInfo.saveToFile();
            complete(completion, Status.REGISTERED, null);
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to register island at " + destinationName, exception);
            complete(completion, Status.METADATA_FAILED, exception);
            return false;
        }
    }

    private void evacuateAndPrepare(MoveContext context) {
        ProtectedRegion sourceOverworld = WorldGuardHandler.getIslandRegion(context.source);
        ProtectedRegion destinationOverworld = WorldGuardHandler.getIslandRegion(context.destination);
        evacuate(context.source.getWorld(), sourceOverworld, false);
        evacuate(context.destination.getWorld(), destinationOverworld, true);

        World nether = plugin.getWorldManager().getNetherWorld();
        if (nether != null) {
            ProtectedRegion sourceNether = WorldGuardHandler.getNetherIslandRegion(context.source);
            ProtectedRegion destinationNether = WorldGuardHandler.getNetherIslandRegion(context.destination);
            evacuate(nether, sourceNether, false);
            evacuate(nether, destinationNether, true);
        }
    }

    private void evacuate(World world, ProtectedRegion region, boolean removeNonPlayers) {
        for (Entity entity : WorldGuardHandler.getEntitiesInRegion(world, region)) {
            if (entity instanceof Player) {
                plugin.getTeleportLogic().spawnTeleport((Player) entity, true);
            } else if (removeNonPlayers) {
                entity.remove();
            }
        }
    }

    private void startWorldCopy(MoveContext context) {
        Region overworld = WorldEditHandler.getRegion(context.source.getWorld(),
                WorldGuardHandler.getIslandRegion(context.source));
        Region nether = plugin.getWorldManager().getNetherWorld() != null
                ? WorldEditHandler.getRegion(plugin.getWorldManager().getNetherWorld(),
                    WorldGuardHandler.getNetherIslandRegion(context.source)) : null;
        IslandRegionCopyTask[] task = new IslandRegionCopyTask[1];
        task[0] = new IslandRegionCopyTask(plugin, context.source, context.destination, overworld, nether,
                () -> onWorldCopied(context, task[0]));
        task[0].runTask(plugin);
    }

    private void onWorldCopied(MoveContext context, IslandRegionCopyTask task) {
        if (task.getFailure() != null) {
            plugin.getLogger().log(Level.SEVERE, "Unable to copy island " + context.sourceName
                    + " to " + context.destinationName, task.getFailure());
            finish(context, Status.COPY_FAILED, task.getFailure());
            return;
        }
        try {
            if (!plugin.getIslandLogic().purgeForReplacement(context.destinationName)) {
                finish(context, Status.CHALLENGE_FAILED, null);
                return;
            }
            if (!plugin.getChallengeLogic().copyIslandChallenges(context.sourceName, context.destinationName)) {
                finish(context, Status.CHALLENGE_FAILED, null);
                return;
            }
            IslandInfo destinationInfo = plugin.getIslandLogic().relocateIslandConfig(context.sourceInfo,
                    context.destinationName, context.source, context.destination);
            if (!WorldGuardHandler.updateRegionChecked(destinationInfo)) {
                finish(context, Status.LOCK_FAILED, null);
                return;
            }
            plugin.getOrphanLogic().removeOrphan(context.destinationName);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to prepare relocated island metadata", exception);
            finish(context, Status.METADATA_FAILED, exception);
            return;
        }

        removeSourceEntities(context);
        ProtectedRegion sourceOverworld = WorldGuardHandler.getIslandRegion(context.source);
        WorldEditHandler.clearIsland(context.source.getWorld(), sourceOverworld, failure -> {
            if (failure == null) {
                clearSourceNether(context);
            } else {
                plugin.getLogger().log(Level.SEVERE, "Unable to clear relocated overworld island", failure);
                finish(context, Status.CLEANUP_FAILED, failure);
            }
        });
    }

    private void clearSourceNether(MoveContext context) {
        World nether = plugin.getWorldManager().getNetherWorld();
        if (nether == null) {
            finalizeMove(context);
            return;
        }
        WorldEditHandler.clearIsland(nether, WorldGuardHandler.getNetherIslandRegion(context.source), failure -> {
            if (failure == null) {
                finalizeMove(context);
            } else {
                plugin.getLogger().log(Level.SEVERE, "Unable to clear relocated nether island", failure);
                finish(context, Status.CLEANUP_FAILED, failure);
            }
        });
    }

    private void removeSourceEntities(MoveContext context) {
        removeNonPlayers(context.source.getWorld(), WorldGuardHandler.getIslandRegion(context.source));
        World nether = plugin.getWorldManager().getNetherWorld();
        if (nether != null) {
            removeNonPlayers(nether, WorldGuardHandler.getNetherIslandRegion(context.source));
        }
    }

    private void removeNonPlayers(World world, ProtectedRegion region) {
        for (Entity entity : WorldGuardHandler.getEntitiesInRegion(world, region)) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
    }

    private void finalizeMove(MoveContext context) {
        try {
            if (!plugin.getChallengeLogic().deleteIslandChallenges(context.sourceName)) {
                finish(context, Status.CLEANUP_FAILED, null);
                return;
            }
            WorldGuardHandler.removeIslandRegion(context.sourceName);
            plugin.getIslandLogic().deleteIslandConfig(context.sourceName, true);
            plugin.getOrphanLogic().save();
            finish(context, Status.COMPLETED, null);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to release relocated island " + context.sourceName,
                    exception);
            finish(context, Status.CLEANUP_FAILED, exception);
        }
    }

    private synchronized boolean reserve(String sourceName, String destinationName) {
        if (movingIslands.contains(sourceName) || movingIslands.contains(destinationName)) {
            return false;
        }
        if (!plugin.getIslandLocatorLogic().reserveForMove(sourceName, destinationName)) {
            return false;
        }
        movingIslands.add(sourceName);
        movingIslands.add(destinationName);
        return true;
    }

    private synchronized void release(String sourceName, String destinationName) {
        movingIslands.remove(sourceName);
        movingIslands.remove(destinationName);
        plugin.getIslandLocatorLogic().releaseMoveReservation(sourceName, destinationName);
    }

    private synchronized boolean isMoving(String islandName) {
        return movingIslands.contains(islandName);
    }

    private String createLockId(String sourceName, String destinationName) {
        return "usb-move-" + sourceName.replace(',', '-') + "-to-" + destinationName.replace(',', '-');
    }

    private void finish(MoveContext context, Status status, Throwable failure) {
        Status finalStatus = status;
        Throwable finalFailure = failure;
        try {
            WorldGuardHandler.removeMoveLock(context.lockId);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to remove island relocation lock " + context.lockId,
                    exception);
            if (finalFailure != null) {
                finalFailure.addSuppressed(exception);
            } else {
                finalFailure = exception;
            }
            if (finalStatus == Status.COMPLETED) {
                finalStatus = Status.CLEANUP_FAILED;
            }
        } finally {
            release(context.sourceName, context.destinationName);
        }
        complete(context.completion, finalStatus, finalFailure);
    }

    private void complete(Consumer<Result> completion, Status status, Throwable failure) {
        if (completion != null) {
            completion.accept(new Result(status, failure));
        }
    }

    public enum Status {
        COMPLETED,
        REGISTERED,
        PLAYER_NOT_FOUND,
        NOT_IN_SKYWORLD,
        INVALID_SOURCE,
        SPAWN_INTERSECTION,
        ALREADY_ASSIGNED,
        NOT_LEADER,
        IGNORED_ISLAND,
        BUSY,
        LOCK_FAILED,
        COPY_FAILED,
        CHALLENGE_FAILED,
        METADATA_FAILED,
        CLEANUP_FAILED
    }

    public static class Result {
        private final Status status;
        private final Throwable failure;

        private Result(Status status, Throwable failure) {
            this.status = status;
            this.failure = failure;
        }

        public Status getStatus() {
            return status;
        }

        public Throwable getFailure() {
            return failure;
        }
    }

    private static class MoveContext {
        private final String sourceName;
        private final String destinationName;
        private final Location source;
        private final Location destination;
        private final IslandInfo sourceInfo;
        private final String lockId;
        private final Consumer<Result> completion;

        private MoveContext(String sourceName, String destinationName, Location source, Location destination,
                            IslandInfo sourceInfo, String lockId, Consumer<Result> completion) {
            this.sourceName = sourceName;
            this.destinationName = destinationName;
            this.source = source.clone();
            this.destination = destination.clone();
            this.sourceInfo = sourceInfo;
            this.lockId = lockId;
            this.completion = completion;
        }
    }
}
