package us.talabrek.ultimateskyblock.integrity;

import dk.lockfuglsang.minecraft.util.TimeUtil;
import org.bukkit.command.CommandSender;
import us.talabrek.ultimateskyblock.async.IncrementalRunnable;
import us.talabrek.ultimateskyblock.handler.WorldGuardHandler;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.Action;
import us.talabrek.ultimateskyblock.island.IslandInfo;
import us.talabrek.ultimateskyblock.player.PlayerInfo;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.util.ProgressTracker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;

import static dk.lockfuglsang.minecraft.po.I18nUtil.marktr;

/** Backs up and then incrementally applies an immutable integrity plan on the server thread. */
public class IntegrityApplyTask extends IncrementalRunnable {
    private enum Phase { PREPARE, VERIFY, BACKUP, ACTIONS, WORLDGUARD, DONE }

    private final uSkyBlock plugin;
    private final IntegrityPlan plan;
    private final IntegrityReport report;
    private final File backupDirectory;
    private final Queue<File> verifyFiles;
    private final List<Action> actions;
    private final Set<File> affectedFiles = new LinkedHashSet<>();
    private List<File> backupFiles;
    private final Queue<String> worldGuardUpdates = new ArrayDeque<>();
    private final Set<String> changedRetainedIslands = new LinkedHashSet<>();
    private final ProgressTracker tracker;
    private int total;
    private final long started = System.currentTimeMillis();
    private Phase phase = Phase.PREPARE;
    private ApplyResult result;
    private int processed;
    private int applied;
    private int prepareIndex;
    private int backupIndex;
    private int actionIndex;
    private boolean mutationsStarted;

    public IntegrityApplyTask(uSkyBlock plugin, CommandSender sender, IntegrityPlan plan, IntegrityReport report,
                              Consumer<ApplyResult> completion) {
        super(plugin);
        this.plugin = plugin;
        this.plan = plan;
        this.report = report;
        this.backupDirectory = new File(new File(plugin.getDataFolder(), "backups"),
                "integrity-" + plan.getId());
        List<File> verification = new ArrayList<>(IntegrityFiles.manifestFiles(plugin.getDataFolder(),
                plugin.directoryPlayers, plugin.directoryIslands));
        this.verifyFiles = new ArrayDeque<>(verification);
        this.actions = plan.getActions();
        this.total = verification.size() + actions.size() * 2;
        this.tracker = new ProgressTracker(sender,
                marktr("\u00a77- INTEGRITY REPAIR: {0,number,##}% ({1}/{2}, applied:{3}) ~ {4}"),
                10, plugin.getConfig().getInt("async.long.feedbackEvery", 30000));
        setOnCompletion(() -> completion.accept(result));
    }

    @Override
    protected boolean execute() {
        try {
            if (phase == Phase.PREPARE && !prepare()) {
                return false;
            }
            if (phase == Phase.VERIFY && !verify()) {
                return false;
            }
            if (phase == Phase.BACKUP && !backup()) {
                return false;
            }
            if (phase == Phase.ACTIONS && !applyActions()) {
                return false;
            }
            if (phase == Phase.WORLDGUARD && !updateWorldGuard()) {
                return false;
            }
            if (phase == Phase.DONE && result == null) {
                report.append("COMPLETE", "repair complete; applied=" + applied
                        + ", backup=" + backupDirectory.getAbsolutePath());
                result = ApplyResult.success(backupDirectory, applied);
            }
        } catch (Exception exception) {
            fail(exception);
        }
        return true;
    }

    private boolean prepare() {
        while (prepareIndex < actions.size()) {
            plan.addAffectedFiles(actions.get(prepareIndex++), plugin.getDataFolder(),
                    plugin.getChallengeLogic().isIslandSharing(), affectedFiles);
            progress();
            if (!tick()) {
                return false;
            }
        }
        backupFiles = new ArrayList<>(affectedFiles);
        total += backupFiles.size();
        phase = Phase.VERIFY;
        return true;
    }

