package net.currencymod.economy;

import com.google.gson.*;
import net.currencymod.CurrencyMod;
import net.currencymod.jobs.BoosterType;
import net.currencymod.jobs.JobManager;
import net.currencymod.jobs.PlayerBoosterData;
import net.currencymod.util.FileUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages daily login rewards for players.
 * Tracks consecutive login streaks and rewards players based on their streak and job level.
 */
public class DailyLoginRewardManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/DailyLoginRewardManager");
    private static final String DATA_FILE = "currency_mod/daily_login_rewards.json";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    
    // Reward constants
    private static final int BASE_REWARD_PER_DAY = 20;  // $20 per day
    private static final int MAX_BASE_REWARD = 140;      // Cap at $140 after day 7
    private static final int JOB_LEVEL_BONUS_PER_DAY = 1; // $1 per job level per day
    private static final int PREMIUM_BOOSTER_INTERVAL = 7; // Every 7 consecutive days
    
    private static DailyLoginRewardManager instance;
    private final Map<UUID, PlayerLoginData> playerData;
    private final Gson gson;
    private boolean dataLoaded = false;
    
    /**
     * Data class to store player login information
     */
    private static class PlayerLoginData {
        String lastLoginDate;  // ISO date string (YYYY-MM-DD)
        int consecutiveDays;  // Consecutive login streak
        
        PlayerLoginData() {
            this.lastLoginDate = "";
            this.consecutiveDays = 0;
        }
        
        PlayerLoginData(String lastLoginDate, int consecutiveDays) {
            this.lastLoginDate = lastLoginDate;
            this.consecutiveDays = consecutiveDays;
        }
    }
    
    /**
     * Gets the singleton instance of the daily login reward manager.
     * @return The manager instance
     */
    public static DailyLoginRewardManager getInstance() {
        if (instance == null) {
            instance = new DailyLoginRewardManager();
        }
        return instance;
    }
    
    /**
     * Creates a new daily login reward manager.
     */
    private DailyLoginRewardManager() {
        this.playerData = new HashMap<>();
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(UUID.class, new UUIDAdapter())
            .create();
    }
    
    /**
     * Checks if a player should receive a daily login reward and processes it.
     * This should be called when a player joins the server.
     * 
     * @param player The player who joined
     */
    public void checkDailyLoginReward(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        
        // Ensure data is loaded (lazy loading)
        if (!dataLoaded) {
            loadData();
            dataLoaded = true;
        }
        
        UUID playerUuid = player.getUuid();
        String today = LocalDate.now().format(DATE_FORMATTER);
        
        PlayerLoginData data = playerData.getOrDefault(playerUuid, new PlayerLoginData());
        
        // Check if player has already logged in today
        if (today.equals(data.lastLoginDate)) {
            // Already logged in today, no reward
            return;
        }
        
        // Check if this is a consecutive login
        boolean isConsecutive = false;
        int newStreak = 1;
        
        if (!data.lastLoginDate.isEmpty()) {
            try {
                LocalDate lastLogin = LocalDate.parse(data.lastLoginDate, DATE_FORMATTER);
                LocalDate todayDate = LocalDate.parse(today, DATE_FORMATTER);
                
                // Check if last login was yesterday (consecutive)
                if (lastLogin.plusDays(1).equals(todayDate)) {
                    isConsecutive = true;
                    newStreak = data.consecutiveDays + 1;
                }
                // If last login was more than 1 day ago, reset streak
                // (If it's the same day, we already returned above)
            } catch (Exception e) {
                LOGGER.warn("Error parsing last login date for player {}: {}", playerUuid, e.getMessage());
                // Treat as new player, start streak at 1
            }
        }
        
        // Update the login data
        data.lastLoginDate = today;
        data.consecutiveDays = newStreak;
        playerData.put(playerUuid, data);
        
        // Calculate and give rewards
        giveDailyReward(player, newStreak);
        
        // Save the updated data
        saveData();
    }
    
    /**
     * Calculates and gives the daily login reward to a player.
     * 
     * @param player The player to reward
     * @param streak The consecutive login streak
     */
    private void giveDailyReward(ServerPlayerEntity player, int streak) {
        UUID playerUuid = player.getUuid();
        
        // Calculate base reward (capped at MAX_BASE_REWARD after day 7)
        int baseReward = Math.min(streak * BASE_REWARD_PER_DAY, MAX_BASE_REWARD);
        
        // Get player's job level
        JobManager jobManager = JobManager.getInstance();
        int jobLevel = jobManager.getPlayerJobLevel(playerUuid).getLevel();
        
        // Calculate job level bonus
        int jobLevelBonus = jobLevel * JOB_LEVEL_BONUS_PER_DAY;
        
        // Total reward
        int totalReward = baseReward + jobLevelBonus;
        
        // Give money reward
        EconomyManager economyManager = CurrencyMod.getEconomyManager();
        economyManager.addBalance(playerUuid, totalReward);
        
        // Check if player should receive a premium booster (every 7 days)
        boolean givePremiumBooster = (streak % PREMIUM_BOOSTER_INTERVAL == 0);
        
        // Log reward calculation for debugging
        LOGGER.info("Daily login reward calculation for player {}: streak={}, baseReward={}, jobLevel={}, jobLevelBonus={}, totalReward={}",
            player.getName().getString(), streak, baseReward, jobLevel, jobLevelBonus, totalReward);
        
        if (givePremiumBooster) {
            PlayerBoosterData boosterData = jobManager.getPlayerBoosterData(playerUuid);
            boosterData.addBooster(BoosterType.PREMIUM);
            jobManager.save(); // Save booster data
            
            // Build message with proper formatting
            Text message;
            if (streak == 1) {
                message = Text.literal("Thanks for logging in. You've been rewarded ")
                    .formatted(Formatting.GREEN)
                    .append(Text.literal("$").formatted(Formatting.GOLD, Formatting.BOLD))
                    .append(Text.literal(String.valueOf(totalReward)).formatted(Formatting.GOLD, Formatting.BOLD))
                    .append(Text.literal(" and a Premium Job Booster!").formatted(Formatting.GREEN));
            } else {
                message = Text.literal("Thanks for logging in for ")
                    .formatted(Formatting.GREEN)
                    .append(Text.literal(String.valueOf(streak)).formatted(Formatting.GOLD, Formatting.BOLD))
                    .append(Text.literal(" days! You've been rewarded ").formatted(Formatting.GREEN))
                    .append(Text.literal("$").formatted(Formatting.GOLD, Formatting.BOLD))
                    .append(Text.literal(String.valueOf(totalReward)).formatted(Formatting.GOLD, Formatting.BOLD))
                    .append(Text.literal(" and a Premium Job Booster!").formatted(Formatting.GREEN));
            }
            
            player.sendMessage(message, false);
            
            LOGGER.info("Daily login reward: Player {} received ${} and Premium Booster (streak: {}, job level: {})",
                player.getName().getString(), totalReward, streak, jobLevel);
        } else {
            // Build message with proper formatting
            Text message;
            if (streak == 1) {
                message = Text.literal("Thanks for logging in. You've been rewarded ")
                    .formatted(Formatting.GREEN)
                    .append(Text.literal("$").formatted(Formatting.GOLD, Formatting.BOLD))
                    .append(Text.literal(String.valueOf(totalReward)).formatted(Formatting.GOLD, Formatting.BOLD))
                    .append(Text.literal(".").formatted(Formatting.GREEN));
            } else {
                message = Text.literal("Thanks for logging in for ")
                    .formatted(Formatting.GREEN)
                    .append(Text.literal(String.valueOf(streak)).formatted(Formatting.GOLD, Formatting.BOLD))
                    .append(Text.literal(" days! You've been rewarded ").formatted(Formatting.GREEN))
                    .append(Text.literal("$").formatted(Formatting.GOLD, Formatting.BOLD))
                    .append(Text.literal(String.valueOf(totalReward)).formatted(Formatting.GOLD, Formatting.BOLD))
                    .append(Text.literal(".").formatted(Formatting.GREEN));
            }
            
            player.sendMessage(message, false);
            
            LOGGER.info("Daily login reward: Player {} received ${} (streak: {}, job level: {})",
                player.getName().getString(), totalReward, streak, jobLevel);
        }
    }
    
    /**
     * Loads daily login reward data from disk.
     */
    private void loadData() {
        MinecraftServer server = CurrencyMod.getServer();
        if (server == null) {
            LOGGER.warn("Cannot load daily login reward data: server is null");
            return;
        }
        
        File dataFile = FileUtil.getServerFile(server, DATA_FILE);
        if (!dataFile.exists()) {
            LOGGER.info("No daily login reward data found, starting fresh");
            return;
        }
        
        try (FileReader reader = new FileReader(dataFile)) {
            JsonObject rootObj = JsonParser.parseReader(reader).getAsJsonObject();
            playerData.clear();
            
            for (String uuidStr : rootObj.keySet()) {
                try {
                    UUID playerUuid = UUID.fromString(uuidStr);
                    JsonObject playerObj = rootObj.getAsJsonObject(uuidStr);
                    
                    String lastLoginDate = playerObj.has("lastLoginDate") 
                        ? playerObj.get("lastLoginDate").getAsString() 
                        : "";
                    int consecutiveDays = playerObj.has("consecutiveDays")
                        ? playerObj.get("consecutiveDays").getAsInt()
                        : 0;
                    
                    playerData.put(playerUuid, new PlayerLoginData(lastLoginDate, consecutiveDays));
                } catch (Exception e) {
                    LOGGER.error("Error loading login data for player {}: {}", uuidStr, e.getMessage());
                }
            }
            
            LOGGER.info("Loaded daily login reward data for {} players", playerData.size());
            dataLoaded = true;
        } catch (IOException e) {
            LOGGER.error("Failed to load daily login reward data: {}", e.getMessage());
            dataLoaded = true; // Mark as loaded even on error to prevent retry loops
        }
    }
    
    /**
     * Saves daily login reward data to disk.
     */
    private void saveData() {
        MinecraftServer server = CurrencyMod.getServer();
        if (server == null) {
            LOGGER.warn("Cannot save daily login reward data: server is null");
            return;
        }
        
        File dataFile = FileUtil.getServerFile(server, DATA_FILE);
        try (FileWriter writer = new FileWriter(dataFile)) {
            JsonObject rootObj = new JsonObject();
            
            for (Map.Entry<UUID, PlayerLoginData> entry : playerData.entrySet()) {
                JsonObject playerObj = new JsonObject();
                playerObj.addProperty("lastLoginDate", entry.getValue().lastLoginDate);
                playerObj.addProperty("consecutiveDays", entry.getValue().consecutiveDays);
                rootObj.add(entry.getKey().toString(), playerObj);
            }
            
            gson.toJson(rootObj, writer);
            LOGGER.debug("Saved daily login reward data for {} players", playerData.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save daily login reward data: {}", e.getMessage());
        }
    }
    
    /**
     * Type adapter for UUID to handle JSON serialization/deserialization
     */
    private static class UUIDAdapter implements JsonSerializer<UUID>, JsonDeserializer<UUID> {
        @Override
        public JsonElement serialize(UUID src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }
        
        @Override
        public UUID deserialize(JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return UUID.fromString(json.getAsString());
        }
    }
}

