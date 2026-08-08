package us.talabrek.ultimateskyblock.command.admin;

import dk.lockfuglsang.minecraft.command.AbstractCommand;
import dk.lockfuglsang.minecraft.po.I18nUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import us.talabrek.ultimateskyblock.island.IslandRelocationLogic;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.Map;

import static dk.lockfuglsang.minecraft.po.I18nUtil.marktr;

/**
 * Registers an island to a player.
 */
public class RegisterIslandToPlayerCommand extends AbstractCommand {
    public RegisterIslandToPlayerCommand() {
        super("register", "usb.admin.register", "player", marktr("set a player''s island to your location"));
    }
    @Override
    public boolean execute(final CommandSender sender, String alias, Map<String, Object> data, final String... args) {
        if (!(sender instanceof Player)) {
            return false;
        }
        if (args.length < 1) {
            return false;
        }
        String playerName = args[0];
        Player player = (Player) sender;
        boolean[] completedSynchronously = {false};
        boolean accepted = uSkyBlock.getInstance().devSetPlayerIsland(player, player.getLocation(), playerName,
                result -> {
                    completedSynchronously[0] = true;
                    sendResult(sender, playerName, result);
                });
        if (accepted && !completedSynchronously[0]) {
            sender.sendMessage(I18nUtil.tr("\u00a7eMoving {0}''s island to your location...", playerName));
        }
        return true;
    }

    private void sendResult(CommandSender sender, String playerName, IslandRelocationLogic.Result result) {
        switch (result.getStatus()) {
            case COMPLETED:
                sender.sendMessage(I18nUtil.tr("\u00a7aMoved {0}''s complete island to your location.", playerName));
                break;
            case REGISTERED:
                sender.sendMessage(I18nUtil.tr("\u00a7aSet {0}''s island to the current island.", playerName));
                break;
            case PLAYER_NOT_FOUND:
                sender.sendMessage(I18nUtil.tr("\u00a74Player not found: {0}", playerName));
                break;
            case NOT_IN_SKYWORLD:
                sender.sendMessage(I18nUtil.tr("\u00a74You must be in the SkyBlock world to register an island."));
                break;
            case INVALID_SOURCE:
                sender.sendMessage(I18nUtil.tr("\u00a74The player''s current island location is invalid or intersects spawn."));
                break;
            case SPAWN_INTERSECTION:
                sender.sendMessage(I18nUtil.tr("\u00a74The destination island would intersect the spawn area."));
                break;
            case ALREADY_ASSIGNED:
                sender.sendMessage(I18nUtil.tr("\u00a74Player is already assigned to this island!"));
                break;
            case NOT_LEADER:
                sender.sendMessage(I18nUtil.tr("\u00a74Only an island leader''s island can be moved."));
                break;
            case IGNORED_ISLAND:
                sender.sendMessage(I18nUtil.tr("\u00a74Ignored islands cannot be moved or overwritten."));
                break;
            case BUSY:
                sender.sendMessage(I18nUtil.tr("\u00a74The source or destination island is currently reserved or moving."));
                break;
            case LOCK_FAILED:
                sender.sendMessage(I18nUtil.tr("\u00a74Unable to protect the islands while moving them."));
                break;
            case COPY_FAILED:
                sender.sendMessage(I18nUtil.tr("\u00a74Unable to copy the island. The source island was not released."));
                break;
            case CHALLENGE_FAILED:
                sender.sendMessage(I18nUtil.tr("\u00a74Unable to copy the challenge progress. The source island was not released."));
                break;
            case METADATA_FAILED:
                sender.sendMessage(I18nUtil.tr("\u00a74Unable to update the relocated island data. Check the server log."));
                break;
            case CLEANUP_FAILED:
                sender.sendMessage(I18nUtil.tr("\u00a74The island was copied, but the old location could not be released safely."));
                break;
            default:
                sender.sendMessage(I18nUtil.tr("\u00a74Unable to move the island."));
        }
    }
}
