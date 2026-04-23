package net.currencymod.trade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.currencymod.CurrencyMod;
import net.currencymod.economy.EconomyManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * Manages player-to-player trades
 */
public class TradeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/TradeManager");
    private static final TradeManager INSTANCE = new TradeManager();
    
    // Trade timeout in seconds
    private static final int TRADE_TIMEOUT = 60; // 1 minute
    
    // Reminder intervals in seconds
    private static final int[] REMINDER_INTERVALS = {30, 15, 5};
    
    // Map of active trade requests: target player UUID -> trade request
    private final Map<UUID, TradeRequest> activeTradeRequests = new HashMap<>();
    
    // Scheduler for trade timeouts
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    /**
     * Private constructor for singleton
     */
    private TradeManager() {
    }
    
    /**
     * Get the singleton instance
     */
    public static TradeManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Create a new trade request
     * @param sender The player sending the trade request
     * @param target The player receiving the trade request
     * @param item The item being offered
     * @param price The price being asked
     * @return true if request was created, false if a request already exists
     */
    public boolean createTradeRequest(ServerPlayerEntity sender, ServerPlayerEntity target, ItemStack item, double price) {
        // Check if target already has an active trade request
        if (activeTradeRequests.containsKey(target.getUuid())) {
            return false;
        }
        
        // Make a copy of the item before removing it from the inventory
        ItemStack tradedItem = item.copy();
        
        // Remove the item from the sender's hand immediately
        sender.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        
        // Create a new trade request
        TradeRequest request = new TradeRequest(sender, target, tradedItem, price);
        activeTradeRequests.put(target.getUuid(), request);
        
        // Send trade request message to the target player
        sendTradeRequest(request);
        
        // Schedule request timeout
        ScheduledFuture<?> timeoutFuture = scheduler.schedule(
            () -> expireTradeRequest(request),
            TRADE_TIMEOUT,
            TimeUnit.SECONDS
        );
        request.setTimeoutFuture(timeoutFuture);
        
        // Schedule reminders
        scheduleReminders(request);
        
        LOGGER.info("Created trade request from {} to {}: {} for ${}", 
            sender.getName().getString(), target.getName().getString(), 
            tradedItem.getName().getString(), price);
        
        return true;
    }
    
    /**
     * Schedule reminders for the trade request
     */
    private void scheduleReminders(TradeRequest request) {
        for (int seconds : REMINDER_INTERVALS) {
            int delaySeconds = TRADE_TIMEOUT - seconds;
            
            if (delaySeconds > 0) {
                scheduler.schedule(
                    () -> sendReminderMessage(request, seconds),
                    delaySeconds,
                    TimeUnit.SECONDS
                );
            }
        }
    }
    
    /**
     * Send a reminder message to the target player
     */
    private void sendReminderMessage(TradeRequest request, int secondsLeft) {
        ServerPlayerEntity target = request.getTarget();
        ServerPlayerEntity sender = request.getSender();
        
        // Check if the request is still active
        if (!activeTradeRequests.containsKey(target.getUuid())) {
            return;
        }
        
        // Send reminder to target
        if (target != null && !target.isDisconnected()) {
            Text reminderText = Text.literal("⏰ ").formatted(Formatting.YELLOW)
                .append(Text.literal("Trade Reminder").formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal("\nYou have a pending trade request from "))
                .append(Text.literal(sender.getName().getString()).formatted(Formatting.AQUA))
                .append(Text.literal("\nThis trade will expire in "))
                .append(Text.literal(secondsLeft + " seconds").formatted(Formatting.RED));
                
            target.sendMessage(reminderText);
        }
        
        // Send reminder to sender
        if (sender != null && !sender.isDisconnected()) {
            Text reminderText = Text.literal("⏰ ").formatted(Formatting.YELLOW)
                .append(Text.literal("Trade Reminder").formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal("\nYour trade request to "))
                .append(Text.literal(target.getName().getString()).formatted(Formatting.AQUA))
                .append(Text.literal(" will expire in "))
                .append(Text.literal(secondsLeft + " seconds").formatted(Formatting.RED));
                
            sender.sendMessage(reminderText);
        }
    }
    
    /**
     * Accept a trade request
     * @param targetPlayer The player accepting the request
     * @return true if the trade was successful, false otherwise
     */
    public boolean acceptTradeRequest(ServerPlayerEntity targetPlayer) {
        UUID targetUuid = targetPlayer.getUuid();
        
        // Check if there's an active trade request for this player
        if (!activeTradeRequests.containsKey(targetUuid)) {
            return false;
        }
        
        TradeRequest request = activeTradeRequests.get(targetUuid);
        
        // Cancel the timeout future
        if (request.getTimeoutFuture() != null) {
            request.getTimeoutFuture().cancel(false);
        }
        
        // Execute the trade
        boolean success = executeTradeTransaction(request);
        
        // Remove the trade request
        activeTradeRequests.remove(targetUuid);
        
        return success;
    }
    
    /**
     * Deny a trade request
     * @param targetPlayer The player denying the request
     * @return true if a request was denied, false otherwise
     */
    public boolean denyTradeRequest(ServerPlayerEntity targetPlayer) {
        UUID targetUuid = targetPlayer.getUuid();
        
        // Check if there's an active trade request for this player
        if (!activeTradeRequests.containsKey(targetUuid)) {
            return false;
        }
        
        TradeRequest request = activeTradeRequests.get(targetUuid);
        
        // Cancel the timeout future
        if (request.getTimeoutFuture() != null) {
            request.getTimeoutFuture().cancel(false);
        }
        
        // Return the item to the sender's inventory
        returnItemToSender(request, "trade denied");
        
        // Notify both players that the trade was denied
        ServerPlayerEntity sender = request.getSender();
        if (sender != null && !sender.isDisconnected()) {
            Text message = Text.literal("❌ ").formatted(Formatting.RED)
                .append(Text.literal("Trade Denied").formatted(Formatting.RED, Formatting.BOLD))
                .append(Text.literal("\n" + targetPlayer.getName().getString() + " denied your trade request."))
                .append(Text.literal("\nYour item has been returned to your inventory.").formatted(Formatting.GREEN));
            
            sender.sendMessage(message);
        }
        
        Text message = Text.literal("✓ ").formatted(Formatting.GREEN)
            .append(Text.literal("Trade Denied").formatted(Formatting.RED, Formatting.BOLD))
            .append(Text.literal("\nYou denied the trade request from " + request.getSender().getName().getString() + "."));
            
        targetPlayer.sendMessage(message);
        
        // Remove the trade request
        activeTradeRequests.remove(targetUuid);
        
        LOGGER.info("Trade request from {} to {} was denied", 
            request.getSender().getName().getString(), 
            targetPlayer.getName().getString());
        
        return true;
    }
    
    /**
     * Handle trade request expiration
     */
    private void expireTradeRequest(TradeRequest request) {
        ServerPlayerEntity target = request.getTarget();
        
        // Check if the request is still active
        if (target == null || !activeTradeRequests.containsKey(target.getUuid())) {
            return;
        }
        
        // Return the item to the sender's inventory
        returnItemToSender(request, "trade expired");
        
        // Notify both players that the trade expired
        ServerPlayerEntity sender = request.getSender();
        
        if (sender != null && !sender.isDisconnected()) {
            Text message = Text.literal("⏰ ").formatted(Formatting.YELLOW)
                .append(Text.literal("Trade Expired").formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal("\nYour trade request to " + target.getName().getString() + " has expired."))
                .append(Text.literal("\nYour item has been returned to your inventory.").formatted(Formatting.GREEN));
                
            sender.sendMessage(message);
        }
        
        if (target != null && !target.isDisconnected()) {
            Text message = Text.literal("⏰ ").formatted(Formatting.YELLOW)
                .append(Text.literal("Trade Expired").formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal("\nThe trade request from " + sender.getName().getString() + " has expired."));
                
            target.sendMessage(message);
        }
        
        // Remove the trade request
        activeTradeRequests.remove(target.getUuid());
        
        LOGGER.info("Trade request from {} to {} expired", 
            sender != null ? sender.getName().getString() : "unknown", 
            target != null ? target.getName().getString() : "unknown");
    }
    
    /**
     * Return the traded item to the sender
     */
    private void returnItemToSender(TradeRequest request, String reason) {
        ServerPlayerEntity sender = request.getSender();
        ItemStack itemToReturn = request.getItem();
        
        if (sender != null && !sender.isDisconnected()) {
            // Try to give the item back to the player
            if (!sender.getInventory().insertStack(itemToReturn)) {
                // If inventory is full, drop the item at the player's feet
                sender.dropItem(itemToReturn, false);
                
                sender.sendMessage(Text.literal("⚠ Your inventory was full, so the traded item was dropped at your feet.")
                    .formatted(Formatting.GOLD));
            }
            
            LOGGER.info("Returned trade item to {} due to {}: {}", 
                sender.getName().getString(), reason, itemToReturn.getName().getString());
        } else {
            LOGGER.warn("Could not return trade item due to {}: sender is offline", reason);
        }
    }
    
    /**
     * Send the trade request message to the target player
     */
    private void sendTradeRequest(TradeRequest request) {
        ServerPlayerEntity target = request.getTarget();
        ServerPlayerEntity sender = request.getSender();
        ItemStack item = request.getItem();
        double price = request.getPrice();
        
        // Format item name with hover tooltip
        MutableText itemComponent = createItemHoverTooltip(item);
        
        // Create clickable buttons
        MutableText acceptButton = Text.literal("[Accept]")
            .setStyle(Style.EMPTY
                .withColor(Formatting.GREEN)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tradehand accept"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    Text.literal("Click to accept this trade"))));
                    
        MutableText denyButton = Text.literal("[Deny]")
            .setStyle(Style.EMPTY
                .withColor(Formatting.RED)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tradehand deny"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    Text.literal("Click to deny this trade"))));
        
        // Create and send the message
        Text header = Text.literal("📦 ")
            .formatted(Formatting.GOLD)
            .append(Text.literal("Trade Request")
                .formatted(Formatting.GOLD));
            
        Text details = Text.literal("\n" + sender.getName().getString() + " wants to sell you:")
            .append(Text.literal("\n").append(itemComponent))
            .append(Text.literal("\nfor "))
            .append(Text.literal("$" + formatPrice(price))
                .formatted(Formatting.GREEN, Formatting.BOLD));
            
        Text timer = Text.literal("\n\nThis request will expire in " + TRADE_TIMEOUT + " seconds.");
            
        Text buttons = Text.literal("\n")
            .append(acceptButton)
            .append(Text.literal(" "))
            .append(denyButton);
        
        target.sendMessage(header.copy().append(details).append(timer).append(buttons));
        
        // Notify the sender as well
        Text senderMessage = Text.literal("📦 ")
            .formatted(Formatting.GOLD)
            .append(Text.literal("Trade Request Sent")
                .formatted(Formatting.GOLD))
            .append(Text.literal("\nYou offered to sell "))
            .append(itemComponent)
            .append(Text.literal(" to " + target.getName().getString() + " for "))
            .append(Text.literal("$" + formatPrice(price))
                .formatted(Formatting.GREEN, Formatting.BOLD))
            .append(Text.literal("\nYour item has been removed from your inventory and will be returned if the trade expires or is denied."))
            .append(Text.literal("\nWaiting for their response..."));
            
        sender.sendMessage(senderMessage);
    }
    
    /**
     * Execute the trade transaction
     * @param request The trade request to execute
     * @return true if the trade was successful, false otherwise
     */
    private boolean executeTradeTransaction(TradeRequest request) {
        ServerPlayerEntity sender = request.getSender();
        ServerPlayerEntity target = request.getTarget();
        ItemStack item = request.getItem();
        double price = request.getPrice();
        
        LOGGER.info("Executing trade transaction: {} selling {} to {} for ${}", 
            sender.getName().getString(), 
            item.getName().getString(), 
            target.getName().getString(), 
            price);
        
        // Get the economy manager from the main mod class
        var economyManager = CurrencyMod.getEconomyManager();
        
        if (economyManager == null) {
            LOGGER.error("Cannot execute trade: Economy manager is null");
            return false;
        }
        
        // Check if the buyer has enough money
        if (economyManager.getBalance(target.getUuid()) < price) {
            // Notify both players that the buyer doesn't have enough money
            sender.sendMessage(Text.literal("❌ Trade Failed: ").formatted(Formatting.RED, Formatting.BOLD)
                .append(Text.literal(target.getName().getString() + " doesn't have enough money.")));
                
            target.sendMessage(Text.literal("❌ Trade Failed: ").formatted(Formatting.RED, Formatting.BOLD)
                .append(Text.literal("You don't have enough money to complete this trade.")));
                
            // Return the item to the sender
            returnItemToSender(request, "buyer insufficient funds");
            
            LOGGER.info("Trade failed: {} doesn't have enough money", target.getName().getString());
            return false;
        }
        
        // Transfer the money from the buyer to the seller
        boolean transferSuccess = economyManager.transferMoney(target.getUuid(), sender.getUuid(), price);
        
        if (!transferSuccess) {
            // Notify both players that the money transfer failed
            sender.sendMessage(Text.literal("❌ Trade Failed: ").formatted(Formatting.RED, Formatting.BOLD)
                .append(Text.literal("Money transfer failed. The item has been returned to you.")));
                
            target.sendMessage(Text.literal("❌ Trade Failed: ").formatted(Formatting.RED, Formatting.BOLD)
                .append(Text.literal("Money transfer failed.")));
                
            // Return the item to the sender
            returnItemToSender(request, "money transfer failed");
            
            LOGGER.error("Trade failed: Money transfer failed");
            return false;
        }
        
        // Give the item to the buyer
        boolean itemGiven = giveItemToBuyer(target, item);
        
        if (!itemGiven) {
            // Reverse the money transfer
            economyManager.transferMoney(sender.getUuid(), target.getUuid(), price);
            
            // Notify both players that the item transfer failed
            sender.sendMessage(Text.literal("❌ Trade Failed: ").formatted(Formatting.RED, Formatting.BOLD)
                .append(Text.literal("Couldn't give the item to the buyer. The money has been refunded.")));
                
            target.sendMessage(Text.literal("❌ Trade Failed: ").formatted(Formatting.RED, Formatting.BOLD)
                .append(Text.literal("Couldn't receive the item. Your money has been refunded.")));
                
            // Return the item to the sender
            returnItemToSender(request, "item transfer failed");
            
            LOGGER.error("Trade failed: Item transfer failed");
            return false;
        }
        
        // Notify both players that the trade was successful
        MutableText itemComponent = createItemHoverTooltip(item);
        
        sender.sendMessage(Text.literal("✓ Trade Successful! ").formatted(Formatting.GREEN, Formatting.BOLD)
            .append(Text.literal("You sold "))
            .append(itemComponent)
            .append(Text.literal(" to " + target.getName().getString() + " for "))
            .append(Text.literal("$" + formatPrice(price)).formatted(Formatting.GREEN)));
            
        target.sendMessage(Text.literal("✓ Trade Successful! ").formatted(Formatting.GREEN, Formatting.BOLD)
            .append(Text.literal("You bought "))
            .append(itemComponent)
            .append(Text.literal(" from " + sender.getName().getString() + " for "))
            .append(Text.literal("$" + formatPrice(price)).formatted(Formatting.GREEN)));
            
        LOGGER.info("Trade successful: {} sold {} to {} for ${}", 
            sender.getName().getString(), 
            item.getName().getString(), 
            target.getName().getString(), 
            price);
            
        return true;
    }
    
    /**
     * Give an item to the buyer
     * @param buyer The player buying the item
     * @param item The item to give
     * @return true if the item was successfully given, false otherwise
     */
    private boolean giveItemToBuyer(ServerPlayerEntity buyer, ItemStack item) {
        if (buyer == null || item == null || item.isEmpty()) {
            return false;
        }
        
        // Try to add the item to the buyer's inventory
        boolean success = buyer.getInventory().insertStack(item.copy());
        
        if (!success) {
            // If the inventory is full, drop the item at the buyer's feet
            buyer.dropItem(item.copy(), false);
            
            buyer.sendMessage(Text.literal("⚠ Your inventory was full, so the item was dropped at your feet.")
                .formatted(Formatting.GOLD));
        }
        
        return true;
    }

    public static ItemEnchantmentsComponent getEnchants(ItemStack stack) {
        // Under the hood this reads the "Enchantments" NBT list and
        // turns it into a Map<Enchantment, Integer>
        return EnchantmentHelper.getEnchantments(stack);
    }

    /**
     * Extract enchantment information from an item using simplified approach for 1.21.1
     * @param item The item to extract enchantment info from
     * @return Text component with enchantment information or null if none
     */
    private Text getEnchantmentInfoText(ItemStack item) {
        if (!item.hasGlint()) {
            return null;
        }
        
        // In Minecraft 1.21.1, just provide a simple enchantment indicator without details
        // This avoids API compatibility issues

        ItemEnchantmentsComponent enchantments = getEnchants(item);
        // ask it to write its data into a fresh NbtCompound
        Set<RegistryEntry<Enchantment>> enchSet = enchantments.getEnchantments();
        Map<Enchantment, Integer> enchMap = new HashMap<>();
        for (RegistryEntry<Enchantment> entry : enchSet) {
            enchMap.put(entry.value(), enchantments.getLevel(entry));
        }

        var enchantmentText = Text.literal("✨ Enchanted with ")
                .formatted(Formatting.LIGHT_PURPLE);

        int idx   = 0;
        int total = enchMap.size();

        List<Text> enchantmentTexts = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : enchMap.entrySet()) {
            enchantmentTexts.add(Text.literal(entry.getKey().toString().replace("minecraft:", "").replace("Enchantment ", ""))
                .formatted(Formatting.LIGHT_PURPLE).append(" " + entry.getValue()));
            idx++;
            if (idx < total) {
                enchantmentTexts.add(Text.literal(", "));
            }
            else if (idx == total) {
                enchantmentTexts.add(Text.literal("."));
            }
        }

        for (Text text : enchantmentTexts) {
            enchantmentText.append(text);
        }

        return enchantmentText;
    }
    
    /**
     * Creates a hover tooltip for an item with enchantment details
     */
    private MutableText createItemHoverTooltip(ItemStack item) {
        MutableText itemText = Text.literal(item.getCount() + "x ")
            .formatted(Formatting.YELLOW)
            .append(item.getName().copy().formatted(Formatting.GOLD));
        
        // Create tooltip with item info
        List<Text> tooltip = new ArrayList<>();
        tooltip.add(item.getName().copy().formatted(Formatting.GOLD, Formatting.BOLD));
        tooltip.add(Text.literal("")); // Empty line
        
        // In Minecraft 1.21.1, we can't easily get detailed enchantment info
        // Just indicate that the item is enchanted
        if (item.hasGlint()) {
            tooltip.add(getEnchantmentInfoText(item));
        }
        
        // Set the hover event with our custom tooltip
        MutableText joinedText = Text.literal("");
        for (int i = 0; i < tooltip.size(); i++) {
            joinedText.append(tooltip.get(i));
            if (i < tooltip.size() - 1) {
                joinedText.append(Text.literal("\n"));
            }
        }
        
        itemText.setStyle(itemText.getStyle().withHoverEvent(
            new HoverEvent(HoverEvent.Action.SHOW_TEXT, joinedText)
        ));
        
        return itemText;
    }
    
    /**
     * Format a price nicely
     */
    private String formatPrice(double price) {
        if (price == Math.floor(price)) {
            return String.format("%.0f", price);
        } else {
            return String.format("%.2f", price);
        }
    }
    
    /**
     * Clean up resources
     */
    public void shutdown() {
        // Return all traded items to their senders
        for (TradeRequest request : activeTradeRequests.values()) {
            returnItemToSender(request, "server shutdown");
        }
        
        // Clear active requests
        activeTradeRequests.clear();
        
        // Shutdown the scheduler
        scheduler.shutdown();
    }
    
    /**
     * Class to hold trade request information
     */
    private static class TradeRequest {
        private final ServerPlayerEntity sender;
        private final ServerPlayerEntity target;
        private final ItemStack item;
        private final double price;
        private ScheduledFuture<?> timeoutFuture;
        
        public TradeRequest(ServerPlayerEntity sender, ServerPlayerEntity target, ItemStack item, double price) {
            this.sender = sender;
            this.target = target;
            this.item = item;
            this.price = price;
        }
        
        public ServerPlayerEntity getSender() {
            return sender;
        }
        
        public ServerPlayerEntity getTarget() {
            return target;
        }
        
        public ItemStack getItem() {
            return item;
        }
        
        public double getPrice() {
            return price;
        }
        
        public ScheduledFuture<?> getTimeoutFuture() {
            return timeoutFuture;
        }
        
        public void setTimeoutFuture(ScheduledFuture<?> timeoutFuture) {
            this.timeoutFuture = timeoutFuture;
        }
    }
} 