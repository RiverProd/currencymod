package net.currencymod.jobs;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Represents an active job booster with its type, activation time, and expiration time.
 */
public class Booster {
    // Format for displaying time
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    
    private final BoosterType type;
    private final long activationTime;
    private final long expirationTime;
    
    /**
     * Creates a new booster with the specified type.
     * @param type The type of booster
     */
    public Booster(BoosterType type) {
        this.type = type;
        this.activationTime = System.currentTimeMillis();
        this.expirationTime = activationTime + (type.getDurationMinutes() * 60 * 1000);
    }
    
    /**
     * Creates a booster with the specified type and timing information.
     * Used when loading from storage.
     * @param type The type of booster
     * @param activationTime The time when the booster was activated
     * @param expirationTime The time when the booster will expire
     */
    public Booster(BoosterType type, long activationTime, long expirationTime) {
        this.type = type;
        this.activationTime = activationTime;
        this.expirationTime = expirationTime;
    }
    
    /**
     * Gets the type of this booster.
     * @return The booster type
     */
    public BoosterType getType() {
        return type;
    }
    
    /**
     * Gets the activation time of this booster.
     * @return The activation time in milliseconds
     */
    public long getActivationTime() {
        return activationTime;
    }
    
    /**
     * Gets the expiration time of this booster.
     * @return The expiration time in milliseconds
     */
    public long getExpirationTime() {
        return expirationTime;
    }
    
    /**
     * Checks if this booster is still active.
     * @return True if the booster is active, false if it has expired
     */
    public boolean isActive() {
        return System.currentTimeMillis() < expirationTime;
    }
    
    /**
     * Gets the number of seconds remaining before this booster expires.
     * @return The remaining time in seconds, or 0 if already expired
     */
    public int getRemainingSeconds() {
        long remainingMs = expirationTime - System.currentTimeMillis();
        if (remainingMs <= 0) {
            return 0;
        }
        return (int) (remainingMs / 1000);
    }
    
    /**
     * Gets a formatted string representing the remaining time.
     * @return A string in MM:SS format showing the remaining time
     */
    public String getFormattedRemainingTime() {
        int totalSeconds = getRemainingSeconds();
        if (totalSeconds <= 0) {
            return "Expired";
        }
        
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    /**
     * Gets a formatted string representing the expiration time.
     * @return A formatted time string showing when the booster will expire
     */
    public String getFormattedExpirationTime() {
        return TIME_FORMAT.format(new Date(expirationTime));
    }
    
    /**
     * Gets a formatted display string for this booster.
     * @return A description of this booster and its remaining time
     */
    public String getFormattedDisplay() {
        String status = isActive() ? "Active" : "Expired";
        return String.format("%s (%s) - %s", 
                type.getDisplayName(), 
                status,
                isActive() ? getFormattedRemainingTime() : "0:00");
    }
} 