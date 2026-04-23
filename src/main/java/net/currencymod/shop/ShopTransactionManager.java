package net.currencymod.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.currencymod.CurrencyMod;
import net.currencymod.util.FileUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages shop transactions and provides notification/summary services
 */
public class ShopTransactionManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String TRANSACTIONS_FILE = "currency_mod/shop_transactions.json";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM/dd HH:mm");
    private static final ShopTransactionManager INSTANCE = new ShopTransactionManager();
    
    // Map of player UUID -> last login timestamp
    private final Map<UUID, Long> playerLastLogin = new ConcurrentHashMap<>();
    
    // Map of player UUID -> list of transaction records involving their shops 
    // (where they are the owner)
    private final Map<UUID, List<TransactionRecord>> playerTransactions = new ConcurrentHashMap<>();
    
    /**
     * Private constructor for singleton pattern
     */
    private ShopTransactionManager() {}
    
    /**
     * Get the singleton instance
     */
    public static ShopTransactionManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Record a transaction to be notified/summarized
     */
    public void recordTransaction(TransactionRecord transaction) {
        // If the owner is online, send them a notification (unless they made the transaction)
        boolean ownerWasOnline = notifyOwnerIfOnline(transaction);
        
        // Only store transactions where the owner was offline
        // This prevents duplicate messages when they log back in
        if (!ownerWasOnline) {
            // Get or create the list of transactions for this shop owner
            List<TransactionRecord> ownerTransactions = playerTransactions.computeIfAbsent(
                transaction.shopOwnerUuid, k -> new ArrayList<>());
            
            // Add the transaction to the list
            ownerTransactions.add(transaction);
        }
        
        // Log for debugging
        CurrencyMod.LOGGER.info("Recorded transaction: {} (owner was online: {})", transaction, ownerWasOnline);
    }
    
    /**
     * Send a notification to the shop owner if they're online
     * @return true if the owner was online and received the notification, false otherwise
     */
    private boolean notifyOwnerIfOnline(TransactionRecord transaction) {
        if (transaction.playerUuid.equals(transaction.shopOwnerUuid)) {
            // Skip notification if player is transacting with their own shop
            return false;
        }
        
        MinecraftServer server = CurrencyMod.getServer();
        if (server == null) return false;
        
        // Try to find the owner online
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(transaction.shopOwnerUuid);
        if (owner != null) {
            // Owner is online, send formatted notification
            Formatting color = transaction.isBuyShop ? Formatting.GREEN : Formatting.BLUE;
            String action = transaction.isBuyShop ? "bought" : "sold";
            String direction = transaction.isBuyShop ? "from" : "to";
            
            // Create a single-line transaction message with all details
            Text message = Text.literal("$ ").formatted(Formatting.GOLD)
                .append(Text.literal(transaction.playerName + " "))
                .append(Text.literal(action + " ").formatted(color))
                .append(Text.literal(transaction.quantity + " ").formatted(Formatting.YELLOW))
                .append(Text.literal(transaction.itemName + " " + direction + " your shop for "))
                .append(Text.literal("$" + formatPrice(transaction.price)).formatted(Formatting.YELLOW));
                
            // Send single message
            owner.sendMessage(message, false);
            return true;
        }
        return false;
    }
    
    /**
     * Format a price to show only 2 decimal places if there are decimals
     */
    private static String formatPrice(double price) {
        return price == Math.floor(price) 
            ? String.format("%.0f", price) 
            : String.format("%.2f", price);
    }
    
    /**
     * Check if a player has at least one shop by checking if they've ever been a shop owner
     * in any transaction.
     * @param playerUuid The UUID of the player to check
     * @return true if the player has at least one shop, false otherwise
     */
    private boolean playerHasShops(UUID playerUuid) {
        // First check: if they have transactions for their shops in the main map
        if (playerTransactions.containsKey(playerUuid)) {
            return true;
        }
        
        // Second check: scan ALL transaction lists for this player as a shop owner
        for (Map.Entry<UUID, List<TransactionRecord>> entry : playerTransactions.entrySet()) {
            for (TransactionRecord tx : entry.getValue()) {
                if (tx.shopOwnerUuid.equals(playerUuid)) {
                    return true;
                }
            }
        }
        
        // If we reach here, the player doesn't have any shops
        return false;
    }
    
    /**
     * Notify a player about transactions that occurred while they were offline
     */
    public void sendOfflineTransactionSummary(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        
        // Check if the player has shops before proceeding - exit early if not
        boolean hasShops = playerHasShops(playerUuid);
        if (!hasShops) {
            CurrencyMod.LOGGER.info("Player {}: No shops found, skipping transaction notification", 
                player.getName().getString());
            return;
        }
        
        // Get the last login time
        long lastLogin = playerLastLogin.getOrDefault(playerUuid, 0L);
        long currentTime = System.currentTimeMillis();
        
        CurrencyMod.LOGGER.info("Player {}: Checking transactions since last login at {} (epoch {})", 
            player.getName().getString(), 
            new Date(lastLogin), 
            lastLogin);
        
        // Get transactions for this player's shops
        List<TransactionRecord> transactions = playerTransactions.getOrDefault(playerUuid, new ArrayList<>());
        
        if (transactions.isEmpty()) {
            CurrencyMod.LOGGER.info("Player {}: No transactions found for shop owner UUID {}", 
                player.getName().getString(), playerUuid);
            // Player has shops but no transactions - show the "no purchases" message
            player.sendMessage(Text.literal("No purchases were made while you were away.").formatted(Formatting.BLUE), false);
            return;
        }
        
        CurrencyMod.LOGGER.info("Player {}: Found {} total shop transactions", 
            player.getName().getString(), transactions.size());
            
        // Filter to only show transactions that happened since the last login
        List<TransactionRecord> newTransactions = new ArrayList<>();
        for (TransactionRecord transaction : transactions) {
            // Skip transactions the player made themselves
            if (transaction.playerUuid.equals(playerUuid)) {
                continue;
            }
            
            // Add transactions that happened after their last login
            if (transaction.timestamp > lastLogin && transaction.timestamp <= currentTime) {
                newTransactions.add(transaction);
                CurrencyMod.LOGGER.debug("Player {}: Including transaction at {} - {} {} {} for ${}", 
                    player.getName().getString(),
                    new Date(transaction.timestamp),
                    transaction.playerName,
                    transaction.isBuyShop ? "bought" : "sold",
                    transaction.itemName,
                    transaction.price);
            } else {
                CurrencyMod.LOGGER.debug("Player {}: Excluding transaction at {} (outside time window)", 
                    player.getName().getString(),
                    new Date(transaction.timestamp));
            }
        }
        
        // If there are no new transactions, show the "no purchases" message
        if (newTransactions.isEmpty()) {
            CurrencyMod.LOGGER.info("Player {}: No new transactions since last login", 
                player.getName().getString());
            player.sendMessage(Text.literal("No purchases were made while you were away.").formatted(Formatting.BLUE), false);
            return;
        }
        
        CurrencyMod.LOGGER.info("Player {}: Found {} new transactions to show", 
            player.getName().getString(), newTransactions.size());
        
        // Group by shop type and calculate totals
        double buyTotal = 0;
        double sellTotal = 0;
        List<TransactionRecord> buyTransactions = new ArrayList<>();
        List<TransactionRecord> sellTransactions = new ArrayList<>();
        
        for (TransactionRecord tx : newTransactions) {
            if (tx.isBuyShop) {
                buyTransactions.add(tx);
                buyTotal += tx.price;
            } else {
                sellTransactions.add(tx);
                sellTotal += tx.price;
            }
        }
        
        // Send summary header
        player.sendMessage(Text.literal("===== Shop Transactions While You Were Away =====")
            .formatted(Formatting.GOLD, Formatting.BOLD));
        
        // Send buy shop summary
        if (!buyTransactions.isEmpty()) {
            player.sendMessage(Text.literal("Buy Shop Profit: $" + formatPrice(buyTotal))
                .formatted(Formatting.GREEN), false);
            
            for (TransactionRecord tx : buyTransactions) {
                LocalDateTime time = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(tx.timestamp), ZoneId.systemDefault());
                
                player.sendMessage(
                    Text.literal("[" + TIME_FORMATTER.format(time) + "] ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal(tx.playerName + " bought " + tx.quantity + " " + tx.itemName + " for $" + formatPrice(tx.price))
                            .formatted(Formatting.GREEN))
                );
            }
        }
        
        // Send sell shop summary
        if (!sellTransactions.isEmpty()) {
            player.sendMessage(Text.literal("Sell Shop Expense: $" + formatPrice(sellTotal))
                .formatted(Formatting.BLUE, Formatting.BOLD));
            
            for (TransactionRecord tx : sellTransactions) {
                LocalDateTime time = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(tx.timestamp), ZoneId.systemDefault());
                
                player.sendMessage(
                    Text.literal("[" + TIME_FORMATTER.format(time) + "] ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal(tx.playerName + " sold " + tx.quantity + " " + tx.itemName + " for $" + formatPrice(tx.price))
                            .formatted(Formatting.BLUE))
                );
            }
        }
        
        player.sendMessage(Text.literal("==============================================")
            .formatted(Formatting.GOLD, Formatting.BOLD));
        
        // Remove the summarized transactions and update last login time
        removeProcessedTransactions(playerUuid, newTransactions);
        // Don't update login time here anymore
        
        CurrencyMod.LOGGER.info("Player {}: Showed transaction summary ({} buy, {} sell)", 
            player.getName().getString(), 
            buyTransactions.size(), 
            sellTransactions.size());
    }
    
    /**
     * Remove transactions that have been shown to the player
     */
    private void removeProcessedTransactions(UUID playerUuid, List<TransactionRecord> processedTransactions) {
        List<TransactionRecord> allTransactions = playerTransactions.get(playerUuid);
        if (allTransactions == null) return;
        
        // Remove all transactions that have been processed
        allTransactions.removeAll(processedTransactions);
        
        // If no transactions remain, remove the empty list
        if (allTransactions.isEmpty()) {
            playerTransactions.remove(playerUuid);
        }
        
        CurrencyMod.LOGGER.info("Removed {} processed transactions for player {}", 
            processedTransactions.size(), playerUuid);
    }
    
    /**
     * Clean up transactions older than 7 days
     */
    private void cleanupOldTransactions(UUID playerUuid) {
        List<TransactionRecord> transactions = playerTransactions.get(playerUuid);
        if (transactions == null) return;
        
        // Keep transactions from the last 7 days only
        long cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000); // 7 days
        
        int sizeBefore = transactions.size();
        transactions.removeIf(tx -> tx.timestamp < cutoffTime);
        int sizeAfter = transactions.size();
        
        // If all transactions were removed, remove the empty list
        if (transactions.isEmpty()) {
            playerTransactions.remove(playerUuid);
            CurrencyMod.LOGGER.info("Removed empty transaction list for player {}", playerUuid);
        } else if (sizeBefore > sizeAfter) {
            CurrencyMod.LOGGER.info("Cleaned up {} old transactions for player {}", 
                (sizeBefore - sizeAfter), playerUuid);
        }
    }
    
    /**
     * Clean up old transactions for all players
     */
    public void cleanupAllTransactions() {
        CurrencyMod.LOGGER.info("Starting cleanup of all old transactions");
        int totalRemoved = 0;
        int playersWithEmptyLists = 0;
        
        // Create a copy of the keys to avoid concurrent modification
        Set<UUID> playerUuids = new HashSet<>(playerTransactions.keySet());
        
        for (UUID playerUuid : playerUuids) {
            List<TransactionRecord> transactions = playerTransactions.get(playerUuid);
            if (transactions == null) continue;
            
            int sizeBefore = transactions.size();
            
            // Keep transactions from the last 7 days only
            long cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000); // 7 days
            transactions.removeIf(tx -> tx.timestamp < cutoffTime);
            
            int sizeAfter = transactions.size();
            totalRemoved += (sizeBefore - sizeAfter);
            
            // If all transactions were removed, remove the empty list
            if (transactions.isEmpty()) {
                playerTransactions.remove(playerUuid);
                playersWithEmptyLists++;
            }
        }
        
        CurrencyMod.LOGGER.info("Transaction cleanup complete: removed {} old transactions and {} empty player entries", 
            totalRemoved, playersWithEmptyLists);
    }
    
    /**
     * Load transaction data from disk
     */
    public void loadData(MinecraftServer server) {
        try {
            playerTransactions.clear();
            playerLastLogin.clear();
            
            File transactionsFile = FileUtil.getServerFile(server, TRANSACTIONS_FILE);
            
            if (!transactionsFile.exists()) {
                CurrencyMod.LOGGER.info("No shop transaction data found");
                return;
            }
            
            try (FileReader reader = new FileReader(transactionsFile)) {
                JsonObject rootObj = JsonParser.parseReader(reader).getAsJsonObject();
                
                // Load last login times
                JsonObject loginObj = rootObj.getAsJsonObject("lastLogin");
                if (loginObj != null) {
                    for (String uuidStr : loginObj.keySet()) {
                        UUID uuid = UUID.fromString(uuidStr);
                        long time = loginObj.get(uuidStr).getAsLong();
                        playerLastLogin.put(uuid, time);
                    }
                }
                
                // Load transactions
                JsonObject txObj = rootObj.getAsJsonObject("transactions");
                if (txObj != null) {
                    for (String uuidStr : txObj.keySet()) {
                        UUID uuid = UUID.fromString(uuidStr);
                        List<TransactionRecord> transactions = new ArrayList<>();
                        
                        for (var txElement : txObj.getAsJsonArray(uuidStr)) {
                            JsonObject tx = txElement.getAsJsonObject();
                            
                            TransactionRecord record = new TransactionRecord(
                                UUID.fromString(tx.get("playerUuid").getAsString()),
                                tx.get("playerName").getAsString(),
                                UUID.fromString(tx.get("shopOwnerUuid").getAsString()),
                                tx.get("itemName").getAsString(),
                                tx.get("quantity").getAsInt(),
                                tx.get("price").getAsDouble(),
                                tx.get("isBuyShop").getAsBoolean(),
                                tx.get("timestamp").getAsLong()
                            );
                            
                            transactions.add(record);
                        }
                        
                        playerTransactions.put(uuid, transactions);
                    }
                }
                
                CurrencyMod.LOGGER.info("Loaded shop transaction data for {} players", playerTransactions.size());
                
                // Clean up old transactions immediately after loading
                cleanupAllTransactions();
            }
        } catch (Exception e) {
            CurrencyMod.LOGGER.error("Error loading shop transaction data", e);
        }
    }
    
    /**
     * Save transaction data to disk
     */
    public void saveData(MinecraftServer server) {
        try {
            if (server == null) {
                CurrencyMod.LOGGER.error("Cannot save shop transaction data: server is null");
                return;
            }
            
            // Use FileUtil to get the file path
            File transactionsFile = FileUtil.getServerFile(server, TRANSACTIONS_FILE);
            if (transactionsFile == null) {
                CurrencyMod.LOGGER.error("Failed to get shop transactions file path");
                return;
            }
            
            CurrencyMod.LOGGER.info("Saving shop transaction data for {} players to: {}", 
                playerTransactions.size(), transactionsFile.getAbsolutePath());
            
            JsonObject rootObj = new JsonObject();
            
            // Save last login times
            JsonObject loginObj = new JsonObject();
            for (Map.Entry<UUID, Long> entry : playerLastLogin.entrySet()) {
                loginObj.addProperty(entry.getKey().toString(), entry.getValue());
            }
            rootObj.add("lastLogin", loginObj);
            
            // Save transactions
            JsonObject txObj = new JsonObject();
            for (Map.Entry<UUID, List<TransactionRecord>> entry : playerTransactions.entrySet()) {
                var transactions = new ArrayList<>();
                
                for (TransactionRecord record : entry.getValue()) {
                    JsonObject tx = new JsonObject();
                    tx.addProperty("playerUuid", record.playerUuid.toString());
                    tx.addProperty("playerName", record.playerName);
                    tx.addProperty("shopOwnerUuid", record.shopOwnerUuid.toString());
                    tx.addProperty("itemName", record.itemName);
                    tx.addProperty("quantity", record.quantity);
                    tx.addProperty("price", record.price);
                    tx.addProperty("isBuyShop", record.isBuyShop);
                    tx.addProperty("timestamp", record.timestamp);
                    
                    transactions.add(tx);
                }
                
                txObj.add(entry.getKey().toString(), GSON.toJsonTree(transactions));
            }
            rootObj.add("transactions", txObj);
            
            // Convert to JSON string
            String jsonContent = GSON.toJson(rootObj);
            
            // Write to file using FileUtil
            boolean success = FileUtil.safeWriteToFile(server, transactionsFile, jsonContent);
            
            if (success) {
                CurrencyMod.LOGGER.info("Successfully saved shop transaction data for {} players", playerTransactions.size());
            } else {
                CurrencyMod.LOGGER.error("Failed to save shop transaction data - FileUtil operation returned failure");
            }
        } catch (Exception e) {
            CurrencyMod.LOGGER.error("Error saving shop transaction data", e);
        }
    }
    
    /**
     * Record a player login to track when offline notifications should be shown
     * This should not update the playerLastLogin map directly, that will happen
     * after checking for transactions
     */
    public void recordPlayerLogin(UUID playerUuid) {
        // Don't update the last login time here anymore
        // Let checkOfflineTransactions handle the update after checking for transactions
        // Note: Don't remove existing entry if present
    }
    
    /**
     * Check and display transaction summary for a player who just logged in
     * @param player The player who just logged in
     */
    public void checkOfflineTransactions(ServerPlayerEntity player) {
        if (player == null) return;
        
        UUID playerUuid = player.getUuid();
        
        CurrencyMod.LOGGER.info("Checking offline transactions for player: {}", player.getName().getString());
        
        // Send a summary of transactions that occurred while the player was offline
        // This will use the existing lastLogin time, or 0 if none exists
        sendOfflineTransactionSummary(player);
        
        // Now update the login time after we've processed transactions
        playerLastLogin.put(playerUuid, System.currentTimeMillis());
        
        // Also clean up any old transactions that weren't part of the summary
        cleanupOldTransactions(playerUuid);
        
        // Save the data after updating login time and removing summarized transactions
        MinecraftServer server = CurrencyMod.getServer();
        if (server != null) {
            saveData(server);
        }
    }
    
    /**
     * Get a list of all transaction records across all players
     * @return A list of all transaction records
     */
    public List<TransactionRecord> getAllTransactions() {
        List<TransactionRecord> allTransactions = new ArrayList<>();
        for (List<TransactionRecord> playerTransactionList : playerTransactions.values()) {
            allTransactions.addAll(playerTransactionList);
        }
        return allTransactions;
    }
    
    /**
     * Sends a summary of transactions to a player
     */
    private void sendTransactionSummary(ServerPlayerEntity player, List<TransactionRecord> transactions) {
        if (transactions.isEmpty()) return;
        
        // First calculate the totals for buys and sells
        double totalBuys = 0;
        double totalSells = 0;
        int buyCount = 0;
        int sellCount = 0;
        
        for (TransactionRecord txn : transactions) {
            if (txn.isBuyShop) {
                totalBuys += txn.price;
                buyCount++;
            } else {
                totalSells += txn.price;
                sellCount++;
            }
        }
        
        // Create a styled header for the transaction summary
        Text header = Text.literal("⏱ ").formatted(Formatting.GOLD)
            .append(Text.literal("Offline Transaction Summary").formatted(Formatting.GOLD, Formatting.BOLD));
        
        player.sendMessage(header, false);
        
        // If the player has a summary waiting, show it to them
        if (buyCount > 0) {
            Text buyMessage = Text.literal("► ").formatted(Formatting.GREEN)
                .append(Text.literal("Players bought ").formatted(Formatting.WHITE))
                .append(Text.literal("items from your shops ").formatted(Formatting.GREEN))
                .append(Text.literal(buyCount + " times").formatted(Formatting.YELLOW))
                .append(Text.literal(" totaling ").formatted(Formatting.WHITE))
                .append(Text.literal("$" + formatPrice(totalBuys)).formatted(Formatting.YELLOW));
            
            player.sendMessage(buyMessage, false);
        }
        
        if (sellCount > 0) {
            Text sellMessage = Text.literal("► ").formatted(Formatting.BLUE)
                .append(Text.literal("Players sold ").formatted(Formatting.WHITE))
                .append(Text.literal("items to your shops ").formatted(Formatting.BLUE))
                .append(Text.literal(sellCount + " times").formatted(Formatting.YELLOW))
                .append(Text.literal(" totaling ").formatted(Formatting.WHITE))
                .append(Text.literal("$" + formatPrice(totalSells)).formatted(Formatting.YELLOW));
            
            player.sendMessage(sellMessage, false);
        }
        
        // Add a note about detailed logs if there are many transactions
        if (transactions.size() > 5) {
            player.sendMessage(
                Text.literal("See server logs for detailed transaction history.").formatted(Formatting.GRAY, Formatting.ITALIC),
                false
            );
        }
    }
    
    /**
     * Class to hold transaction record data
     */
    public static class TransactionRecord {
        public final UUID playerUuid;
        public final String playerName;
        public final UUID shopOwnerUuid;
        public final String itemName;
        public final int quantity;
        public final double price;
        public final boolean isBuyShop;
        public final long timestamp;
        
        public TransactionRecord(UUID playerUuid, String playerName, UUID shopOwnerUuid, 
                                 String itemName, int quantity, double price, 
                                 boolean isBuyShop, long timestamp) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.shopOwnerUuid = shopOwnerUuid;
            this.itemName = itemName;
            this.quantity = quantity;
            this.price = price;
            this.isBuyShop = isBuyShop;
            this.timestamp = timestamp;
        }
        
        @Override
        public String toString() {
            return "Transaction[player=" + playerName + ", item=" + itemName + 
                   ", qty=" + quantity + ", price=" + price + 
                   ", type=" + (isBuyShop ? "BUY" : "SELL") + "]";
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TransactionRecord that = (TransactionRecord) o;
            return quantity == that.quantity &&
                   Double.compare(that.price, price) == 0 &&
                   isBuyShop == that.isBuyShop &&
                   timestamp == that.timestamp &&
                   Objects.equals(playerUuid, that.playerUuid) &&
                   Objects.equals(shopOwnerUuid, that.shopOwnerUuid) &&
                   Objects.equals(itemName, that.itemName);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(playerUuid, shopOwnerUuid, itemName, quantity, price, isBuyShop, timestamp);
        }
    }
} 