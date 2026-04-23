package net.currencymod.economy;

import com.google.gson.*;
import net.currencymod.CurrencyMod;
import net.currencymod.util.FileUtil;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;

public class EconomyManager {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(UUID.class, new UUIDAdapter())
            .create();
    
    private static final String ECONOMY_FILE = "currency_mod/economy.json";
    private static final double DEFAULT_BALANCE = 100.0;

    private Map<UUID, Double> playerBalances = new HashMap<>();
    
    /**
     * Get a player's balance
     * @param playerUuid The UUID of the player
     * @return The player's balance
     */
    public double getBalance(UUID playerUuid) {
        // If player doesn't exist in the map, add them with the default balance
        if (!playerBalances.containsKey(playerUuid)) {
            playerBalances.put(playerUuid, DEFAULT_BALANCE);
            CurrencyMod.LOGGER.debug("Added player {} to economy with default balance", playerUuid);
        }
        return playerBalances.get(playerUuid);
    }
    
    /**
     * Set a player's balance
     * @param playerUuid The UUID of the player
     * @param amount The new balance amount
     */
    public void setBalance(UUID playerUuid, double amount) {
        playerBalances.put(playerUuid, amount);
    }
    
    /**
     * Add money to a player's balance
     * @param playerUuid The UUID of the player
     * @param amount The amount to add
     * @return The new balance
     */
    public double addBalance(UUID playerUuid, double amount) {
        double currentBalance = getBalance(playerUuid);
        double newBalance = currentBalance + amount;
        setBalance(playerUuid, newBalance);
        return newBalance;
    }
    
    /**
     * Remove money from a player's balance
     * @param playerUuid The UUID of the player
     * @param amount The amount to remove
     * @return The new balance, or -1 if the player doesn't have enough money
     */
    public double removeBalance(UUID playerUuid, double amount) {
        double currentBalance = getBalance(playerUuid);
        if (currentBalance < amount) {
            return -1; // Not enough money
        }
        
        double newBalance = currentBalance - amount;
        setBalance(playerUuid, newBalance);
        return newBalance;
    }
    
    /**
     * Transfer money from one player to another
     * @param fromUuid The UUID of the sender
     * @param toUuid The UUID of the receiver
     * @param amount The amount to transfer
     * @return True if the transfer was successful, false otherwise
     */
    public boolean transferMoney(UUID fromUuid, UUID toUuid, double amount) {
        if (amount <= 0) {
            return false;
        }
        
        double senderBalance = getBalance(fromUuid);
        if (senderBalance < amount) {
            return false; // Sender doesn't have enough money
        }
        
        // Remove from sender
        setBalance(fromUuid, senderBalance - amount);
        
        // Add to receiver
        double receiverBalance = getBalance(toUuid);
        setBalance(toUuid, receiverBalance + amount);
        
        return true;
    }
    
    /**
     * Load economy data from file
     * @param server The Minecraft server instance
     */
    public void loadData(MinecraftServer server) {
        if (server == null) {
            CurrencyMod.LOGGER.error("Cannot load economy data: server is null");
            return;
        }

        // Get the file using our utility class
        File economyFile = FileUtil.getServerFile(server, ECONOMY_FILE);
        if (economyFile == null) {
            CurrencyMod.LOGGER.error("Failed to get economy file path");
            return;
        }
        
        // Log the file path
        CurrencyMod.LOGGER.info("Loading economy data from: {}", economyFile.getAbsolutePath());
        
        // Check if the file is accessible
        if (!economyFile.exists()) {
            CurrencyMod.LOGGER.info("No economy data file found, starting with fresh data");
            playerBalances = new HashMap<>();
            return;
        }
        
        if (!FileUtil.isFileAccessible(economyFile, false)) {
            CurrencyMod.LOGGER.error("Economy file exists but is not accessible: {}", economyFile.getAbsolutePath());
            return;
        }
        
        try {
            // Read the file content
            String jsonContent = FileUtil.safeReadFromFile(economyFile);
            if (jsonContent == null || jsonContent.isEmpty()) {
                CurrencyMod.LOGGER.warn("Empty economy file, starting with fresh data");
                playerBalances = new HashMap<>();
                return;
            }
            
            // Parse the JSON
            JsonObject rootObject = JsonParser.parseString(jsonContent).getAsJsonObject();
            playerBalances.clear();
            
            // Deserialize the player balances
            for (Map.Entry<String, JsonElement> entry : rootObject.entrySet()) {
                try {
                    UUID playerUuid = UUID.fromString(entry.getKey());
                    double balance = entry.getValue().getAsDouble();
                    playerBalances.put(playerUuid, balance);
                } catch (Exception e) {
                    CurrencyMod.LOGGER.error("Error parsing player entry: " + entry.getKey(), e);
                }
            }
            
            CurrencyMod.LOGGER.info("Loaded economy data for {} players", playerBalances.size());
        } catch (Exception e) {
            CurrencyMod.LOGGER.error("Failed to load economy data", e);
            // Reset to empty map in case of error
            playerBalances = new HashMap<>();
        }
    }
    
    /**
     * Save economy data to file
     * @param server The Minecraft server instance
     */
    public void saveData(MinecraftServer server) {
        if (server == null) {
            CurrencyMod.LOGGER.error("Cannot save economy data: server is null");
            return;
        }

        // Get the file using our utility class
        File economyFile = FileUtil.getServerFile(server, ECONOMY_FILE);
        if (economyFile == null) {
            CurrencyMod.LOGGER.error("Failed to get economy file path");
            return;
        }
        
        // Log the file path
        CurrencyMod.LOGGER.info("Saving economy data to: {}", economyFile.getAbsolutePath());
        
        try {
            // Create a JSON object with the player balances
            JsonObject rootObject = new JsonObject();
            
            // Serialize the player balances
            for (Map.Entry<UUID, Double> entry : playerBalances.entrySet()) {
                rootObject.addProperty(entry.getKey().toString(), entry.getValue());
            }
            
            // Convert to JSON string
            String jsonContent = GSON.toJson(rootObject);
            
            // Write to file using our utility class
            boolean success = FileUtil.safeWriteToFile(server, economyFile, jsonContent);
            if (success) {
                CurrencyMod.LOGGER.info("Saved economy data for {} players", playerBalances.size());
            } else {
                CurrencyMod.LOGGER.error("Failed to save economy data");
            }
        } catch (Exception e) {
            CurrencyMod.LOGGER.error("Error during economy data saving", e);
        }
    }
    
    /**
     * Get all player balances
     * @return An unmodifiable map of all player UUIDs to their balances
     */
    public Map<UUID, Double> getAllBalances() {
        return Collections.unmodifiableMap(playerBalances);
    }
    
    /**
     * Type adapter for UUID to handle JSON serialization/deserialization
     */
    private static class UUIDAdapter implements JsonSerializer<UUID>, JsonDeserializer<UUID> {
        @Override
        public JsonElement serialize(UUID src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }

        @Override
        public UUID deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return UUID.fromString(json.getAsString());
        }
    }
} 