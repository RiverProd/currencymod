package net.currencymod.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.currencymod.CurrencyMod;
import net.currencymod.util.FileUtil;
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
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Manages offline payment records and notification for players
 */
public class OfflinePaymentManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/OfflinePaymentManager");
    private static final String PAYMENTS_FILE = "currency_mod/offline_payments.json";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    private static final OfflinePaymentManager INSTANCE = new OfflinePaymentManager();
    
    // Map of player UUID to list of payment records
    private final Map<UUID, List<PaymentRecord>> offlinePayments = new HashMap<>();
    
    /**
     * Get the singleton instance
     */
    public static OfflinePaymentManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Private constructor for singleton
     */
    private OfflinePaymentManager() {
    }
    
    /**
     * Record a payment to an offline player
     * @param receiverUuid UUID of the player receiving the payment
     * @param senderName Name of the player sending the payment
     * @param amount Amount of the payment
     */
    public void recordOfflinePayment(UUID receiverUuid, String senderName, double amount) {
        PaymentRecord record = new PaymentRecord(senderName, amount, System.currentTimeMillis());
        
        // Add the payment record to the player's list
        offlinePayments.computeIfAbsent(receiverUuid, k -> new ArrayList<>()).add(record);
        
        // Save the updated payments
        saveOfflinePayments();
        
        LOGGER.info("Recorded offline payment: {} sent ${} to {}", 
            senderName, amount, receiverUuid);
    }
    
    /**
     * Check if a player has any offline payments and notify them if so
     * @param player The player to check and notify
     */
    public void checkOfflinePayments(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        List<PaymentRecord> payments = offlinePayments.get(playerUuid);
        
        if (payments == null || payments.isEmpty()) {
            return; // No offline payments
        }
        
        // Sort payments by timestamp (newest first)
        payments.sort(Comparator.comparing(PaymentRecord::timestamp).reversed());
        
        // Calculate total amount received
        double totalAmount = payments.stream()
            .mapToDouble(PaymentRecord::amount)
            .sum();
        
        // Format with 2 decimal places
        String formattedTotal = formatAmount(totalAmount);
        
        // Send header message
        player.sendMessage(Text.literal("📝 Payments Received While Offline:")
            .formatted(Formatting.GOLD));
        
        // Send total
        player.sendMessage(Text.literal("You received a total of ")
            .append(Text.literal("$" + formattedTotal)
                .formatted(Formatting.GREEN, Formatting.BOLD))
            .append(Text.literal(" from ")
                .formatted(Formatting.WHITE))
            .append(Text.literal(payments.size() + " payment" + (payments.size() > 1 ? "s" : ""))
                .formatted(Formatting.YELLOW)));
        
        // Send details for each payment (up to 5, to avoid spam)
        int displayCount = Math.min(payments.size(), 5);
        for (int i = 0; i < displayCount; i++) {
            PaymentRecord payment = payments.get(i);
            String formattedAmount = formatAmount(payment.amount());
            
            // Format date
            String formattedDate = DATE_FORMAT.format(new Date(payment.timestamp()));
            
            player.sendMessage(Text.literal("• ")
                .formatted(Formatting.GRAY)
                .append(Text.literal(payment.senderName())
                    .formatted(Formatting.YELLOW))
                .append(Text.literal(" sent you ")
                    .formatted(Formatting.WHITE))
                .append(Text.literal("$" + formattedAmount)
                    .formatted(Formatting.GREEN, Formatting.BOLD))
                .append(Text.literal(" on ")
                    .formatted(Formatting.WHITE))
                .append(Text.literal(formattedDate)
                    .formatted(Formatting.GRAY)));
        }
        
        // If there are more payments, show a summary message
        if (payments.size() > 5) {
            int remaining = payments.size() - 5;
            player.sendMessage(Text.literal("... and ")
                .formatted(Formatting.GRAY)
                .append(Text.literal(remaining + " more payment" + (remaining > 1 ? "s" : ""))
                    .formatted(Formatting.YELLOW)));
        }
        
        // Clear the payments for this player
        offlinePayments.remove(playerUuid);
        saveOfflinePayments();
        
        LOGGER.info("Notified player {} of {} offline payments totaling ${}", 
            player.getName().getString(), payments.size(), totalAmount);
    }
    
    /**
     * Load offline payments from file
     * @param server The Minecraft server instance
     */
    public void loadOfflinePayments(MinecraftServer server) {
        File dataFile = FileUtil.getServerFile(server, PAYMENTS_FILE);
        if (!dataFile.exists()) {
            LOGGER.info("No offline payments file found");
            return;
        }
        
        try (FileReader reader = new FileReader(dataFile)) {
            Gson gson = new GsonBuilder().create();
            Type type = new TypeToken<Map<UUID, List<PaymentRecord>>>(){}.getType();
            Map<UUID, List<PaymentRecord>> loaded = gson.fromJson(reader, type);
            
            if (loaded != null) {
                offlinePayments.clear();
                offlinePayments.putAll(loaded);
                LOGGER.info("Loaded offline payments for {} players", offlinePayments.size());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load offline payments: {}", e.getMessage());
        }
    }
    
    /**
     * Save offline payments to file
     */
    private void saveOfflinePayments() {
        MinecraftServer server = CurrencyMod.getServer();
        if (server == null) {
            LOGGER.error("Cannot save offline payments: server is null");
            return;
        }
        
        File dataFile = FileUtil.getServerFile(server, PAYMENTS_FILE);
        
        try (FileWriter writer = new FileWriter(dataFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(offlinePayments, writer);
            LOGGER.info("Saved offline payments for {} players", offlinePayments.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save offline payments: {}", e.getMessage());
        }
    }
    
    /**
     * Format an amount with proper decimal places
     */
    private String formatAmount(double amount) {
        if (amount == Math.floor(amount)) {
            return String.format("%.0f", amount);
        } else {
            return String.format("%.2f", amount);
        }
    }
    
    /**
     * Record class to store payment information
     */
    public record PaymentRecord(String senderName, double amount, long timestamp) {
    }
} 