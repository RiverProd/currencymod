package net.currencymod.jobs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.currencymod.CurrencyMod;
import net.currencymod.ui.MarketplaceScreenHandler;
import net.currencymod.util.FileUtil;
import net.currencymod.util.ItemStackAdapter;
import net.currencymod.util.UUIDAdapter;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;

/**
 * Manages the global marketplace for buying items from completed jobs.
 */
public class MarketplaceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/MarketplaceManager");
    private static final String MARKETPLACE_FILE = "currency_mod/marketplace.json";
    private static final Type MARKETPLACE_ITEM_LIST_TYPE = new TypeToken<List<MarketplaceItem>>() {}.getType();
    private static final int MAX_INVENTORY_SIZE = 27; // 3 rows of 9 slots
    
    // Create a gson instance with our custom adapters
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(ItemStack.class, new ItemStackAdapter())
        .registerTypeAdapter(UUID.class, new UUIDAdapter())
        .create();
    
    private static MarketplaceManager instance;
    private final List<MarketplaceItem> marketplaceItems = new ArrayList<>();
    
    // Map to track which items players have clicked and are awaiting confirmation
    // The key is a unique identifier combining player UUID and item index
    private final Map<String, Integer> pendingConfirmations = new HashMap<>();
    
    /**
     * Create a unique key for the confirmation map based on player and item
     */
    private String createConfirmationKey(UUID playerUuid, int itemIndex) {
        return playerUuid.toString() + "_" + itemIndex;
    }
    
    /**
     * Get or create the MarketplaceManager instance.
     * 
     * @return The MarketplaceManager instance
     */
    public static MarketplaceManager getInstance() {
        if (instance == null) {
            instance = new MarketplaceManager();
        }
        return instance;
    }
    
    /**
     * Private constructor to enforce singleton pattern.
     */
    private MarketplaceManager() {
        // Do not load in constructor - this will be called explicitly by DataManager
        // or when needed through the no-arg load() method
        LOGGER.info("MarketplaceManager initialized - data will be loaded when server is available");
    }
    
    /**
     * Get the list of all marketplace items
     * @return An unmodifiable list of marketplace items
     */
    public List<MarketplaceItem> getMarketplaceItems() {
        return Collections.unmodifiableList(marketplaceItems);
    }
    
    /**
     * Load marketplace items from file without server context.
     * This is a fallback method and may not work in all contexts.
     */
    public void load() {
        LOGGER.warn("Loading marketplace without server context - this may not work correctly");
        // Get the server from CurrencyMod if available
        MinecraftServer server = CurrencyMod.getServer();
        if (server != null) {
            load(server);
        } else {
            LOGGER.error("Failed to load marketplace: No server available");
            marketplaceItems.clear();
        }
    }
    
    /**
     * Save marketplace items to file without server context.
     * This is a fallback method and may not work in all contexts.
     */
    public void save() {
        LOGGER.warn("Saving marketplace without server context - this may not work correctly");
        // Get the server from CurrencyMod if available
        MinecraftServer server = CurrencyMod.getServer();
        if (server != null) {
            save(server);
        } else {
            LOGGER.error("Failed to save marketplace: No server available");
        }
    }
    
    /**
     * Add an item to the marketplace.
     * 
     * @param item The item to add
     * @param quantity The quantity of the item
     * @param originalPrice The original price paid for the item
     */
    public void addItem(Item item, int quantity, int originalPrice) {
        if (quantity <= 0 || originalPrice <= 0) {
            LOGGER.warn("Invalid marketplace item: Quantity and price must be positive");
            return;
        }
        
        // Create a marketplace item
        ItemStack itemStack = new ItemStack(item, quantity);
        int sellPrice = (int)(originalPrice * 2.5); // 2.5x the original price
        
        MarketplaceItem marketplaceItem = new MarketplaceItem(
            UUID.randomUUID(),
            itemStack,
            sellPrice,
            System.currentTimeMillis()
        );
        
        // Add to the list
        marketplaceItems.add(marketplaceItem);
        
        // If we have too many items, remove the oldest ones
        while (marketplaceItems.size() > MAX_INVENTORY_SIZE) {
            marketplaceItems.remove(0); // Remove the oldest item
        }
        
        // Save the marketplace
        save();
        
        LOGGER.info("Added {} x{} to marketplace for ${}", 
            item.getName().getString(), quantity, sellPrice);
    }
    
    /**
     * Open the marketplace GUI for a player.
     * 
     * @param player The player
     */
    public void openMarketplace(ServerPlayerEntity player) {
        // Clear any previous confirmations for this player
        String playerPrefix = player.getUuid().toString() + "_";
        pendingConfirmations.entrySet().removeIf(entry -> entry.getKey().startsWith(playerPrefix));
        
        // Create a screen handler factory
        NamedScreenHandlerFactory factory = new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Marketplace").formatted(Formatting.DARK_GREEN);
            }
            
            @Override
            public ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, net.minecraft.entity.player.PlayerEntity player) {
                // Create an inventory with marketplace items
                SimpleInventory inventory = new SimpleInventory(MAX_INVENTORY_SIZE);
                
                // Fill the inventory with marketplace items
                for (int i = 0; i < marketplaceItems.size() && i < MAX_INVENTORY_SIZE; i++) {
                    MarketplaceItem marketplaceItem = marketplaceItems.get(i);
                    
                    // Create a copy of the item
                    ItemStack itemStack = marketplaceItem.getItemStack().copy();
                    
                    // Get the price for display
                    int price = marketplaceItem.getPrice();
                    
                    // Instead of trying to modify the item name, display price info when player clicks
                    // and in chat when previewing the item
                    
                    // Put the item in the slot
                    inventory.setStack(i, itemStack);
                    
                    LOGGER.debug("Added marketplace item: {} for ${}", 
                        itemStack.getName().getString(), price);
                }
                
                // Create a new MarketplaceScreenHandler with the inventory
                return new MarketplaceScreenHandler(syncId, playerInventory, inventory) {
                    @Override
                    public boolean onButtonClick(net.minecraft.entity.player.PlayerEntity player, int id) {
                        if (player instanceof ServerPlayerEntity serverPlayer) {
                            // Now the marketplace item index is the same as the slot index
                            int marketplaceItemIndex = id;
                            
                            // Check if slot index is within the marketplace items range
                            if (marketplaceItemIndex >= 0 && marketplaceItemIndex < marketplaceItems.size()) {
                                // Process the click (either preview or purchase)
                                boolean success = handleSlotClick(serverPlayer, marketplaceItemIndex);
                                return true; // We've handled the click either way
                            }
                        }
                        return super.onButtonClick(player, id);
                    }
                };
            }
        };
        
        // Open the GUI for the player
        player.openHandledScreen(factory);
    }
    
    /**
     * Handle a player clicking on an item in the marketplace GUI.
     * Implements a two-step confirmation process:
     * 1. First click: Show item details
     * 2. Second click: Process the purchase
     * 
     * @param player The player
     * @param slotIndex The index of the slot that was clicked
     * @return True if the action was successful, false otherwise
     */
    public boolean handleSlotClick(ServerPlayerEntity player, int slotIndex) {
        // In the new layout, the marketplace item index is the same as the slot index
        int marketplaceItemIndex = slotIndex;
        
        // Check if the slot index is within the marketplace items range
        if (marketplaceItemIndex < 0 || marketplaceItemIndex >= marketplaceItems.size()) {
            return false;
        }
        
        // Get the marketplace item directly from our list
        MarketplaceItem marketplaceItem = marketplaceItems.get(marketplaceItemIndex);
        
        // Create a unique key for this player and item combination
        String confirmationKey = createConfirmationKey(player.getUuid(), marketplaceItemIndex);
        
        // Check if this is a confirmation click
        if (pendingConfirmations.containsKey(confirmationKey)) {
            // This is a confirmation click, so process the purchase
            return processPurchase(player, marketplaceItem);
        } else {
            // This is the first click, so show the preview
            sendItemPreview(player, marketplaceItem);
            
            // Add to pending confirmations
            pendingConfirmations.put(confirmationKey, marketplaceItemIndex);
            
            // Don't process the purchase yet
            return true;
        }
    }
    
    /**
     * Process the actual purchase of a marketplace item.
     * 
     * @param player The player making the purchase
     * @param marketplaceItem The item being purchased
     * @return True if the purchase was successful, false otherwise
     */
    private boolean processPurchase(ServerPlayerEntity player, MarketplaceItem marketplaceItem) {
        int price = marketplaceItem.getPrice();
        
        try {
            // Check if player has enough money
            double playerBalance = CurrencyMod.getEconomyManager().getBalance(player.getUuid());
            if (playerBalance < price) {
                player.sendMessage(Text.literal("You don't have enough money to buy this item. You need ")
                    .append(Text.literal("$" + price).formatted(Formatting.RED))
                    .append(Text.literal(" but you only have "))
                    .append(Text.literal("$" + (int)playerBalance).formatted(Formatting.RED))
                    .append(Text.literal(".")));
                return false;
            }
            
            // Check if player has inventory space
            if (!hasInventorySpace(player, marketplaceItem.getItemStack())) {
                player.sendMessage(Text.literal("You don't have enough inventory space to buy this item.")
                    .formatted(Formatting.RED));
                return false;
            }
            
            // Make the purchase
            CurrencyMod.getEconomyManager().removeBalance(player.getUuid(), price);
            
            // Give the item to the player - create a clean copy
            ItemStack purchasedStack = marketplaceItem.getItemStack().copy();
            
            // Store item details for the message before giving it to the player
            int itemCount = purchasedStack.getCount();
            String itemName = purchasedStack.getName().getString();
            
            player.giveItemStack(purchasedStack);
            
            // Remove the item from the marketplace
            marketplaceItems.remove(marketplaceItem);
            save();
            
            // Format a nicer purchase confirmation message
            Text itemText = Text.literal(itemCount + "x ").formatted(Formatting.GRAY)
                .append(Text.literal(itemName).formatted(Formatting.YELLOW))
                .append(Text.literal(" for ").formatted(Formatting.WHITE))
                .append(Text.literal("$" + price).formatted(Formatting.GREEN));
            
            // Notify the player
            player.sendMessage(Text.literal("Purchase successful! You bought ").formatted(Formatting.GREEN)
                .append(itemText)
                .append(Text.literal(".")));
            
            // Clear all pending confirmations for this player
            String playerPrefix = player.getUuid().toString() + "_";
            pendingConfirmations.entrySet().removeIf(entry -> entry.getKey().startsWith(playerPrefix));
            
            // Schedule reopening the marketplace on the next tick
            player.getServer().execute(() -> {
                openMarketplace(player);
            });
            
            return true;
        } catch (Exception e) {
            LOGGER.error("Error handling marketplace purchase", e);
            player.sendMessage(Text.literal("An error occurred while processing your purchase.")
                .formatted(Formatting.RED));
            return false;
        }
    }
    
    /**
     * Check if a player has inventory space for an item.
     * 
     * @param player The player
     * @param itemStack The item stack
     * @return True if the player has space, false otherwise
     */
    private boolean hasInventorySpace(ServerPlayerEntity player, ItemStack itemStack) {
        // Try to find an empty slot or a slot with the same item
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            
            if (stack.isEmpty()) {
                return true; // Found an empty slot
            }
            
            if (stack.getItem() == itemStack.getItem() && 
                    stack.getCount() + itemStack.getCount() <= stack.getMaxCount()) {
                return true; // Found a slot with the same item and enough space
            }
        }
        
        return false;
    }
    
    /**
     * Load marketplace items from file.
     * 
     * @param server The Minecraft server instance
     */
    public void load(MinecraftServer server) {
        if (server == null) {
            LOGGER.error("Cannot load marketplace items: server is null");
            return;
        }
        
        try {
            // Get the file using FileUtil
            File marketplaceFile = FileUtil.getServerFile(server, MARKETPLACE_FILE);
            
            // Check if the file is accessible
            if (!FileUtil.isFileAccessible(marketplaceFile, false)) {
                LOGGER.warn("Marketplace file is not accessible for reading: {}", marketplaceFile);
                return;
            }
            
            // Read the file content
            String content = FileUtil.safeReadFromFile(marketplaceFile);
            
            if (content != null && !content.trim().isEmpty()) {
                List<MarketplaceItem> loadedItems = GSON.fromJson(content, MARKETPLACE_ITEM_LIST_TYPE);
                if (loadedItems != null) {
                    marketplaceItems.clear();
                    marketplaceItems.addAll(loadedItems);
                    LOGGER.info("Loaded {} marketplace items from {}", marketplaceItems.size(), marketplaceFile);
                    
                    // Log loaded items for debugging
                    for (MarketplaceItem item : marketplaceItems) {
                        ItemStack stack = item.getItemStack();
                        LOGGER.debug("Loaded marketplace item: {} x{} for ${}", 
                            stack.getName().getString(), stack.getCount(), item.getPrice());
                    }
                } else {
                    LOGGER.warn("Failed to parse marketplace items from file, starting with empty marketplace");
                    marketplaceItems.clear();
                }
            } else {
                LOGGER.info("No marketplace file found or file is empty, starting with empty marketplace");
                marketplaceItems.clear();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load marketplace items", e);
            marketplaceItems.clear();
        }
    }
    
    /**
     * Save marketplace items to file.
     * 
     * @param server The Minecraft server instance
     */
    public void save(MinecraftServer server) {
        if (server == null) {
            LOGGER.error("Cannot save marketplace items: server is null");
            return;
        }
        
        try {
            // Get the file using FileUtil
            File marketplaceFile = FileUtil.getServerFile(server, MARKETPLACE_FILE);
            
            // Convert the marketplace items to JSON
            String jsonContent = GSON.toJson(marketplaceItems, MARKETPLACE_ITEM_LIST_TYPE);
            
            // Write the content to the file
            boolean success = FileUtil.safeWriteToFile(server, marketplaceFile, jsonContent);
            
            if (success) {
                LOGGER.info("Saved {} marketplace items to {}", marketplaceItems.size(), marketplaceFile);
                
                // Log saved items for debugging
                for (MarketplaceItem item : marketplaceItems) {
                    ItemStack stack = item.getItemStack();
                    LOGGER.debug("Saved marketplace item: {} x{} for ${}", 
                        stack.getName().getString(), stack.getCount(), item.getPrice());
                }
            } else {
                LOGGER.error("Failed to save marketplace items to {}", marketplaceFile);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save marketplace items", e);
        }
    }
    
    /**
     * Send a marketplace item preview to a player.
     * 
     * @param player The player to send the preview to
     * @param marketplaceItem The item to preview
     */
    private void sendItemPreview(ServerPlayerEntity player, MarketplaceItem marketplaceItem) {
        ItemStack stack = marketplaceItem.getItemStack();
        int price = marketplaceItem.getPrice();
        String itemName = stack.getName().getString();
        int count = stack.getCount();
        
        // Create a fancy item preview with hover effect
        Text itemNameText = Text.literal(count + "x ").formatted(Formatting.GRAY)
            .append(Text.literal(itemName).formatted(Formatting.YELLOW, Formatting.BOLD));
            
        Text priceText = Text.literal("$" + price).formatted(Formatting.GREEN, Formatting.BOLD);
        
        // Create a divider
        Text divider = Text.literal("------------------------").formatted(Formatting.GOLD);
        
        // Build the complete message
        player.sendMessage(divider);
        player.sendMessage(Text.literal("PURCHASE CONFIRMATION").formatted(Formatting.GOLD, Formatting.BOLD));
        player.sendMessage(Text.literal("Item: ").formatted(Formatting.GOLD).append(itemNameText));
        player.sendMessage(Text.literal("Price: ").formatted(Formatting.GOLD).append(priceText));
        player.sendMessage(Text.literal("Click again to confirm purchase").formatted(Formatting.GREEN, Formatting.ITALIC));
        player.sendMessage(divider);
    }
    
    /**
     * A class representing an item for sale in the marketplace.
     */
    public static class MarketplaceItem {
        private final UUID id;
        private final ItemStack itemStack;
        private final int price;
        private final long timestamp;
        
        /**
         * Creates a new marketplace item.
         * 
         * @param id The unique ID of the item
         * @param itemStack The item stack
         * @param price The price of the item
         * @param timestamp The timestamp when the item was added
         */
        public MarketplaceItem(UUID id, ItemStack itemStack, int price, long timestamp) {
            this.id = id;
            this.itemStack = itemStack;
            this.price = price;
            this.timestamp = timestamp;
        }
        
        /**
         * Gets the unique ID of the item.
         * 
         * @return The unique ID
         */
        public UUID getId() {
            return id;
        }
        
        /**
         * Gets the item stack.
         * 
         * @return The item stack
         */
        public ItemStack getItemStack() {
            return itemStack;
        }
        
        /**
         * Gets the price of the item.
         * 
         * @return The price
         */
        public int getPrice() {
            return price;
        }
        
        /**
         * Gets the timestamp when the item was added.
         * 
         * @return The timestamp
         */
        public long getTimestamp() {
            return timestamp;
        }
    }
} 