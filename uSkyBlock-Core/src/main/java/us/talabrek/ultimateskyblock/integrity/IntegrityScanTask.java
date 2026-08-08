package us.talabrek.ultimateskyblock.integrity;

import dk.lockfuglsang.minecraft.util.TimeUtil;
import org.bukkit.command.CommandSender;
import us.talabrek.ultimateskyblock.async.IncrementalRunnable;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.Action;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.IslandRecord;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.Issue;
import us.talabrek.ultimateskyblock.integrity.IntegrityPlan.PlayerRecord;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.util.ProgressTracker;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Consumer;

import static dk.lockfuglsang.minecraft.po.I18nUtil.marktr;

/** Incrementally reads every player and island file and produces an immutable plan. */
public class IntegrityScanTask extends IncrementalRunnable {
    private final uSkyBlock plugin;
    private final String id;
    private final IntegrityReport report;
    private final IntegritySnapshotReader reader;
    private final Queue<File> playerFiles;
    private final Queue<File> islandFiles;
    private final Queue<File> manifestCaptureFiles;
    private final Queue<File> manifestVerifyFiles;
    private final Map<String, String> manifestHashes = new LinkedHashMap<>();
    private final Map<UUID, PlayerRecord> players = new LinkedHashMap<>();
    private final Map<String, IslandRecord> islands = new LinkedHashMap<>();
    private final List<String> fatalErrors = new ArrayList<>();
    private final ProgressTracker tracker;
    private int total;
    private final long started = System.currentTimeMillis();
    private int processed;
    private boolean finalized;
    private boolean manifestCaptured;
    private boolean manifestFileListChecked;
    private IntegrityManifest manifest;
    private IntegrityPlanner.Session planningSession;
    private IntegrityPlanner.Result planned;
    private Queue<Issue> issuesToReport;
    private Queue<Action> actionsToReport;
    private ScanResult result;

    public IntegrityScanTask(uSkyBlock plugin, CommandSender sender, String id, IntegrityReport report,
                             Consumer<ScanResult> completion) {
        super(plugin);
        this.plugin = plugin;
        this.id = id;
        this.report = report;
        this.reader = new IntegritySnapshotReader(plugin);
        List<File> playersAtStart = IntegrityFiles.listPlayers(plugin.directoryPlayers);
        List<File> islandsAtStart = IntegrityFiles.listIslands(plugin.directoryIslands);
        List<File> manifestAtStart = new ArrayList<>(IntegrityFiles.manifestFiles(plugin.getDataFolder(),
                playersAtStart, islandsAtStart));
        this.playerFiles = new ArrayDeque<>(playersAtStart);
        this.islandFiles = new ArrayDeque<>(islandsAtStart);
        this.manifestCaptureFiles = new ArrayDeque<>(manifestAtStart);
        this.manifestVerifyFiles = new ArrayDeque<>(manifestAtStart);
        this.total = playersAtStart.size() * 3 + islandsAtStart.size() * 4
                + manifestAtStart.size() * 2 + 5;
        this.tracker = new ProgressTracker(sender,
                marktr("\u00a77- INTEGRITY SCAN: {0,number,##}% ({1}/{2}, errors:{3}) ~ {4}"),
                10, plugin.getConfig().getInt("async.long.feedbackEvery", 30000));
        setOnCompletion(() -> completion.accept(result));
    }

