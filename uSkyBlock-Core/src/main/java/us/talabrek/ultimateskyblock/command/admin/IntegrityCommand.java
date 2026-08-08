package us.talabrek.ultimateskyblock.command.admin;

import dk.lockfuglsang.minecraft.command.AbstractCommand;
import dk.lockfuglsang.minecraft.command.CompositeCommand;
import org.bukkit.command.CommandSender;
import us.talabrek.ultimateskyblock.integrity.IslandDataIntegrityLogic;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.Map;

import static dk.lockfuglsang.minecraft.po.I18nUtil.marktr;

/** Administrative entry point for global island/player data integrity checks. */
public class IntegrityCommand extends CompositeCommand {
    public IntegrityCommand(uSkyBlock plugin) {
        super("integrity", "usb.admin.integrity", marktr("scans and repairs island/player data integrity"));
        IslandDataIntegrityLogic integrity = plugin.getIslandDataIntegrityLogic();
        add(new AbstractCommand("scan", "usb.admin.integrity", marktr("creates a read-only integrity report and repair plan")) {
            @Override
            public boolean execute(CommandSender sender, String alias, Map<String, Object> data, String... args) {
                return integrity.scan(sender);
            }
        });
        add(new AbstractCommand("status", "usb.admin.integrity", marktr("shows the current integrity operation")) {
            @Override
            public boolean execute(CommandSender sender, String alias, Map<String, Object> data, String... args) {
                return integrity.status(sender);
            }
        });
        add(new AbstractCommand("confirm", "usb.admin.integrity", marktr("verifies, backs up and applies the scanned plan")) {
            @Override
            public boolean execute(CommandSender sender, String alias, Map<String, Object> data, String... args) {
                return integrity.confirm(sender);
            }
        });
        add(new AbstractCommand("cancel", "usb.admin.integrity", marktr("cancels a scan or discards a pending plan")) {
            @Override
            public boolean execute(CommandSender sender, String alias, Map<String, Object> data, String... args) {
                return integrity.cancel(sender);
            }
        });
    }
}
