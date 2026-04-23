package net.currencymod.shop;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.JsonSyntaxException;
import net.currencymod.CurrencyMod;
import net.currencymod.data.DataManager;
import net.currencymod.util.FileUtil;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ShopManager {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(BlockPos.class, new BlockPosAdapter())
            .registerTypeAdapter(UUID.class, new UUIDAdapter())
            .create();
    
    private static final String SHOPS_FILE = "currency_mod/shops.json";
    
    // Add a debugging counter for save operations
    private static int saveCounter = 0;
    private static boolean isShuttingDown = false;
    
    // Add a set to track worlds that have been saved during shutdown
    private static final Set<String> worldsSavedDuringShutdown = new HashSet<>();
    
    // Map of world dimension ID -> map of block positions -> shop data
    private final Map<String, Map<BlockPos, ShopData>> shops = new HashMap<>();
    
    // World ID for this manager
    private String managedWorldId = null;
    
    /**
     * Register a new shop
     * @param worldId The world dimension ID
     * @param pos The position of the shop sign
     * @param owner The UUID of the shop owner
     * @param price The price of items in the shop
     * @param item The item being sold (can be null for buy shops)
     * @param isBuyShop Whether this is a buy shop (true) or sell shop (false)
     * @return The created shop data
     */
    public ShopData createShop(String worldId, BlockPos pos, UUID owner, double price, ItemStack item, boolean isBuyShop) {
        return createShop(worldId, pos, owner, price, item, isBuyShop, false);
    }
    
    /**
     * Register a new shop with admin shop option
     * @param worldId The world dimension ID
     * @param pos The position of the shop sign
     * @param owner The UUID of the shop owner
     * @param price The price of items in the shop
     * @param item The item being sold (can be null for buy shops)
     * @param isBuyShop Whether this is a buy shop (true) or sell shop (false)
     * @param isAdminShop Whether this is an admin shop (true) or regular shop (false)
     * @return The created shop data
     */
    public ShopData createShop(String worldId, BlockPos pos, UUID owner, double price, ItemStack item, boolean isBuyShop, boolean isAdminShop) {
        return createShop(worldId, pos, owner, price, item, isBuyShop, isAdminShop, null, 0, null);
    }
    
    /**
     * Register a new shop with all web sync data
     * @param worldId The world dimension ID
     * @param pos The position of the shop sign
     * @param owner The UUID of the shop owner
     * @param price The price of items in the shop
     * @param item The item being sold (can be null for buy shops)
     * @param isBuyShop Whether this is a buy shop (true) or sell shop (false)
     * @param isAdminShop Whether this is an admin shop (true) or regular shop (false)
     * @param itemName The name of the item (for web sync)
     * @param quantity The quantity of items (for web sync)
     * @param serviceTag The service tag if required (for web sync, can be null)
     * @return The created shop data
     */
    public ShopData createShop(String worldId, BlockPos pos, UUID owner, double price, ItemStack item, 
                              boolean isBuyShop, boolean isAdminShop, String itemName, int quantity, String serviceTag) {
        // Create the shop data with web sync information
        ShopData shopData = new ShopData(owner, price, item, isBuyShop, isAdminShop, itemName, quantity, serviceTag);
        
        // Get or create the map for this world
        Map<BlockPos, ShopData> worldShops = shops.computeIfAbsent(worldId, k -> new HashMap<>());
        
        // Add the shop to the map
        worldShops.put(pos, shopData);
        
        return shopData;
    }
    
    /**
     * Get shop data at a position
     * @param worldId The world dimension ID
     * @param pos The position of the shop sign
     * @return The shop data, or null if no shop exists
     */
    public ShopData getShop(String worldId, BlockPos pos) {
        Map<BlockPos, ShopData> worldShops = shops.get(worldId);
        if (worldShops == null) {
            return null;
        }
        
        return worldShops.get(pos);
    }
    
    /**
     * Remove a shop
     * @param worldId The world dimension ID
     * @param pos The position of the shop sign
     */
    public void removeShop(String worldId, BlockPos pos) {
        Map<BlockPos, ShopData> worldShops = shops.get(worldId);
        if (worldShops != null) {
            worldShops.remove(pos);
        }
    }
    
    /**
     * Check if a player owns a shop
     * @param worldId The world dimension ID
     * @param pos The position of the shop sign
     * @param playerUuid The UUID of the player to check
     * @return True if the player owns the shop, false otherwise
     */
    public boolean isShopOwner(String worldId, BlockPos pos, UUID playerUuid) {
        ShopData shopData = getShop(worldId, pos);
        return shopData != null && shopData.getOwner().equals(playerUuid);
    }
    
    /**
     * Get the count of shops in a specific world
     * @param worldId The world dimension ID
     * @return The number of shops in the world
     */
    public int getShopCount(String worldId) {
        Map<BlockPos, ShopData> worldShops = shops.get(worldId);
        if (worldShops == null) {
            return 0;
        }
        return worldShops.size();
    }
    
    /**
     * Get all shops for a specific world
     * @param worldId The world dimension ID
     * @return Map of block positions to shop data, or empty map if no shops
     */
    public Map<BlockPos, ShopData> getAllShops(String worldId) {
        Map<BlockPos, ShopData> worldShops = shops.get(worldId);
        if (worldShops == null) {
            return new HashMap<>();
        }
        return new HashMap<>(worldShops); // Return a copy to prevent external modification
    }
    
    /**
     * Get all shops across all worlds
     * @return Map of world ID to map of block positions to shop data
     */
    public Map<String, Map<BlockPos, ShopData>> getAllShops() {
        Map<String, Map<BlockPos, ShopData>> result = new HashMap<>();
        for (Map.Entry<String, Map<BlockPos, ShopData>> entry : shops.entrySet()) {
            result.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return result;
    }
    
    /**
     * Attempt to migrate shop data from sign if web sync data is missing
     * This is called when a chunk loads to populate missing data for old shops
     * @param world The world containing the shop
     * @param pos The position of the shop sign
     * @return True if migration was successful, false otherwise
     */
    public boolean migrateShopDataIfNeeded(ServerWorld world, BlockPos pos) {
        String worldId = ShopManager.getWorldId(world);
        ShopData shopData = getShop(worldId, pos);
        
        if (shopData == null) {
            return false;
        }
        
        // If shop already has web sync data, no migration needed
        if (shopData.hasWebSyncData()) {
            return false;
        }
        
        // Try to read from sign
        try {
            if (!world.isChunkLoaded(pos)) {
                return false; // Chunk not loaded, can't migrate yet
            }
            
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (!(blockEntity instanceof SignBlockEntity signEntity)) {
                return false; // Sign doesn't exist or was removed
            }
            
            // Parse sign text
            String firstLine = signEntity.getFrontText().getMessage(0, false).getString().trim();
            String secondLine = signEntity.getFrontText().getMessage(1, false).getString().trim();
            String thirdLine = signEntity.getFrontText().getMessage(2, false).getString().trim();
            String fourthLine = signEntity.getFrontText().getMessage(3, false).getString().trim();
            
            // Extract service tag
            String serviceTag = extractServiceTagFromLine(firstLine);
            
            // Parse item name, quantity
            String itemName;
            int quantity;
            
            // Check if this is using the new format (quantity : price in fourth line)
            java.util.regex.Pattern QUANTITY_PRICE_PATTERN = java.util.regex.Pattern.compile("(\\d+)\\s*:\\s*\\$?(\\d+(?:\\.\\d+)?)");
            java.util.regex.Matcher quantityPriceMatcher = QUANTITY_PRICE_PATTERN.matcher(fourthLine);
            
            if (quantityPriceMatcher.find()) {
                // New format: item name on lines 2-3, quantity:price on line 4
                itemName = secondLine;
                if (!thirdLine.isEmpty()) {
                    itemName += " " + thirdLine;
                }
                quantity = Integer.parseInt(quantityPriceMatcher.group(1));
            } else {
                // Standard format: item on line 2, quantity on line 3
                itemName = secondLine;
                try {
                    quantity = Integer.parseInt(thirdLine);
                } catch (NumberFormatException e) {
                    CurrencyMod.LOGGER.warn("Could not parse quantity for migration at {}: {}", pos, thirdLine);
                    return false;
                }
            }
            
            if (quantity <= 0 || itemName == null || itemName.isEmpty()) {
                return false;
            }
            
            // Update shop data
            shopData.setItemName(itemName);
            shopData.setQuantity(quantity);
            shopData.setServiceTag(serviceTag);
            
            CurrencyMod.LOGGER.info("Migrated web sync data for shop at {}: {} x{}", pos, itemName, quantity);
            
            // Save the updated data
            saveData(world.getServer());
            
            return true;
        } catch (Exception e) {
            CurrencyMod.LOGGER.error("Error migrating shop data at {}: {}", pos, e.getMessage());
            return false;
        }
    }
    
    /**
     * Extract service tag from first line of shop sign
     */
    private String extractServiceTagFromLine(String firstLine) {
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
        if (!tag.matches("(?i)[A-Z]{1,3}")) {
            return null;
        }
        return tag.toUpperCase();
    }
    
    /**
     * Reset all static tracking variables
     * This should be called when server starts
     */
    public static void resetTracking() {
        saveCounter = 0;
        isShuttingDown = false;
        worldsSavedDuringShutdown.clear();
    }
    
    /**
     * Load shop data from file
     * @param server The Minecraft server instance
     */
    public void loadData(MinecraftServer server) {
        // Reset tracking on first load
        synchronized (worldsSavedDuringShutdown) {
            if (saveCounter == 0) {
                resetTracking();
            }
        }
        
        if (server == null) {
            CurrencyMod.LOGGER.error("Cannot load shop data: server is null");
            return;
        }
        
        try {
            // Get the file using FileUtil
            File shopsFile = FileUtil.getServerFile(server, SHOPS_FILE);
            
            // Check if the file is accessible
            if (!FileUtil.isFileAccessible(shopsFile, false)) {
                CurrencyMod.LOGGER.info("No shops file found, starting with empty shop data");
                return;
            }
            
            // Read the file content
            String content = FileUtil.safeReadFromFile(shopsFile);
            
            if (content == null || content.trim().isEmpty()) {
                CurrencyMod.LOGGER.info("Shops file is empty, starting with empty shop data");
                return;
            }
            
            JsonObject rootObject = GSON.fromJson(content, JsonObject.class);
            
            for (Map.Entry<String, JsonElement> worldEntry : rootObject.entrySet()) {
                String worldId = worldEntry.getKey();
                JsonObject worldObject = worldEntry.getValue().getAsJsonObject();
                Map<BlockPos, ShopData> worldShops = new HashMap<>();
                
                for (Map.Entry<String, JsonElement> shopEntry : worldObject.entrySet()) {
                    String posKey = shopEntry.getKey();
                    JsonObject shopObject = shopEntry.getValue().getAsJsonObject();
                    
                    try {
                        // Extract coordinates from string like "(x,y,z)"
                        String[] parts = posKey.substring(1, posKey.length() - 1).split(",");
                        int x = Integer.parseInt(parts[0]);
                        int y = Integer.parseInt(parts[1]);
                        int z = Integer.parseInt(parts[2]);
                        BlockPos pos = new BlockPos(x, y, z);
                        
                        // Load shop data
                        UUID owner = UUID.fromString(shopObject.get("owner").getAsString());
                        double price = shopObject.get("price").getAsDouble();
                        boolean isBuyShop = shopObject.get("isBuyShop").getAsBoolean();
                        
                        // Load isAdminShop flag if present, default to false if not
                        boolean isAdminShop = shopObject.has("isAdminShop") ? 
                            shopObject.get("isAdminShop").getAsBoolean() : false;
                        
                        // Load web sync data if present (for new format)
                        String itemName = shopObject.has("itemName") ? 
                            shopObject.get("itemName").getAsString() : null;
                        int quantity = shopObject.has("quantity") ? 
                            shopObject.get("quantity").getAsInt() : 0;
                        String serviceTag = shopObject.has("serviceTag") && !shopObject.get("serviceTag").isJsonNull() ? 
                            shopObject.get("serviceTag").getAsString() : null;
                        
                        // Create shop data with web sync data if available
                        ShopData shopData = new ShopData(owner, price, null, isBuyShop, isAdminShop, 
                                                         itemName, quantity, serviceTag);
                        worldShops.put(pos, shopData);
                        
                        // If web sync data is missing, mark for migration (will be populated when chunk loads)
                        if (!shopData.hasWebSyncData()) {
                            CurrencyMod.LOGGER.debug("Shop at {} loaded without web sync data, will migrate when chunk loads", pos);
                        }
                    } catch (Exception e) {
                        CurrencyMod.LOGGER.error("Failed to load shop at {}: {}", posKey, e.getMessage());
                    }
                }
                
                shops.put(worldId, worldShops);
                CurrencyMod.LOGGER.info("Loaded {} shops for world {}", worldShops.size(), worldId);
            }
            
            // Count total shops
            int totalShops = shops.values().stream().mapToInt(Map::size).sum();
            CurrencyMod.LOGGER.info("Loaded {} shops across {} worlds", totalShops, shops.size());
        } catch (Exception e) {
            CurrencyMod.LOGGER.error("Failed to load shop data", e);
        }
    }
    
    /**
     * Save shop data to file
     * @param server The Minecraft server instance
     */
    public void saveData(MinecraftServer server) {
        // Increment counter
        saveCounter++;
        
        if (server == null) {
            CurrencyMod.LOGGER.error("Cannot save shop data: server is null (attempt #{})", saveCounter);
            return;
        }
        
        // Ensure we have a world ID
        if (managedWorldId == null) {
            for (ServerWorld world : server.getWorlds()) {
                ShopManager worldManager = ((ShopAccess) world).getShopManager();
                if (worldManager == this) {
                    managedWorldId = getWorldId(world);
                    break;
                }
            }
        }
        
        // Track call stack to help identify where saves are coming from
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String caller = "unknown";
        if (stackTrace.length > 2) {
            caller = stackTrace[2].getClassName() + "." + stackTrace[2].getMethodName() + ":" + stackTrace[2].getLineNumber();
        }
        
        // In shutdown mode, prevent duplicate saves for the same world
        DataManager dataManager = DataManager.getInstance();
        if (dataManager.isShuttingDown() && managedWorldId != null) {
            synchronized (worldsSavedDuringShutdown) {
                if (worldsSavedDuringShutdown.contains(managedWorldId)) {
                    CurrencyMod.LOGGER.info("Shop save #{} from {} skipped - world {} already saved during shutdown", 
                        saveCounter, caller, managedWorldId);
                    return;
                }
                worldsSavedDuringShutdown.add(managedWorldId);
            }
            CurrencyMod.LOGGER.info("Shop save #{} from {} is first save for world {} during shutdown", 
                saveCounter, caller, managedWorldId);
        }
        
        CurrencyMod.LOGGER.info("Shop save #{} requested by {}", saveCounter, caller);
        
        // Check for shutdown context
        if (dataManager.isShuttingDown() && !isShuttingDown) {
            isShuttingDown = true;
            CurrencyMod.LOGGER.info("First shop save during shutdown detected");
        }
        
        try {
            // Get the file using FileUtil
            File shopsFile = FileUtil.getServerFile(server, SHOPS_FILE);
            
            // Initialize root object
            JsonObject rootObject = new JsonObject();
            
            // Read existing file content if it exists
            if (shopsFile.exists() && shopsFile.length() > 0) {
                try {
                    String existingContent = FileUtil.safeReadFromFile(shopsFile);
                    if (existingContent != null && !existingContent.trim().isEmpty()) {
                        try {
                            rootObject = GSON.fromJson(existingContent, JsonObject.class);
                            CurrencyMod.LOGGER.info("Read existing shops file with {} world entries", rootObject.size());
                        } catch (JsonSyntaxException e) {
                            CurrencyMod.LOGGER.error("Error parsing existing shops file, will overwrite: {}", e.getMessage());
                            // Continue with empty rootObject
                        }
                    }
                } catch (Exception e) {
                    CurrencyMod.LOGGER.error("Error reading existing shops file: {}", e.getMessage());
                    // Continue with empty rootObject
                }
            }
            
            // Add/Update our world's shops in the root object
            if (managedWorldId != null) {
                Map<BlockPos, ShopData> worldShops = shops.get(managedWorldId);
                if (worldShops != null && !worldShops.isEmpty()) {
                    JsonObject worldObject = new JsonObject();
                    
                    for (Map.Entry<BlockPos, ShopData> shopEntry : worldShops.entrySet()) {
                        BlockPos pos = shopEntry.getKey();
                        ShopData shopData = shopEntry.getValue();
                        
                        JsonObject shopObject = new JsonObject();
                        shopObject.addProperty("owner", shopData.getOwner().toString());
                        shopObject.addProperty("price", shopData.getPrice());
                        shopObject.addProperty("isBuyShop", shopData.isBuyShop());
                        shopObject.addProperty("isAdminShop", shopData.isAdminShop());
                        
                        // Save web sync data if available
                        if (shopData.getItemName() != null && !shopData.getItemName().isEmpty()) {
                            shopObject.addProperty("itemName", shopData.getItemName());
                        }
                        if (shopData.getQuantity() > 0) {
                            shopObject.addProperty("quantity", shopData.getQuantity());
                        }
                        if (shopData.getServiceTag() != null && !shopData.getServiceTag().isEmpty()) {
                            shopObject.addProperty("serviceTag", shopData.getServiceTag());
                        }
                        
                        // Use BlockPos as string key
                        worldObject.add("(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")", shopObject);
                    }
                    
                    // Add/replace this world's data in the root object
                    rootObject.add(managedWorldId, worldObject);
                    CurrencyMod.LOGGER.info("Added/updated {} shops for world {}", worldShops.size(), managedWorldId);
                } else {
                    CurrencyMod.LOGGER.info("No shops to save for world {}", managedWorldId);
                }
            } else {
                // If we don't have a managed world ID, save all shops
                for (Map.Entry<String, Map<BlockPos, ShopData>> worldEntry : shops.entrySet()) {
                    String worldId = worldEntry.getKey();
                    Map<BlockPos, ShopData> worldShops = worldEntry.getValue();
                    JsonObject worldObject = new JsonObject();
                    
                    for (Map.Entry<BlockPos, ShopData> shopEntry : worldShops.entrySet()) {
                        BlockPos pos = shopEntry.getKey();
                        ShopData shopData = shopEntry.getValue();
                        
                        JsonObject shopObject = new JsonObject();
                        shopObject.addProperty("owner", shopData.getOwner().toString());
                        shopObject.addProperty("price", shopData.getPrice());
                        shopObject.addProperty("isBuyShop", shopData.isBuyShop());
                        shopObject.addProperty("isAdminShop", shopData.isAdminShop());
                        
                        // Save web sync data if available
                        if (shopData.getItemName() != null && !shopData.getItemName().isEmpty()) {
                            shopObject.addProperty("itemName", shopData.getItemName());
                        }
                        if (shopData.getQuantity() > 0) {
                            shopObject.addProperty("quantity", shopData.getQuantity());
                        }
                        if (shopData.getServiceTag() != null && !shopData.getServiceTag().isEmpty()) {
                            shopObject.addProperty("serviceTag", shopData.getServiceTag());
                        }
                        
                        // Use BlockPos as string key
                        worldObject.add("(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")", shopObject);
                    }
                    
                    // Add/replace this world's data in the root object
                    rootObject.add(worldId, worldObject);
                }
                CurrencyMod.LOGGER.info("Added/updated all shops for {} worlds", shops.size());
            }
            
            // Convert to JSON and write to file using FileUtil
            String jsonContent = GSON.toJson(rootObject);
            boolean success = FileUtil.safeWriteToFile(server, shopsFile, jsonContent);
            
            if (success) {
                // Count total shops across all worlds
                int totalShops = countShopsInRootObject(rootObject);
                CurrencyMod.LOGGER.info("Saved {} shops across {} worlds", totalShops, rootObject.size());
            } else {
                CurrencyMod.LOGGER.error("Failed to save shop data to {}", shopsFile);
            }
        } catch (Exception e) {
            CurrencyMod.LOGGER.error("Failed to save shop data", e);
        }
    }
    
    /**
     * Count the total number of shops in a root JSON object
     * @param rootObject The root JSON object containing world shop data
     * @return The total number of shops
     */
    private int countShopsInRootObject(JsonObject rootObject) {
        int total = 0;
        for (String worldId : rootObject.keySet()) {
            JsonObject worldObject = rootObject.getAsJsonObject(worldId);
            total += worldObject.size();
        }
        return total;
    }
    
    /**
     * Get the world dimension ID from a World instance
     * @param world The world
     * @return The dimension ID as a string
     */
    public static String getWorldId(World world) {
        return world.getRegistryKey().getValue().toString();
    }
    
    /**
     * Type adapter for BlockPos to handle JSON serialization/deserialization
     */
    private static class BlockPosAdapter implements JsonSerializer<BlockPos>, JsonDeserializer<BlockPos> {
        @Override
        public JsonElement serialize(BlockPos src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("x", src.getX());
            obj.addProperty("y", src.getY());
            obj.addProperty("z", src.getZ());
            return obj;
        }

        @Override
        public BlockPos deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            int x = obj.get("x").getAsInt();
            int y = obj.get("y").getAsInt();
            int z = obj.get("z").getAsInt();
            return new BlockPos(x, y, z);
        }
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