    @Override
    protected boolean execute() {
        while (!manifestCaptureFiles.isEmpty()) {
            File file = manifestCaptureFiles.remove();
            try {
                manifestHashes.put(IntegrityManifest.path(plugin.getDataFolder(), file),
                        IntegrityManifest.hash(file));
            } catch (Exception exception) {
                fail("Unable to fingerprint " + file + " before scan: " + exception.getMessage());
            }
            progress();
            if (!tick()) {
                return false;
            }
        }
        if (!manifestCaptured) {
            manifest = IntegrityManifest.fromHashes(plugin.getDataFolder(), manifestHashes);
            manifestCaptured = true;
        }
        while (!playerFiles.isEmpty()) {
            File file = playerFiles.remove();
            try {
                PlayerRecord player = reader.readPlayer(file);
                PlayerRecord duplicate = players.putIfAbsent(player.getUuid(), player);
                if (duplicate != null) {
                    fail("Duplicate player UUID " + player.getUuid() + " in " + duplicate.getFile() + " and " + file);
                }
            } catch (Exception exception) {
                fail("Unable to read player file " + file + ": " + exception.getMessage());
            }
            progress();
            if (!tick()) {
                return false;
            }
        }
        while (!islandFiles.isEmpty()) {
            File file = islandFiles.remove();
            try {
                IslandRecord island = reader.readIsland(file);
                islands.put(island.getId(), island);
            } catch (Exception exception) {
                fail("Unable to read island file " + file + ": " + exception.getMessage());
            }
            progress();
            if (!tick()) {
                return false;
            }
        }
        if (!manifestFileListChecked) {
            Collection<File> currentFiles = IntegrityFiles.manifestFiles(plugin.getDataFolder(),
                    plugin.directoryPlayers, plugin.directoryIslands);
            if (!manifest.hasSameFileList(currentFiles)) {
                fail("Data file list changed while the integrity scan was running");
            }
            manifestFileListChecked = true;
        }
        while (!manifestVerifyFiles.isEmpty()) {
            File file = manifestVerifyFiles.remove();
            try {
                if (!manifest.matchesFile(file)) {
                    fail("Data file changed while the integrity scan was running: " + file);
                }
            } catch (Exception exception) {
                fail("Unable to verify " + file + " after scan: " + exception.getMessage());
            }
            progress();
            if (!tick()) {
                return false;
            }
        }
        if (!fatalErrors.isEmpty()) {
            finalizeScan();
            finalized = true;
            return true;
        }
        if (planningSession == null) {
            planningSession = new IntegrityPlanner.Session(players, islands);
        }
        while (planned == null) {
            if (planningSession.step()) {
                planned = planningSession.getResult();
                issuesToReport = new ArrayDeque<>(planned.getIssues());
                actionsToReport = new ArrayDeque<>(planned.getActions());
                total += issuesToReport.size() + actionsToReport.size();
            }
            progress();
            if (!tick()) {
                return false;
            }
        }
        while (!issuesToReport.isEmpty()) {
            Issue issue = issuesToReport.remove();
            if (!append(issue.isIgnored() ? "IGNORED" : "ISSUE", issue.getMessage())) {
                finalizeScan();
                finalized = true;
                return true;
            }
            progress();
            if (!tick()) {
                return false;
            }
        }
        while (!actionsToReport.isEmpty()) {
            Action action = actionsToReport.remove();
            if (!append("PLANNED", action.getDescription())) {
                finalizeScan();
                finalized = true;
                return true;
            }
            progress();
            if (!tick()) {
                return false;
            }
        }
        if (!finalized) {
            finalizeScan();
            finalized = true;
        }
        return true;
    }

    private void finalizeScan() {
        try {
            if (!fatalErrors.isEmpty()) {
                report.append("FAILED", "scan incomplete; fatal errors=" + fatalErrors.size());
                result = new ScanResult(null, fatalErrors);
                return;
            }
            report.append("SUMMARY", "players=" + players.size() + ", islands=" + islands.size()
                    + ", issues=" + planned.getIssues().size() + ", actions=" + planned.getActions().size());
            IntegrityPlan plan = new IntegrityPlan(id, players, islands, planned.getIssues(), planned.getActions(),
                    manifest, report.getFile());
            result = new ScanResult(plan, fatalErrors);
        } catch (Exception exception) {
            fail("Unable to finalize integrity scan: " + exception.getMessage());
            result = new ScanResult(null, fatalErrors);
        }
    }

    private void progress() {
        processed++;
        tracker.progressUpdate(processed, total, fatalErrors.size(),
                TimeUtil.millisAsString(System.currentTimeMillis() - started));
    }

    private boolean append(String type, String message) {
        try {
            report.append(type, message);
            return true;
        } catch (IOException exception) {
            fail("Unable to append " + type + " to integrity report: " + exception.getMessage());
            return false;
        }
    }

    private void fail(String message) {
        fatalErrors.add(message);
        plugin.getLogger().warning("Integrity scan: " + message);
        try {
            report.append("ERROR", message);
        } catch (IOException ignored) {
            // The original report error remains represented by the failed result.
        }
    }

    public static class ScanResult {
        private final IntegrityPlan plan;
        private final List<String> fatalErrors;

        private ScanResult(IntegrityPlan plan, List<String> fatalErrors) {
            this.plan = plan;
            this.fatalErrors = new ArrayList<>(fatalErrors);
        }

        public IntegrityPlan getPlan() {
            return plan;
        }

        public List<String> getFatalErrors() {
            return fatalErrors;
        }
    }
}
