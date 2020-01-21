package us.talabrek.ultimateskyblock.command.admin;

import static dk.lockfuglsang.minecraft.po.I18nUtil.marktr;
import static dk.lockfuglsang.minecraft.po.I18nUtil.tr;

import dk.lockfuglsang.minecraft.command.AbstractCommand;
import java.util.Map;
import org.bukkit.command.CommandSender;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.command.admin.task.RecalculateAllLevelsTask;

/**
 * The recalculateall-command.
 */
public class RecalculateAllCommand extends AbstractCommand {
    private final uSkyBlock plugin;

    private RecalculateAllLevelsTask recalculateTask;

    public RecalculateAllCommand(uSkyBlock plugin) {
        super("recalculateall", "usb.admin.recalculateall", "?stop", marktr("recalculates the level of all islands"));
        this.plugin = plugin;
    }

    @Override
    public boolean execute(final CommandSender sender, String alias, Map<String, Object> data, String... args) {
        if (recalculateActive()) {
            tryStop(sender, args);
            return true;
        }
        if (args.length > 0) {
            sender.sendMessage(tr("\u00a74Not expecting any params!"));
            return false;
        }
        sender.sendMessage(tr("\u00a7eRecalculating all island levels."));
        recalculateTask = new RecalculateAllLevelsTask(plugin, plugin.directoryIslands, sender, () -> {
            recalculateTask = null;
        });
        recalculateTask.runTaskTimer(plugin, 20, 20);
        return true;
    }

    private boolean recalculateActive() {
        return recalculateTask != null && recalculateTask.isActive();
    }

    private void tryStop(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("stop")) {
            sender.sendMessage(tr("\u00a74Trying to abort recalculation"));
            recalculateTask.stop();
            return;
        }
        sender.sendMessage(tr("\u00a74A recalculation is already running.\u00a7e You can \u00a79stop\u00a7e it."));
    }
}
