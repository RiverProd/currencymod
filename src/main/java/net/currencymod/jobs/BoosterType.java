package net.currencymod.jobs;

/**
 * Enum representing different types of job boosters.
 */
public enum BoosterType {
    /**
     * Premium booster: +20% payout, -20% item requirements, 30 minute duration
     */
    PREMIUM("Premium Booster", 20, 20, 30),
    
    /**
     * Basic booster: +15% payout, no item reduction, 30 minute duration
     */
    BASIC("Basic Booster", 15, 0, 30);
    
    private final String displayName;
    private final int rewardBonus;         // Percentage increase in reward
    private final int quantityReduction;   // Percentage reduction in item requirements
    private final int durationMinutes;     // Duration in minutes
    
    BoosterType(String displayName, int rewardBonus, int quantityReduction, int durationMinutes) {
        this.displayName = displayName;
        this.rewardBonus = rewardBonus;
        this.quantityReduction = quantityReduction;
        this.durationMinutes = durationMinutes;
    }
    
    /**
     * Gets the display name of this booster type.
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Gets the reward percentage bonus for this booster type.
     * @return The percentage bonus for rewards
     */
    public int getRewardBonus() {
        return rewardBonus;
    }
    
    /**
     * Gets the quantity percentage reduction for this booster type.
     * @return The percentage reduction in required items
     */
    public int getQuantityReduction() {
        return quantityReduction;
    }
    
    /**
     * Gets the duration of this booster type in minutes.
     * @return The duration in minutes
     */
    public int getDurationMinutes() {
        return durationMinutes;
    }
    
    /**
     * Gets the reward multiplier for this booster type.
     * @return The reward multiplier (e.g., 1.2 for +20%)
     */
    public double getRewardMultiplier() {
        return (rewardBonus + 100) / 100.0;
    }
    
    /**
     * Gets the quantity multiplier for this booster type.
     * @return The quantity multiplier (e.g., 0.8 for -20%)
     */
    public double getQuantityMultiplier() {
        return (100 - quantityReduction) / 100.0;
    }
} 