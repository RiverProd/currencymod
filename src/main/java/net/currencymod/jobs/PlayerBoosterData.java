package net.currencymod.jobs;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages a player's job boosters, both owned and active.
 */
public class PlayerBoosterData {
    private final UUID playerUuid;
    private final EnumMap<BoosterType, Integer> ownedBoosters;
    private Booster activeBooster;
    
    /**
     * Creates new booster data for a player.
     * @param playerUuid The player's UUID
     */
    public PlayerBoosterData(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.ownedBoosters = new EnumMap<>(BoosterType.class);
        this.activeBooster = null;
    }
    
    /**
     * Gets the player's UUID.
     * @return The player's UUID
     */
    public UUID getPlayerUuid() {
        return playerUuid;
    }
    
    /**
     * Gets the map of owned boosters.
     * @return Map of booster types to counts
     */
    public Map<BoosterType, Integer> getOwnedBoosters() {
        // Clean up the map by removing zero-count entries
        ownedBoosters.entrySet().removeIf(entry -> entry.getValue() <= 0);
        return ownedBoosters;
    }
    
    /**
     * Gets the count of a specific booster type owned by the player.
     * @param type The booster type
     * @return The number of boosters of that type owned
     */
    public int getBoosterCount(BoosterType type) {
        return ownedBoosters.getOrDefault(type, 0);
    }
    
    /**
     * Gets the total count of all boosters owned by the player.
     * @return The total number of boosters
     */
    public int getTotalBoosterCount() {
        int total = 0;
        for (Integer count : ownedBoosters.values()) {
            total += count;
        }
        return total;
    }
    
    /**
     * Gets the player's current active booster, if any.
     * If the active booster is expired, it will be removed and null returned.
     * @return The active booster, or null if none is active
     */
    public Booster getActiveBooster() {
        // Check if the active booster has expired
        if (activeBooster != null && !activeBooster.isActive()) {
            activeBooster = null;
        }
        
        return activeBooster;
    }
    
    /**
     * Adds a booster to the player's inventory.
     * @param type The type of booster to add
     */
    public void addBooster(BoosterType type) {
        int currentCount = ownedBoosters.getOrDefault(type, 0);
        ownedBoosters.put(type, currentCount + 1);
    }
    
    /**
     * Uses a booster from the player's inventory and activates it.
     * @param type The type of booster to use
     * @return True if the booster was used, false if the player doesn't have any
     */
    public boolean useBooster(BoosterType type) {
        int count = getBoosterCount(type);
        if (count <= 0) {
            return false;
        }
        
        // Remove one booster from inventory
        ownedBoosters.put(type, count - 1);
        
        // Create and set the active booster
        activeBooster = new Booster(type);
        
        return true;
    }
    
    /**
     * Sets the active booster (used when loading from storage).
     * @param booster The booster to set as active
     */
    public void setActiveBooster(Booster booster) {
        this.activeBooster = booster;
    }
    
    /**
     * Checks if the player has an active booster.
     * @return True if the player has an active booster
     */
    public boolean hasActiveBooster() {
        return getActiveBooster() != null;
    }
    
    /**
     * Gets the reward multiplier from the active booster.
     * @return The reward multiplier (e.g., 1.2 for +20%), or 1.0 if no active booster
     */
    public double getBoosterRewardMultiplier() {
        Booster booster = getActiveBooster();
        return (booster != null) ? booster.getType().getRewardMultiplier() : 1.0;
    }
    
    /**
     * Gets the quantity multiplier from the active booster.
     * @return The quantity multiplier (e.g., 0.8 for -20%), or 1.0 if no active booster
     */
    public double getBoosterQuantityMultiplier() {
        Booster booster = getActiveBooster();
        return (booster != null) ? booster.getType().getQuantityMultiplier() : 1.0;
    }
    
    /**
     * Gets the reward bonus percentage from the active booster.
     * @return The bonus percentage (e.g., 20 for +20%), or 0 if no active booster
     */
    public int getBoosterRewardBonusPercent() {
        Booster booster = getActiveBooster();
        return (booster != null) ? booster.getType().getRewardBonus() : 0;
    }
    
    /**
     * Gets the quantity reduction percentage from the active booster.
     * @return The reduction percentage (e.g., 20 for -20%), or 0 if no active booster
     */
    public int getBoosterQuantityReductionPercent() {
        Booster booster = getActiveBooster();
        return (booster != null) ? booster.getType().getQuantityReduction() : 0;
    }
} 