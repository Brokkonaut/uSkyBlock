package us.talabrek.ultimateskyblock.integrity;

import org.bukkit.command.CommandSender;
import us.talabrek.ultimateskyblock.async.JobManager;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;

import static dk.lockfuglsang.minecraft.po.I18nUtil.tr;

/** Coordinates the single active scan/confirm lifecycle. */
public class IslandDataIntegrityLogic {
    public enum State { IDLE, SCANNING, READY, APPLYING, COMPLETE, FAILED }

    private final uSkyBlock plugin;
    private State state = State.IDLE;
    private IntegrityPlan plan;
    private IntegrityReport report;
    private IntegrityScanTask scanTask;
    private String lastError;
    private File backupDirectory;

    public IslandDataIntegrityLogic(uSkyBlock plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean scan(CommandSender sender) {
        if (!checkMaintenance(sender) || !checkAvailable(sender)) {
            return true;
        }
        if (state == State.READY) {
            sender.sendMessage(tr("\u00a7eAn integrity plan is waiting for confirm or cancel."));
            return true;
        }
        if (hasRunningJobs()) {
            sender.sendMessage(tr("\u00a7cCannot scan while another uSkyBlock job is running."));
            return true;
        }

        String id = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
        report = null;
        try {
            report = new IntegrityReport(plugin.getDataFolder(), id);
            report.append("START", "read-only global integrity scan requested by " + sender.getName());
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to create integrity report", exception);
            sender.sendMessage(tr("\u00a7cUnable to create the integrity report; scan was not started."));
            state = State.FAILED;
            lastError = exception.getMessage();
            return true;
        }

        plan = null;
        backupDirectory = null;
        lastError = null;
        state = State.SCANNING;
        scanTask = new IntegrityScanTask(plugin, sender, id, report, result -> finishScan(sender, result));
        scanTask.runTask(plugin);
        sender.sendMessage(tr("\u00a7eIntegrity scan started. No data will be changed."));
        return true;
    }

    public synchronized boolean confirm(CommandSender sender) {
        if (!checkMaintenance(sender) || !checkAvailable(sender)) {
            return true;
        }
        if (state != State.READY || plan == null || report == null) {
            sender.sendMessage(tr("\u00a7cThere is no confirmed-ready integrity plan. Run /usb integrity scan first."));
            return true;
        }
        if (hasRunningJobs()) {
            sender.sendMessage(tr("\u00a7cCannot repair while another uSkyBlock job is running."));
            return true;
        }

        state = State.APPLYING;
        IntegrityApplyTask applyTask = new IntegrityApplyTask(plugin, sender, plan, report,
                result -> finishApply(sender, result));
        applyTask.runTask(plugin);
        sender.sendMessage(tr("\u00a7eIntegrity plan accepted. Fingerprints are being verified before backup and repair."));
        return true;
    }

    public synchronized boolean cancel(CommandSender sender) {
        if (state == State.APPLYING) {
            sender.sendMessage(tr("\u00a7cAn integrity repair cannot be cancelled after confirm."));
            return true;
        }
        if (state == State.SCANNING && scanTask != null) {
            scanTask.cancel();
        }
        if (state == State.SCANNING || state == State.READY) {
            appendReport("CANCELLED", "integrity run cancelled by " + sender.getName());
            state = State.IDLE;
            plan = null;
            scanTask = null;
            sender.sendMessage(tr("\u00a7eIntegrity scan/plan cancelled. No repair was applied."));
        } else {
            sender.sendMessage(tr("\u00a77There is no pending integrity scan or plan."));
        }
        return true;
    }

    public synchronized boolean status(CommandSender sender) {
        switch (state) {
            case SCANNING:
                sender.sendMessage(tr("\u00a7eIntegrity status: scanning files (read-only)."));
                break;
            case READY:
                sender.sendMessage(tr("\u00a7eIntegrity status: ready; {0} issues, {1} planned actions. Report: {2}",
                        plan.getIssues().size(), plan.getActions().size(), plan.getReportFile().getAbsolutePath()));
                break;
            case APPLYING:
                sender.sendMessage(tr("\u00a7eIntegrity status: verifying, backing up, or applying; cancellation is disabled."));
                break;
            case COMPLETE:
                sender.sendMessage(tr("\u00a7aIntegrity status: complete. Backup: {0}; report: {1}",
                        backupDirectory != null ? backupDirectory.getAbsolutePath() : "none",
                        report != null ? report.getFile().getAbsolutePath() : "none"));
                break;
            case FAILED:
                sender.sendMessage(tr("\u00a7cIntegrity status: failed: {0}. Report: {1}", lastError,
                        report != null ? report.getFile().getAbsolutePath() : "none"));
                break;
            default:
                sender.sendMessage(tr("\u00a77Integrity status: idle."));
        }
        return true;
    }

    private synchronized void finishScan(CommandSender sender, IntegrityScanTask.ScanResult result) {
        scanTask = null;
        if (state != State.SCANNING) {
            return;
        }
        if (result == null || result.getPlan() == null) {
            state = State.FAILED;
            int errors = result != null ? result.getFatalErrors().size() : 1;
            lastError = "scan incomplete; fatal errors=" + errors;
            plugin.getLogger().warning("Integrity " + lastError + "; no confirmable plan was created");
            sender.sendMessage(tr("\u00a7cIntegrity scan incomplete ({0} fatal errors). No repair plan is available. Report: {1}",
                    errors, report.getFile().getAbsolutePath()));
            return;
        }
        plan = result.getPlan();
        state = State.READY;
        plugin.getLogger().info("Integrity scan " + plan.getId() + " found " + plan.getIssues().size()
                + " issues and planned " + plan.getActions().size() + " actions");
        sender.sendMessage(tr("\u00a7aIntegrity scan complete: {0} issues, {1} planned actions. Report: {2}",
                plan.getIssues().size(), plan.getActions().size(), plan.getReportFile().getAbsolutePath()));
        sender.sendMessage(tr("\u00a7eReview the report, then use /usb integrity confirm or cancel."));
    }

    private synchronized void finishApply(CommandSender sender, IntegrityApplyTask.ApplyResult result) {
        if (state != State.APPLYING) {
            return;
        }
        plan = null;
        if (result != null && result.isSuccess()) {
            state = State.COMPLETE;
            backupDirectory = result.getBackupDirectory();
            plugin.getLogger().info("Integrity repair completed with " + result.getApplied()
                    + " logged changes; backup=" + backupDirectory);
            sender.sendMessage(tr("\u00a7aIntegrity repair complete: {0} logged changes. Backup: {1}; report: {2}",
                    result.getApplied(), backupDirectory.getAbsolutePath(), report.getFile().getAbsolutePath()));
            return;
        }
        state = State.FAILED;
        lastError = result != null ? result.getError() : "repair task returned no result";
        backupDirectory = result != null ? result.getBackupDirectory() : null;
        if (result != null && result.isStale()) {
            sender.sendMessage(tr("\u00a7cIntegrity plan was discarded because files changed. Run a new scan. Report: {0}",
                    report.getFile().getAbsolutePath()));
        } else if (result != null && !result.isMutationsStarted()) {
            sender.sendMessage(tr("\u00a7cIntegrity repair did not start; no data was changed. Report: {0}",
                    report.getFile().getAbsolutePath()));
        } else {
            sender.sendMessage(tr("\u00a7cIntegrity repair stopped after an error. Restore from {0}. Report: {1}",
                    backupDirectory != null ? backupDirectory.getAbsolutePath() : "unavailable",
                    report.getFile().getAbsolutePath()));
        }
    }

    private boolean checkMaintenance(CommandSender sender) {
        if (!plugin.isMaintenanceMode()) {
            sender.sendMessage(tr("\u00a7cIntegrity operations require manually enabled maintenance mode."));
            return false;
        }
        return true;
    }

    private boolean checkAvailable(CommandSender sender) {
        if (state == State.SCANNING || state == State.APPLYING) {
            sender.sendMessage(tr("\u00a7cAn integrity operation is already running."));
            return false;
        }
        return true;
    }

    private boolean hasRunningJobs() {
        for (Map.Entry<String, JobManager.Stats> entry : JobManager.getStats().entrySet()) {
            if (entry.getValue().getRunningJobs() > 0) {
                return true;
            }
        }
        return false;
    }

    private void appendReport(String type, String message) {
        if (report == null) {
            return;
        }
        try {
            report.append(type, message);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to update integrity report", exception);
        }
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized boolean requiresMaintenanceMode() {
        return state == State.SCANNING || state == State.READY || state == State.APPLYING;
    }
}
