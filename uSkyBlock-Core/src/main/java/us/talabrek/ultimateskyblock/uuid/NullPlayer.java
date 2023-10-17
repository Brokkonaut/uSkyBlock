package us.talabrek.ultimateskyblock.uuid;

import org.bukkit.BanEntry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class NullPlayer implements OfflinePlayer {
    public static final NullPlayer INSTANCE = new NullPlayer();
    private NullPlayer() {
    }

    @Override
    public boolean isOnline() {
        return false;
    }

    @Override
    public String getName() {
        return PlayerDB.UNKNOWN_PLAYER_NAME;
    }

    @Override
    public UUID getUniqueId() {
        return PlayerDB.UNKNOWN_PLAYER_UUID;
    }

    @Override
    public boolean isBanned() {
        return false;
    }

    @Override
    public boolean isWhitelisted() {
        return true;
    }

    @Override
    public void setWhitelisted(boolean b) {

    }

    @Override
    public Player getPlayer() {
        return null;
    }

    @Override
    public long getFirstPlayed() {
        return 0;
    }

    @Override
    public long getLastPlayed() {
        return 0;
    }

    @Override
    public boolean hasPlayedBefore() {
        return false;
    }

    @Override
    public Location getBedSpawnLocation() {
        return null;
    }

    @Override
    public void incrementStatistic(@NotNull Statistic statistic) throws IllegalArgumentException {

    }

    @Override
    public void decrementStatistic(@NotNull Statistic statistic) throws IllegalArgumentException {

    }

    @Override
    public void incrementStatistic(@NotNull Statistic statistic, int amount) throws IllegalArgumentException {

    }

    @Override
    public void decrementStatistic(@NotNull Statistic statistic, int amount) throws IllegalArgumentException {

    }

    @Override
    public void setStatistic(@NotNull Statistic statistic, int newValue) throws IllegalArgumentException {

    }

    @Override
    public int getStatistic(@NotNull Statistic statistic) throws IllegalArgumentException {
        return 0;
    }

    @Override
    public void incrementStatistic(@NotNull Statistic statistic, @NotNull Material material) throws IllegalArgumentException {

    }

    @Override
    public void decrementStatistic(@NotNull Statistic statistic, @NotNull Material material) throws IllegalArgumentException {

    }

    @Override
    public int getStatistic(@NotNull Statistic statistic, @NotNull Material material) throws IllegalArgumentException {
        return 0;
    }

    @Override
    public void incrementStatistic(@NotNull Statistic statistic, @NotNull Material material, int amount) throws IllegalArgumentException {

    }

    @Override
    public void decrementStatistic(@NotNull Statistic statistic, @NotNull Material material, int amount) throws IllegalArgumentException {

    }

    @Override
    public void setStatistic(@NotNull Statistic statistic, @NotNull Material material, int newValue) throws IllegalArgumentException {

    }

    @Override
    public void incrementStatistic(@NotNull Statistic statistic, @NotNull EntityType entityType) throws IllegalArgumentException {

    }

    @Override
    public void decrementStatistic(@NotNull Statistic statistic, @NotNull EntityType entityType) throws IllegalArgumentException {

    }

    @Override
    public int getStatistic(@NotNull Statistic statistic, @NotNull EntityType entityType) throws IllegalArgumentException {
        return 0;
    }

    @Override
    public void incrementStatistic(@NotNull Statistic statistic, @NotNull EntityType entityType, int amount) throws IllegalArgumentException {

    }

    @Override
    public void decrementStatistic(@NotNull Statistic statistic, @NotNull EntityType entityType, int amount) {

    }

    @Override
    public void setStatistic(@NotNull Statistic statistic, @NotNull EntityType entityType, int newValue) {

    }

    @Override
    public Map<String, Object> serialize() {
        return null;
    }

    @Override
    public boolean isOp() {
        return false;
    }

    @Override
    public void setOp(boolean b) {

    }

    @Override
    public long getLastLogin() {
        return 0;
    }

    @Override
    public long getLastSeen() {
        return 0;
    }

    @Override
    public @NotNull PlayerProfile getPlayerProfile() {
        return new PlayerProfile() {
            @Override
            public @NotNull Map<String, Object> serialize() {
                return Collections.emptyMap();
            }

            @Override
            public @Nullable UUID getUniqueId() {
                return PlayerDB.UNKNOWN_PLAYER_UUID;
            }

            @Override
            public @Nullable String getName() {
                return PlayerDB.UNKNOWN_PLAYER_NAME;
            }

            @Override
            public @NotNull PlayerTextures getTextures() {
                return new PlayerTextures() {
                    @Override
                    public boolean isEmpty() {
                        return true;
                    }

                    @Override
                    public void clear() {
                    }

                    @Override
                    public @Nullable URL getSkin() {
                        return null;
                    }

                    @Override
                    public void setSkin(@Nullable URL skinUrl) {
                    }

                    @Override
                    public void setSkin(@Nullable URL skinUrl, @Nullable SkinModel skinModel) {
                    }

                    @Override
                    public @NotNull SkinModel getSkinModel() {
                        return null;
                    }

                    @Override
                    public @Nullable URL getCape() {
                        return null;
                    }

                    @Override
                    public void setCape(@Nullable URL capeUrl) {
                    }
                    
                    @Override
                    public long getTimestamp() {
                        return 0;
                    }

                    @Override
                    public boolean isSigned() {
                        return false;
                    }};
            }

            @Override
            public void setTextures(@Nullable PlayerTextures textures) {
            }

            @Override
            public boolean isComplete() {
                return false;
            }

            @Override
            public @NotNull CompletableFuture<PlayerProfile> update() {
                return null;
            }

            @Override
            public @NotNull PlayerProfile clone() {
                return this;
            }

            @Override
            public @NotNull String setName(@Nullable String name) {
                return null;
            }

            @Override
            public @Nullable UUID getId() {
                return PlayerDB.UNKNOWN_PLAYER_UUID;
            }

            @Override
            public @Nullable UUID setId(@Nullable UUID uuid) {
                return null;
            }

            @Override
            public @NotNull Set<ProfileProperty> getProperties() {
                return Collections.emptySet();
            }

            @Override
            public boolean hasProperty(@Nullable String property) {
                return false;
            }

            @Override
            public void setProperty(@NotNull ProfileProperty property) {
            }

            @Override
            public void setProperties(@NotNull Collection<ProfileProperty> properties) {
            }

            @Override
            public boolean removeProperty(@Nullable String property) {
                return false;
            }

            @Override
            public void clearProperties() {
            }

            @Override
            public boolean completeFromCache() {
                return false;
            }

            @Override
            public boolean completeFromCache(boolean onlineMode) {
                return false;
            }

            @Override
            public boolean completeFromCache(boolean lookupUUID, boolean onlineMode) {
                return false;
            }

            @Override
            public boolean complete(boolean textures) {
                return false;
            }

            @Override
            public boolean complete(boolean textures, boolean onlineMode) {
                return false;
            }};
    }

    @Override
    public @Nullable Location getLastDeathLocation() {
        return null;
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public <E extends BanEntry<? super PlayerProfile>> @Nullable E ban(@Nullable String reason, @Nullable Date expires, @Nullable String source) {
        return null;
    }

    @Override
    public <E extends BanEntry<? super PlayerProfile>> @Nullable E ban(@Nullable String reason, @Nullable Instant expires, @Nullable String source) {
        return null;
    }

    @Override
    public <E extends BanEntry<? super PlayerProfile>> @Nullable E ban(@Nullable String reason, @Nullable Duration duration, @Nullable String source) {
        return null;
    }
}
