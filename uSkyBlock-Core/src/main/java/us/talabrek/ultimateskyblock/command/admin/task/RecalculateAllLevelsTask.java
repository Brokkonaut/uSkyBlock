package us.talabrek.ultimateskyblock.command.admin.task;

import static dk.lockfuglsang.minecraft.po.I18nUtil.marktr;
import static dk.lockfuglsang.minecraft.po.I18nUtil.tr;
import static us.talabrek.ultimateskyblock.util.LogUtil.log;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dk.lockfuglsang.minecraft.file.FileUtil;
import dk.lockfuglsang.minecraft.util.TimeUtil;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.logging.Level;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.api.async.Callback;
import us.talabrek.ultimateskyblock.api.model.IslandScore;
import us.talabrek.ultimateskyblock.handler.WorldGuardHandler;
import us.talabrek.ultimateskyblock.util.IslandUtil;
import us.talabrek.ultimateskyblock.util.ProgressTracker;

public class RecalculateAllLevelsTask extends BukkitRunnable {

    private uSkyBlock plugin;
    private CommandSender sender;
    private Runnable callback;
    private ArrayDeque<String> islandList;
    private long tStart;
    private ProgressTracker tracker;
    private boolean active;
    private boolean calculating;
    private int progress;
    private int total;

    public RecalculateAllLevelsTask(uSkyBlock plugin, File islandDir, CommandSender sender, Runnable callback) {
        this.plugin = plugin;
        this.sender = sender;
        this.callback = callback;
        String[] islandList = islandDir.list(IslandUtil.createIslandFilenameFilter());
        this.islandList = new ArrayDeque<>(Arrays.asList(islandList));
        int feedbackEvery = plugin.getConfig().getInt("async.long.feedbackEvery", 30000);
        tStart = System.currentTimeMillis();
        tracker = new ProgressTracker(sender, marktr("\u00a77- UPDATING: {0,number,##}% ({1}/{2}) ~ {3}"), 25, feedbackEvery);
        active = true;
        calculating = false;
        progress = 0;
        total = this.islandList.size();
    }

    @Override
    public void run() {
        if (calculating) {
            return;
        }
        if (active && !islandList.isEmpty()) {
            progress++;
            tracker.progressUpdate(progress, total, TimeUtil.millisAsString(System.currentTimeMillis() - tStart));

            String islandFile = islandList.removeFirst();
            String islandName = FileUtil.getBasename(islandFile);
            final ProtectedRegion region = WorldGuardHandler.getIslandRegionAt(plugin.getIslandInfo(islandName).getIslandLocation());
            if (region == null) {
                return;
            }
            calculating = true;
            plugin.calculateScoreAsync(null, islandName, new Callback<IslandScore>() {
                @Override
                public void run() {
                    calculating = false;
                }
            });
        } else {
            cancel();
            log(Level.INFO, "Done updating island levels.");
            sender.sendMessage(tr("\u00a79Done updating island levels"));
            active = false;
            callback.run();
        }
    }

    public boolean isActive() {
        return active;
    }

    public void stop() {
        active = false;
    }
}
