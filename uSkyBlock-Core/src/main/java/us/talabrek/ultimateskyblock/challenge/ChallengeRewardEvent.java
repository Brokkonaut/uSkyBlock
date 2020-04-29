package us.talabrek.ultimateskyblock.challenge;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import us.talabrek.ultimateskyblock.api.PlayerInfo;

/**
 * Fired before a player gets a reward
 */
public class ChallengeRewardEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final PlayerInfo playerInfo;
    private final Challenge challenge;
    private final Reward reward;
    private final boolean isFirstCompletion;

    public ChallengeRewardEvent(PlayerInfo playerInfo, Challenge challenge, Reward reward, boolean isFirstCompletion) {
        this.playerInfo = playerInfo;
        this.challenge = challenge;
        this.reward = reward;
        this.isFirstCompletion = isFirstCompletion;
    }

    public PlayerInfo getPlayerInfo() {
        return playerInfo;
    }

    public Challenge getChallenge() {
        return challenge;
    }

    public Reward getReward() {
        return reward;
    }

    public boolean isFirstCompletion() {
        return isFirstCompletion;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
