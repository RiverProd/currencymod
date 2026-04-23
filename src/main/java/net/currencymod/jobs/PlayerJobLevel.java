package net.currencymod.jobs;

import java.util.UUID;

/**
 * Manages player job level data and progression.
 */
public class PlayerJobLevel {
    public static final int MAX_LEVEL = 30;
    public static final double LEVEL_BONUS_PERCENT = 0.10; // 10% per level
    
    private final UUID playerUuid;
    private int level;
    private int completedJobs;
    
    /**
     * Creates a new PlayerJobLevel with default values.
     *
     * @param playerUuid The player's UUID
     */
    public PlayerJobLevel(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.level = 0;
        this.completedJobs = 0;
    }
    
    /**
     * Creates a PlayerJobLevel with existing values.
     *
     * @param playerUuid The player's UUID
     * @param level The player's current level
     * @param completedJobs The number of completed jobs at current level
     */
    public PlayerJobLevel(UUID playerUuid, int level, int completedJobs) {
        this.playerUuid = playerUuid;
        this.level = Math.min(level, MAX_LEVEL);
        this.completedJobs = completedJobs;
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
     * Gets the player's current job level.
     *
     * @return The player's level
     */
    public int getLevel() {
        return level;
    }
    
    /**
     * Gets the number of jobs completed at the current level.
     *
     * @return The number of completed jobs
     */
    public int getCompletedJobs() {
        return completedJobs;
    }
    
    /**
     * Gets the job reward multiplier based on the player's level.
     *
     * @return The reward multiplier (1.0 + level * bonus)
     */
    public double getRewardMultiplier() {
        return 1.0 + (level * LEVEL_BONUS_PERCENT);
    }
    
    /**
     * Gets the number of jobs a player should have based on their level.
     * 
     * - Levels 0-4: 5 jobs (default)
     * - Levels 5-9: 6 jobs
     * - Levels 10-14: 7 jobs
     * - Levels 15-19: 8 jobs
     * - Levels 20-24: 10 jobs
     * - Levels 25-29: 11 jobs
     * - Level 30: 12 jobs
     *
     * @return The number of jobs the player should have
     */
    public int getJobsAllowed() {
        // Base number of jobs
        int baseJobs = 5;
        
        if (level >= 30) {
            return 12; // Maximum at level 30
        } else if (level >= 25) {
            return 11; // Levels 25-29
        } else if (level >= 20) {
            return 10; // Levels 20-24
        } else if (level >= 15) {
            return 8; // Levels 15-19
        } else if (level >= 10) {
            return 7; // Levels 10-14
        } else if (level >= 5) {
            return 6; // Levels 5-9
        } else {
            return baseJobs; // Levels 0-4
        }
    }
    
    /**
     * Gets the number of jobs required to reach the next level.
     *
     * @return The jobs required for the next level, or 0 if at max level
     */
    public int getRequiredJobsForNextLevel() {
        if (level >= MAX_LEVEL) {
            return 0; // Already at max level
        }
        
        // Level 1: 5 jobs
        // Level 2: 10 more jobs
        // Level 3: 15 more jobs
        // ...
        // Level 30: 150 more jobs
        return (level + 1) * 5;
    }
    
    /**
     * Gets the total number of jobs required for the current level.
     *
     * @return The total jobs required to reach the current level
     */
    public int getTotalJobsForCurrentLevel() {
        int total = 0;
        for (int i = 1; i <= level; i++) {
            total += i * 5;
        }
        return total;
    }
    
    /**
     * Gets the progress percentage towards the next level.
     *
     * @return The progress percentage (0-100)
     */
    public int getProgressPercentage() {
        if (level >= MAX_LEVEL) {
            return 100;
        }
        
        int required = getRequiredJobsForNextLevel();
        if (required == 0) {
            return 100;
        }
        
        return (int) (completedJobs * 100.0 / required);
    }
    
    /**
     * Records a job completion and checks for level advancement.
     *
     * @return True if the player leveled up, false otherwise
     */
    public boolean completeJob() {
        completedJobs++;
        
        // Check if we've reached the requirements for the next level
        if (level < MAX_LEVEL && completedJobs >= getRequiredJobsForNextLevel()) {
            level++;
            completedJobs = 0; // Reset job counter after level up
            return true;
        }
        
        return false;
    }
    
    /**
     * Gets a formatted string showing the level and progress.
     *
     * @return A formatted string with level information
     */
    public String getFormattedLevelInfo() {
        if (level >= MAX_LEVEL) {
            return "Max Level " + level;
        }
        
        int progressPercent = getProgressPercentage();
        return "Level " + level + " (" + progressPercent + "% to next level)";
    }
    
    /**
     * Gets a formatted string showing the bonus percentage.
     *
     * @return A formatted string with bonus information
     */
    public String getFormattedBonus() {
        int bonusPercent = (int)(getRewardMultiplier() * 100) - 100;
        return "+" + bonusPercent + "% reward bonus";
    }
} 