package net.currencymod.jobs;

import java.util.UUID;

/**
 * Manages a player's job skip data.
 */
public class PlayerJobSkipData {
    private final UUID playerUuid;
    private int skipCount;
    private int lastRewardedLevel; // The last level for which skips were awarded
    
    /**
     * Creates new skip data for a player.
     * @param playerUuid The player's UUID
     */
    public PlayerJobSkipData(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.skipCount = 0;
        this.lastRewardedLevel = -1; // -1 means never rewarded
    }
    
    /**
     * Creates skip data with existing values.
     * @param playerUuid The player's UUID
     * @param skipCount The current skip count
     * @param lastRewardedLevel The last level for which skips were awarded
     */
    public PlayerJobSkipData(UUID playerUuid, int skipCount, int lastRewardedLevel) {
        this.playerUuid = playerUuid;
        this.skipCount = skipCount;
        this.lastRewardedLevel = lastRewardedLevel;
    }
    
    /**
     * Gets the player's UUID.
     * @return The player's UUID
     */
    public UUID getPlayerUuid() {
        return playerUuid;
    }
    
    /**
     * Gets the current skip count.
     * @return The number of skips owned
     */
    public int getSkipCount() {
        return skipCount;
    }
    
    /**
     * Sets the skip count.
     * @param skipCount The new skip count
     */
    public void setSkipCount(int skipCount) {
        this.skipCount = Math.max(0, skipCount);
    }
    
    /**
     * Adds skips to the player's count.
     * @param amount The number of skips to add
     */
    public void addSkips(int amount) {
        this.skipCount += amount;
    }
    
    /**
     * Uses a skip (decrements count by 1).
     * @return True if a skip was used, false if the player has no skips
     */
    public boolean useSkip() {
        if (skipCount <= 0) {
            return false;
        }
        skipCount--;
        return true;
    }
    
    /**
     * Gets the last level for which skips were awarded.
     * @return The last rewarded level, or -1 if never rewarded
     */
    public int getLastRewardedLevel() {
        return lastRewardedLevel;
    }
    
    /**
     * Sets the last level for which skips were awarded.
     * @param level The level
     */
    public void setLastRewardedLevel(int level) {
        this.lastRewardedLevel = level;
    }
    
    /**
     * Checks if the player has been rewarded skips for the given level.
     * @param level The level to check
     * @return True if skips have been awarded for this level
     */
    public boolean hasBeenRewardedForLevel(int level) {
        return lastRewardedLevel >= level;
    }
}

