package net.currencymod.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.currencymod.CurrencyMod;
import net.currencymod.config.ModConfig;
import net.currencymod.economy.EconomyManager;
import net.currencymod.shop.ShopAccess;
import net.currencymod.shop.ShopData;
import net.currencymod.shop.ShopManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import com.mojang.authlib.GameProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages synchronization of player balance data to the website API.
 * Periodically collects player balances and sends them via HTTP POST.
 */
public class WebSyncManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/WebSyncManager");
    private static WebSyncManager instance;
    
    private MinecraftServer server;
    private Timer syncTimer;
    private Timer shopSyncTimer;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isShopSyncRunning = new AtomicBoolean(false);
    private final HttpClient httpClient;
    private final Gson gson;
    
    // Pattern to match "quantity : price" format
    private static final Pattern QUANTITY_PRICE_PATTERN = Pattern.compile("(\\d+)\\s*:\\s*\\$?(\\d+(?:\\.\\d+)?)");
    
    // Shop sync interval: 30 minutes = 1800 seconds
    private static final int SHOP_SYNC_INTERVAL_SECONDS = 1800;
    
    private WebSyncManager() {
        // Create HTTP client with timeout
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        
        // Create Gson instance for JSON serialization
        this.gson = new GsonBuilder().create();
    }
    
    /**
     * Get the singleton instance of WebSyncManager
     */
    public static WebSyncManager getInstance() {
        if (instance == null) {
            instance = new WebSyncManager();
        }
        return instance;
    }
    
    /**
     * Initialize the web sync manager with server instance
     */
    public void initialize(MinecraftServer server) {
        if (this.server != null) {
            LOGGER.warn("WebSyncManager already initialized, skipping");
            return;
        }
        
        this.server = server;
        
        ModConfig config = ModConfig.getInstance();
        
        // Check if web sync is enabled
        if (!config.isWebSyncEnabled()) {
            LOGGER.info("Web sync is disabled in configuration");
            return;
        }
        
        String apiUrl = config.getWebSyncApiUrl();
        if (apiUrl == null || apiUrl.isEmpty()) {
            LOGGER.warn("Web sync API URL is not configured, web sync disabled");
            return;
        }
        
        // Start the sync timer
        startSyncTimer();
        // Start the shop sync timer (separate, runs every 30 minutes)
        startShopSyncTimer();
        LOGGER.info("WebSyncManager initialized with sync interval: {} seconds", config.getWebSyncInterval());
    }
    
    /**
     * Start the periodic sync timer
     */
    private void startSyncTimer() {
        if (isRunning.get()) {
            LOGGER.warn("Sync timer is already running");
            return;
        }
        
        ModConfig config = ModConfig.getInstance();
        int intervalSeconds = config.getWebSyncInterval();
        
        if (intervalSeconds <= 0) {
            LOGGER.warn("Invalid sync interval: {} seconds, using default 60", intervalSeconds);
            intervalSeconds = 60;
        }
        
        syncTimer = new Timer("CurrencyMod-WebSync", true);
        syncTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (server != null) {
                    syncPlayerData();
                }
            }
        }, 0, intervalSeconds * 1000L);
        
        isRunning.set(true);
        LOGGER.info("Web sync timer started with interval: {} seconds", intervalSeconds);
    }
    
    /**
     * Start the periodic shop sync timer (runs every 30 minutes)
     */
    private void startShopSyncTimer() {
        if (isShopSyncRunning.get()) {
            LOGGER.warn("Shop sync timer is already running");
            return;
        }
        
        ModConfig config = ModConfig.getInstance();
        
        // Check if web sync is enabled
        if (!config.isWebSyncEnabled()) {
            LOGGER.info("Web sync is disabled, shop sync will not start");
            return;
        }
        
        String apiUrl = config.getWebSyncApiUrl();
        if (apiUrl == null || apiUrl.isEmpty()) {
            LOGGER.warn("Web sync API URL is not configured, shop sync disabled");
            return;
        }
        
        shopSyncTimer = new Timer("CurrencyMod-ShopSync", true);
        shopSyncTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (server != null) {
                    syncShopData();
                }
            }
        }, 30000, SHOP_SYNC_INTERVAL_SECONDS * 1000L); // 30 second initial delay to ensure shops are loaded
        
        isShopSyncRunning.set(true);
        LOGGER.info("Shop sync timer started with 30 second initial delay, then every {} seconds (30 minutes)", SHOP_SYNC_INTERVAL_SECONDS);
    }
    
    /**
     * Stop the sync timer and cleanup
     */
    public void shutdown() {
        if (syncTimer != null) {
            syncTimer.cancel();
            syncTimer = null;
        }
        if (shopSyncTimer != null) {
            shopSyncTimer.cancel();
            shopSyncTimer = null;
        }
        isRunning.set(false);
        isShopSyncRunning.set(false);
        server = null;
        LOGGER.info("WebSyncManager shut down");
    }
    
    /**
     * Collect player data and sync to the website API
     */
    private void syncPlayerData() {
        if (server == null) {
            LOGGER.warn("Cannot sync: server is null");
            return;
        }
        
        ModConfig config = ModConfig.getInstance();
        if (!config.isWebSyncEnabled()) {
            LOGGER.debug("Web sync is disabled, skipping sync");
            return;
        }
        
        String apiUrl = config.getWebSyncApiUrl();
        String apiKey = config.getWebSyncApiKey();
        
        if (apiUrl == null || apiUrl.isEmpty()) {
            LOGGER.warn("Web sync API URL is not configured");
            return;
        }
        
        LOGGER.debug("Starting web sync to: {}", apiUrl);
        
        try {
            // Collect player data
            List<PlayerBalanceData> playerData = collectPlayerData();
            
            if (playerData.isEmpty()) {
                LOGGER.info("No player data to sync (no players with balances found)");
                return;
            }
            
            LOGGER.info("Syncing {} players to website...", playerData.size());
            
            // Build JSON payload
            JsonArray playersArray = new JsonArray();
            for (PlayerBalanceData data : playerData) {
                JsonObject playerObj = new JsonObject();
                playerObj.addProperty("uuid", data.uuid.toString());
                playerObj.addProperty("name", data.name);
                playerObj.addProperty("balance", data.balance);
                playersArray.add(playerObj);
            }
            
            JsonObject payload = new JsonObject();
            payload.add("players", playersArray);
            
            String jsonPayload = gson.toJson(payload);
            
            // Build HTTP request
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));
            
            // Add API key if configured
            if (apiKey != null && !apiKey.isEmpty()) {
                requestBuilder.header("X-API-Key", apiKey);
            }
            
            HttpRequest request = requestBuilder.build();
            
            // Send request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOGGER.info("Successfully synced {} players to website", playerData.size());
            } else {
                LOGGER.warn("Web sync failed with status code: {} - {}", response.statusCode(), response.body());
            }
            
        } catch (IOException e) {
            LOGGER.warn("Network error during web sync: {}", e.getMessage());
        } catch (InterruptedException e) {
            LOGGER.warn("Web sync interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.error("Unexpected error during web sync", e);
        }
    }
    
    /**
     * Collect player balance data from the economy manager
     */
    private List<PlayerBalanceData> collectPlayerData() {
        List<PlayerBalanceData> playerData = new ArrayList<>();
        
        if (server == null) {
            return playerData;
        }
        
        EconomyManager economyManager = CurrencyMod.getEconomyManager();
        if (economyManager == null) {
            LOGGER.warn("EconomyManager is null, cannot collect player data");
            return playerData;
        }
        
        Map<UUID, Double> allBalances = economyManager.getAllBalances();
        
        for (Map.Entry<UUID, Double> entry : allBalances.entrySet()) {
            UUID uuid = entry.getKey();
            double balance = entry.getValue();
            String name = getPlayerName(uuid);
            
            playerData.add(new PlayerBalanceData(uuid, name, balance));
        }
        
        return playerData;
    }
    
    /**
     * Get a player's name from their UUID.
     * Uses the same pattern as BaltopCommand.
     */
    private String getPlayerName(UUID uuid) {
        if (server == null) {
            return shortenUuid(uuid);
        }
        
        // Try to find the player on the server (online players)
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            return player.getName().getString();
        }
        
        // If player is offline, try to get their name from the user cache
        var userCache = server.getUserCache();
        if (userCache != null) {
            Optional<GameProfile> profileOpt = userCache.getByUuid(uuid);
            if (profileOpt.isPresent()) {
                GameProfile profile = profileOpt.get();
                return profile.getName();
            }
        }
        
        // If we can't find the name, use a shortened UUID
        return shortenUuid(uuid);
    }
    
    /**
     * Shorten a UUID for display purposes
     */
    private String shortenUuid(UUID uuid) {
        String uuidString = uuid.toString();
        return uuidString.substring(0, 8) + "...";
    }
    
    /**
     * Collect shop data and sync to the website API
     */
    private void syncShopData() {
        if (server == null) {
            LOGGER.warn("Cannot sync shops: server is null");
            return;
        }
        
        ModConfig config = ModConfig.getInstance();
        if (!config.isWebSyncEnabled()) {
            LOGGER.debug("Web sync is disabled, skipping shop sync");
            return;
        }
        
        String apiUrl = config.getWebSyncApiUrl();
        String apiKey = config.getWebSyncApiKey();
        
        if (apiUrl == null || apiUrl.isEmpty()) {
            LOGGER.warn("Web sync API URL is not configured");
            return;
        }
        
        // Extract base URL and construct shops endpoint
        String shopsApiUrl;
        if (apiUrl.contains("/api/player-balances")) {
            shopsApiUrl = apiUrl.replace("/api/player-balances", "/api/shops");
        } else if (apiUrl.endsWith("/")) {
            shopsApiUrl = apiUrl + "api/shops";
        } else {
            shopsApiUrl = apiUrl + "/api/shops";
        }
        
        LOGGER.debug("Starting shop sync to: {}", shopsApiUrl);
        
        try {
            // Collect shop data from all worlds
            List<ShopDataForSync> shopData = collectShopData();
            
            if (shopData.isEmpty()) {
                LOGGER.info("No shop data to sync (no shops found)");
                return;
            }
            
            LOGGER.info("Syncing {} shops to website...", shopData.size());
            
            // Build JSON payload
            JsonArray shopsArray = new JsonArray();
            for (ShopDataForSync data : shopData) {
                JsonObject shopObj = new JsonObject();
                shopObj.addProperty("ownerUuid", data.ownerUuid.toString());
                shopObj.addProperty("ownerName", data.ownerName);
                shopObj.addProperty("itemName", data.itemName);
                shopObj.addProperty("quantity", data.quantity);
                shopObj.addProperty("price", data.price);
                shopObj.addProperty("pricePerItem", data.pricePerItem);
                shopObj.addProperty("isBuyShop", data.isBuyShop);
                shopObj.addProperty("isAdminShop", data.isAdminShop);
                shopObj.addProperty("requiresService", data.requiresService);
                shopObj.addProperty("serviceTag", data.serviceTag != null ? data.serviceTag : "");
                shopsArray.add(shopObj);
            }
            
            JsonObject payload = new JsonObject();
            payload.add("shops", shopsArray);
            
            String jsonPayload = gson.toJson(payload);
            
            // Build HTTP request
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(shopsApiUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));
            
            // Add API key if configured
            if (apiKey != null && !apiKey.isEmpty()) {
                requestBuilder.header("X-API-Key", apiKey);
            }
            
            HttpRequest request = requestBuilder.build();
            
            // Send request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOGGER.info("Successfully synced {} shops to website", shopData.size());
            } else {
                LOGGER.warn("Shop sync failed with status code: {} - {}", response.statusCode(), response.body());
            }
            
        } catch (IOException e) {
            LOGGER.warn("Network error during shop sync: {}", e.getMessage());
        } catch (InterruptedException e) {
            LOGGER.warn("Shop sync interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.error("Unexpected error during shop sync", e);
        }
    }
    
    /**
     * Collect shop data from all worlds
     */
    private List<ShopDataForSync> collectShopData() {
        List<ShopDataForSync> shopDataList = new ArrayList<>();
        
        if (server == null) {
            return shopDataList;
        }
        
        try {
            // Iterate through all worlds
            for (ServerWorld world : server.getWorlds()) {
                ShopManager shopManager = ((ShopAccess) world).getShopManager();
                if (shopManager == null) {
                    continue;
                }
                
                String worldId = ShopManager.getWorldId(world);
                
                // Get all shops for this world
                Map<BlockPos, ShopData> worldShops = shopManager.getAllShops(worldId);
                
                // Process each shop
                for (Map.Entry<BlockPos, ShopData> entry : worldShops.entrySet()) {
                    BlockPos signPos = entry.getKey();
                    ShopData shopData = entry.getValue();
                    
                    // Use stored web sync data if available, otherwise try to read from sign
                    ShopDataForSync syncData = null;
                    
                    if (shopData.hasWebSyncData()) {
                        // Use stored data - no need to read sign
                        syncData = createShopDataForSync(world, signPos, shopData);
                    } else {
                        // Fallback: try to read from sign (for old shops that haven't migrated yet)
                        syncData = getShopDataFromSign(world, signPos, shopData);
                        // If we successfully read from sign, update the shop data for next time
                        if (syncData != null) {
                            shopData.setItemName(syncData.itemName);
                            shopData.setQuantity(syncData.quantity);
                            shopData.setServiceTag(syncData.serviceTag);
                            // Save the updated data (async, don't wait)
                            try {
                                if (shopManager != null) {
                                    shopManager.saveData(world.getServer());
                                }
                            } catch (Exception e) {
                                LOGGER.warn("Failed to save migrated shop data: {}", e.getMessage());
                            }
                        }
                    }
                    
                    if (syncData != null) {
                        shopDataList.add(syncData);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error collecting shop data", e);
        }
        
        return shopDataList;
    }
    
    /**
     * Create shop sync data from stored ShopData (preferred method)
     */
    private ShopDataForSync createShopDataForSync(ServerWorld world, BlockPos signPos, ShopData shopData) {
        try {
            String itemName = shopData.getItemName();
            int quantity = shopData.getQuantity();
            double price = shopData.getPrice();
            String serviceTag = shopData.getServiceTag();
            
            if (itemName == null || itemName.isEmpty() || quantity <= 0) {
                LOGGER.warn("Shop at {} has invalid web sync data", signPos);
                return null;
            }
            
            double pricePerItem = price / quantity;
            String ownerName = getPlayerName(shopData.getOwner());
            boolean requiresService = serviceTag != null && !serviceTag.isEmpty();
            
            return new ShopDataForSync(
                shopData.getOwner(),
                ownerName,
                itemName,
                quantity,
                price,
                pricePerItem,
                shopData.isBuyShop(),
                shopData.isAdminShop(),
                requiresService,
                serviceTag
            );
        } catch (Exception e) {
            LOGGER.error("Error creating shop sync data from stored data at {}: {}", signPos, e.getMessage());
            return null;
        }
    }
    
    /**
     * Get shop data from a sign at a specific position (fallback for old shops)
     */
    private ShopDataForSync getShopDataFromSign(ServerWorld world, BlockPos signPos, ShopData shopData) {
        try {
            BlockEntity blockEntity = world.getBlockEntity(signPos);
            if (!(blockEntity instanceof SignBlockEntity signEntity)) {
                return null;
            }
            
            // Parse sign text
            String firstLine = signEntity.getFrontText().getMessage(0, false).getString().trim();
            String secondLine = signEntity.getFrontText().getMessage(1, false).getString().trim();
            String thirdLine = signEntity.getFrontText().getMessage(2, false).getString().trim();
            String fourthLine = signEntity.getFrontText().getMessage(3, false).getString().trim();
            
            // Extract service tag if present
            String serviceTag = extractServiceTag(firstLine);
            boolean requiresService = serviceTag != null;
            
            // Parse item name, quantity, and price
            String itemName;
            int quantity;
            double price = shopData.getPrice();
            
            // Check if this is using the new format (quantity : price in fourth line)
            Matcher quantityPriceMatcher = QUANTITY_PRICE_PATTERN.matcher(fourthLine);
            if (quantityPriceMatcher.find()) {
                // New format: item name on lines 2-3, quantity:price on line 4
                itemName = secondLine;
                if (!thirdLine.isEmpty()) {
                    itemName += " " + thirdLine;
                }
                quantity = Integer.parseInt(quantityPriceMatcher.group(1));
                // Price is already set from shopData
            } else {
                // Standard format: item on line 2, quantity on line 3, price on line 4
                itemName = secondLine;
                try {
                    quantity = Integer.parseInt(thirdLine);
                } catch (NumberFormatException e) {
                    LOGGER.warn("Could not parse quantity from shop sign at {}: {}", signPos, thirdLine);
                    return null;
                }
                // Price is already set from shopData
            }
            
            if (quantity <= 0) {
                LOGGER.warn("Invalid quantity for shop at {}: {}", signPos, quantity);
                return null;
            }
            
            double pricePerItem = price / quantity;
            String ownerName = getPlayerName(shopData.getOwner());
            
            return new ShopDataForSync(
                shopData.getOwner(),
                ownerName,
                itemName,
                quantity,
                price,
                pricePerItem,
                shopData.isBuyShop(),
                shopData.isAdminShop(),
                requiresService,
                serviceTag
            );
        } catch (Exception e) {
            LOGGER.error("Error getting shop data from sign at {}: {}", signPos, e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract an optional service tag from the first line of a shop sign.
     * Supports formats like [Buy TAG] or [Sell TAG] (case-insensitive, TAG = 1-3 letters).
     * Returns the uppercased tag or null if none/invalid.
     */
    private String extractServiceTag(String firstLine) {
        if (firstLine == null) return null;
        String trimmed = firstLine.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return null;
        }
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        String[] parts = inner.split("\\s+");
        if (parts.length != 2) {
            return null;
        }
        String base = parts[0];
        String tag = parts[1];
        if (!(base.equalsIgnoreCase("buy") || base.equalsIgnoreCase("sell") || 
              base.equalsIgnoreCase("adminbuy") || base.equalsIgnoreCase("adminsell"))) {
            return null;
        }
        if (tag.length() < 1 || tag.length() > 3) {
            return null;
        }
        // Ensure tag is letters only
        if (!tag.matches("(?i)[A-Z]{1,3}")) {
            return null;
        }
        return tag.toUpperCase();
    }
    
    /**
     * Data class for shop information
     */
    private static class ShopDataForSync {
        final UUID ownerUuid;
        final String ownerName;
        final String itemName;
        final int quantity;
        final double price;
        final double pricePerItem;
        final boolean isBuyShop;
        final boolean isAdminShop;
        final boolean requiresService;
        final String serviceTag;
        
        ShopDataForSync(UUID ownerUuid, String ownerName, String itemName, int quantity, 
                       double price, double pricePerItem, boolean isBuyShop, boolean isAdminShop,
                       boolean requiresService, String serviceTag) {
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName;
            this.itemName = itemName;
            this.quantity = quantity;
            this.price = price;
            this.pricePerItem = pricePerItem;
            this.isBuyShop = isBuyShop;
            this.isAdminShop = isAdminShop;
            this.requiresService = requiresService;
            this.serviceTag = serviceTag;
        }
    }
    
    /**
     * Data class for player balance information
     */
    private static class PlayerBalanceData {
        final UUID uuid;
        final String name;
        final double balance;
        
        PlayerBalanceData(UUID uuid, String name, double balance) {
            this.uuid = uuid;
            this.name = name;
            this.balance = balance;
        }
    }
}