    private boolean verify() throws IOException {
        Collection<File> currentFiles = IntegrityFiles.manifestFiles(plugin.getDataFolder(),
                plugin.directoryPlayers, plugin.directoryIslands);
        if (!plan.getManifest().hasSameFileList(currentFiles)) {
            throw new StalePlanException("integrity data file list changed since scan");
        }
        while (!verifyFiles.isEmpty()) {
            File file = verifyFiles.remove();
            if (!plan.getManifest().matchesFile(file)) {
                throw new StalePlanException("integrity data changed since scan: " + file);
            }
            progress();
            if (!tick()) {
                return false;
            }
        }
        report.append("VERIFIED", "SHA-256 fingerprints and file list still match scan " + plan.getId());
        phase = Phase.BACKUP;
        return true;
    }

    private boolean backup() throws IOException {
        if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
            throw new IOException("Unable to create backup directory " + backupDirectory);
        }
        while (backupIndex < backupFiles.size()) {
            File source = backupFiles.get(backupIndex++);
            Path relative = plugin.getDataFolder().toPath().toAbsolutePath().normalize().relativize(
                    source.toPath().toAbsolutePath().normalize());
            if (relative.startsWith("..")) {
                throw new IOException("Refusing to back up file outside data folder: " + source);
            }
            Path target = backupDirectory.toPath().resolve(relative).normalize();
            Files.createDirectories(target.getParent());
            Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
            progress();
            if (!tick()) {
                return false;
            }
        }
        report.append("BACKUP", "complete: " + backupDirectory.getAbsolutePath());
        phase = Phase.ACTIONS;
        return true;
    }

    private boolean applyActions() throws IOException {
        while (actionIndex < actions.size()) {
            Action action = actions.get(actionIndex++);
            mutationsStarted = true;
            apply(action);
            applied++;
            String message = action.getDescription();
            plugin.getLogger().info("Integrity repair: " + message);
            report.append("APPLIED", message);
            progress();
            if (!tick()) {
                return false;
            }
        }
        worldGuardUpdates.addAll(changedRetainedIslands);
        phase = Phase.WORLDGUARD;
        return true;
    }

    private void apply(Action action) throws IOException {
        switch (action.getType()) {
            case CLEAR_PLAYER_ASSIGNMENT:
                PlayerInfo clearing = requirePlayer(action);
                if (!clearing.getHasIsland() || !action.getIslandId().equals(clearing.locationForParty())) {
                    throw new IllegalStateException("Player assignment changed before repair for "
                            + action.getPlayerId());
                }
                clearing.removeFromIsland();
                clearing.save();
                clearing.saveToFileChecked();
                break;
            case REMOVE_MEMBER_REFERENCE:
                requireIsland(action).repairRemoveMemberReference(action.getMemberKey());
                changedRetainedIslands.add(action.getIslandId());
                break;
            case ADD_MEMBER_REFERENCE:
                PlayerInfo joining = requirePlayer(action);
                if (!joining.getHasIsland() || !action.getIslandId().equals(joining.locationForParty())) {
                    throw new IllegalStateException("Player no longer points to island " + action.getIslandId()
                            + ": " + action.getPlayerId());
                }
                requireIsland(action).repairAddMemberReference(action.getPlayerId(), action.getPlayerName(),
                        action.isLeader());
                changedRetainedIslands.add(action.getIslandId());
                break;
            case SET_LEADER_IDENTITY:
                requireIsland(action).repairSetLeaderIdentity(action.getPlayerId(), action.getPlayerName());
                changedRetainedIslands.add(action.getIslandId());
                break;
            case NORMALIZE_PARTY_SIZE:
                IslandInfo sizing = requireIsland(action);
                if (sizing.getMemberUUIDs().size() != action.getExpectedSize()) {
                    throw new IllegalStateException("Projected party size no longer matches island "
                            + action.getIslandId());
                }
                sizing.repairSetPartySize(action.getExpectedSize());
                changedRetainedIslands.add(action.getIslandId());
                break;
            case REMOVE_TRUST_REFERENCE:
                PlayerInfo trusted = requirePlayer(action);
                trusted.removeTrust(action.getIslandId());
                trusted.saveToFileChecked();
                break;
            case REMOVE_BAN_REFERENCE:
                PlayerInfo banned = requirePlayer(action);
                banned.unbanFromIsland(action.getIslandId());
                banned.saveToFileChecked();
                break;
            case RELEASE_ISLAND:
                plugin.getIslandLogic().releaseIslandForIntegrity(action.getIslandId());
                changedRetainedIslands.remove(action.getIslandId());
                break;
            default:
                throw new IllegalStateException("Unsupported integrity action " + action.getType());
        }
    }

    private boolean updateWorldGuard() throws IOException {
        while (!worldGuardUpdates.isEmpty()) {
            String islandId = worldGuardUpdates.remove();
            IslandInfo island = plugin.getIslandLogic().getIslandInfoForMaintenance(islandId);
            if (island == null || !WorldGuardHandler.updateRegionChecked(island)) {
                throw new IllegalStateException("Unable to update WorldGuard for island " + islandId);
            }
            island.repairPersist();
            String message = "updated WorldGuard regions for retained island " + islandId;
            plugin.getLogger().info("Integrity repair: " + message);
            report.append("APPLIED", message);
            applied++;
            if (!tick()) {
                return false;
            }
        }
        phase = Phase.DONE;
        return true;
    }

    private PlayerInfo requirePlayer(Action action) {
        PlayerInfo player = plugin.getPlayerLogic().getPlayerInfoForMaintenance(action.getPlayerId());
        if (player == null) {
            throw new IllegalStateException("Unable to load player " + action.getPlayerId());
        }
        return player;
    }

    private IslandInfo requireIsland(Action action) {
        IslandInfo island = plugin.getIslandLogic().getIslandInfoForMaintenance(action.getIslandId());
        if (island == null || !island.exists() || island.ignore()) {
            throw new IllegalStateException("Unable or unsafe to load island " + action.getIslandId());
        }
        return island;
    }

    private void progress() {
        processed++;
        tracker.progressUpdate(processed, total, applied,
                TimeUtil.millisAsString(System.currentTimeMillis() - started));
    }

    private void fail(Exception exception) {
        boolean stale = exception instanceof StalePlanException;
        String message;
        if (stale) {
            message = "repair plan is stale: " + exception.getMessage() + "; run a new scan";
        } else if (!mutationsStarted) {
            message = "repair did not start because verification or backup failed: " + exception.getMessage()
                    + "; no data was changed";
        } else {
            message = "repair stopped after " + applied + " applied changes: " + exception.getMessage()
                    + "; restore from " + backupDirectory.getAbsolutePath();
        }
        plugin.getLogger().log(stale ? Level.WARNING : Level.SEVERE, "Integrity " + message, exception);
        try {
            report.append("FAILED", message);
        } catch (IOException reportError) {
            plugin.getLogger().log(Level.SEVERE, "Unable to append integrity failure to report", reportError);
        }
        result = ApplyResult.failure(backupDirectory, applied, message, stale, mutationsStarted);
        phase = Phase.DONE;
    }

    public static class ApplyResult {
        private final boolean success;
        private final File backupDirectory;
        private final int applied;
        private final String error;
        private final boolean stale;
        private final boolean mutationsStarted;

        private ApplyResult(boolean success, File backupDirectory, int applied, String error, boolean stale,
                            boolean mutationsStarted) {
            this.success = success;
            this.backupDirectory = backupDirectory;
            this.applied = applied;
            this.error = error;
            this.stale = stale;
            this.mutationsStarted = mutationsStarted;
        }

        public static ApplyResult success(File backupDirectory, int applied) {
            return new ApplyResult(true, backupDirectory, applied, null, false, applied > 0);
        }

        public static ApplyResult failure(File backupDirectory, int applied, String error, boolean stale,
                                          boolean mutationsStarted) {
            return new ApplyResult(false, backupDirectory, applied, error, stale, mutationsStarted);
        }

        public boolean isSuccess() {
            return success;
        }

        public File getBackupDirectory() {
            return backupDirectory;
        }

        public int getApplied() {
            return applied;
        }

        public String getError() {
            return error;
        }

        public boolean isStale() {
            return stale;
        }

        public boolean isMutationsStarted() {
            return mutationsStarted;
        }
    }

    private static class StalePlanException extends IOException {
        private StalePlanException(String message) {
            super(message);
        }
    }
}
