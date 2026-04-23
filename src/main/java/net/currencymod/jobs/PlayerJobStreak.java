package net.currencymod.jobs;

import net.minecraft.server.MinecraftServer;
import java.util.UUID;

/**
 * Manages player job streak data and bonuses.
 * Players receive bonuses for completing jobs on consecutive Minecraft days.
 */
public class PlayerJobStreak {
    // Bonus percentages for each streak day (starting with day 2)
    private static final double[] STREAK_BONUSES = {0.02, 0.03, 0.04, 0.05, 0.05, 0.06};
    private static final double MAX_STREAK_BONUS = 0.25; // 25% maximum bonus
    
    private final UUID playerUuid;
    private int streakLength;         // Current streak length (in days)
    private long lastCompletionDay;   // Last Minecraft day when a job was completed
    
    /**
     * Creates a new PlayerJobStreak with default values.
     *
     * @param playerUuid The player's UUID
     */
    public PlayerJobStreak(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.streakLength = 0;
        this.lastCompletionDay = -1;
    }
    
    /**
     * Creates a PlayerJobStreak with existing values.
     *
     * @param playerUuid The player's UUID
     * @param streakLength The player's current streak length
     * @param lastCompletionDay The last Minecraft day when the player completed a job
     */
    public PlayerJobStreak(UUID playerUuid, int streakLength, long lastCompletionDay) {
        this.playerUuid = playerUuid;
        this.streakLength = streakLength;
        this.lastCompletionDay = lastCompletionDay;
    }
    
    /**
     * Gets the player's UUID.
     *
     * @return The player's UUID
     */
    public UUID getPlayerUuid() {
        return playerUuid;
    }
    
    /**
     * Gets the player's current streak length.
     *
     * @return The streak length in days
     */
    public int getStreakLength() {
        return streakLength;
    }
    
    /**
     * Gets the last Minecraft day when the player completed a job.
     *
     * @return The last completion day
     */
    public long getLastCompletionDay() {
        return lastCompletionDay;
    }
    
    /**
     * Gets the current day in the Minecraft world.
     *
     * @param server The Minecraft server
     * @return The current Minecraft day
     */
    public static long getCurrentDay(MinecraftServer server) {
        // Minecraft day is 24000 ticks
        return server.getOverworld().getTimeOfDay() / 24000L;
    }
    
    /**
     * Records a job completion and updates the player's streak.
     *
     * @param server The Minecraft server
     * @return True if the streak advanced, false if it was broken or started
     */
    public boolean recordJobCompletion(MinecraftServer server) {
        long currentDay = getCurrentDay(server);
        boolean streakAdvanced = false;
        
        // First job completion
        if (lastCompletionDay == -1) {
            streakLength = 1;
            lastCompletionDay = currentDay;
            return false;
        }
        
        // Same day completion - no change to streak
        if (currentDay == lastCompletionDay) {
            return false;
        }
        
        // Next day completion - advance streak
        if (currentDay == lastCompletionDay + 1) {
            streakLength++;
            streakAdvanced = true;
        } 
        // Skipped a day or more - streak broken
        else {
            streakLength = 1;
            streakAdvanced = false;
        }
        
        lastCompletionDay = currentDay;
        return streakAdvanced;
    }
    
    /**
     * Gets the streak bonus multiplier based on the player's streak length.
     *
     * @return The streak bonus multiplier (between 1.0 and 1.25)
     */
    public double getStreakBonus() {
        if (streakLength <= 1) {
            return 1.0; // No bonus for first day
        }
        
        double bonus = 0.0;
        // Calculate cumulative bonus from streak days
        for (int i = 0; i < Math.min(streakLength - 1, STREAK_BONUSES.length); i++) {
            bonus += STREAK_BONUSES[i];
        }
        
        // Add flat 6% for each day beyond day 7
        if (streakLength > STREAK_BONUSES.length + 1) {
            int extraDays = streakLength - (STREAK_BONUSES.length + 1);
            bonus += extraDays * 0.06;
        }
        
        // Cap the bonus at the maximum
        bonus = Math.min(bonus, MAX_STREAK_BONUS);
        
        return 1.0 + bonus;
    }
    
    /**
     * Gets a formatted string showing the streak information.
     *
     * @return A formatted string with streak information
     */
    public String getFormattedStreakInfo() {
        if (streakLength <= 1) {
            return "No active streak";
        }
        
        int bonusPercent = (int)((getStreakBonus() * 100) - 100);
        return streakLength + " day streak (+" + bonusPercent + "% bonus)";
    }

    /**
     * Gets a formatted string showing the bonus percentage.
     *
     * @return A formatted string with bonus information
     */
    public String getFormattedStreakBonus() {
        int bonusPercent = (int)(getStreakBonus() * 100) - 100;
        return "+" + bonusPercent + "% reward bonus";
    }

    /**
     * Checks if the streak should end based on the current day
     * @param server The Minecraft server
     * @return true if the streak was ended, false otherwise
     */
    public boolean checkAndEndExpiredStreak(MinecraftServer server) {
        // Only check for players who have an active streak
        if (streakLength <= 0) {
            return false;
        }
        
        // Get the current Minecraft day
        long currentDay = getCurrentDay(server);
        
        // Check if more than one day has passed since the last completion
        if (currentDay > lastCompletionDay + 1) {
            // Streak has expired - reset it silently
            streakLength = 0;
            return true;
        }
        
        return false;
    }
} 