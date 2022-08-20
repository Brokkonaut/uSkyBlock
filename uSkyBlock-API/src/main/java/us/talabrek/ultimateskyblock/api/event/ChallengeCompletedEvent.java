package us.talabrek.ultimateskyblock.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import us.talabrek.ultimateskyblock.api.PlayerInfo;

public class ChallengeCompletedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final PlayerInfo playerInfo;
    private final String challenge;

    public ChallengeCompletedEvent(PlayerInfo playerInfo, String challenge) {
        this.playerInfo = playerInfo;
        this.challenge = challenge;
    }

    public PlayerInfo getPlayerInfo() {
        return playerInfo;
    }

    public String getChallenge() {
        return challenge;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
