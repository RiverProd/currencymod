package net.currencymod.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates and delivers notifications to service providers about
 * new subscribers and revenue earned while they were offline.
 */
public class ServiceNotificationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/ServiceNotificationManager");
    private static final String NOTIFICATIONS_FILE = "currency_mod/service_notifications.json";

    private static final ServiceNotificationManager INSTANCE = new ServiceNotificationManager();

    // Map of provider UUID -> (service tag -> summary)
    private final Map<UUID, Map<String, ServiceSummary>> providerSummaries = new HashMap<>();

    private ServiceNotificationManager() {}

    public static ServiceNotificationManager getInstance() {
        return INSTANCE;
    }

    /**
     * Record a new subscriber for a provider's service.
     * Sends live notification if provider is online, otherwise stores for later.
     */
    public void recordSubscription(UUID providerUuid, UUID subscriberUuid, String serviceTag) {
        if (providerUuid == null || serviceTag == null) {
            return;
        }

        // Get subscriber name from subscription if available
        String subscriberName = null;
        try {
            ServiceManager serviceManager = ServiceManager.getInstance();
            ServiceSubscription subscription = serviceManager.getSubscription(subscriberUuid, serviceTag);
            if (subscription != null) {
                subscriberName = subscription.getSubscriberName();
            }
        } catch (Exception e) {
            // Will fall back to UUID lookup in notifyProviderIfOnline
        }

        // Try to send live notification if provider is online
        boolean providerWasOnline = notifyProviderIfOnline(providerUuid, subscriberUuid, subscriberName, serviceTag, true, 0.0);

        // Only store for offline delivery if provider was offline
        if (!providerWasOnline) {
            Map<String, ServiceSummary> summaries = providerSummaries
                .computeIfAbsent(providerUuid, k -> new HashMap<>());
            ServiceSummary summary = summaries.computeIfAbsent(serviceTag.toUpperCase(), k -> new ServiceSummary());

            summary.newSubscribers++;

            saveNotifications();
        }

        LOGGER.debug("Recorded new subscriber {} for service {} (provider {}, online: {})",
            subscriberUuid, serviceTag, providerUuid, providerWasOnline);
    }

    /**
     * Record revenue earned for a provider's service.
     * Sends live notification if provider is online, otherwise stores for later.
     */
    public void recordRevenue(UUID providerUuid, String serviceTag, double amount) {
        if (providerUuid == null || serviceTag == null || amount <= 0) {
            return;
        }

        // Try to send live notification if provider is online
        boolean providerWasOnline = notifyProviderIfOnline(providerUuid, null, null, serviceTag, false, amount);

        // Only store for offline delivery if provider was offline
        if (!providerWasOnline) {
            Map<String, ServiceSummary> summaries = providerSummaries
                .computeIfAbsent(providerUuid, k -> new HashMap<>());
            ServiceSummary summary = summaries.computeIfAbsent(serviceTag.toUpperCase(), k -> new ServiceSummary());

            summary.revenue += amount;

            saveNotifications();
        }

        LOGGER.debug("Recorded revenue ${} for service {} (provider {}, online: {})",
            amount, serviceTag, providerUuid, providerWasOnline);
    }

    /**
     * Deliver any pending notifications to a provider when they log in.
     * After delivery, the stored summaries are cleared for that provider.
     */
    public void deliverNotifications(ServerPlayerEntity provider) {
        if (provider == null) {
            return;
        }

        UUID providerUuid = provider.getUuid();
        Map<String, ServiceSummary> summaries = providerSummaries.get(providerUuid);

        if (summaries == null || summaries.isEmpty()) {
            return;
        }

        // Build and send a simple overview
        provider.sendMessage(
            Text.literal("📝 Service activity while you were offline:")
                .formatted(Formatting.GOLD),
            false
        );

        for (Map.Entry<String, ServiceSummary> entry : summaries.entrySet()) {
            String serviceTag = entry.getKey();
            ServiceSummary summary = entry.getValue();

            // Skip lines with no changes (defensive)
            if (summary.newSubscribers <= 0 && summary.revenue <= 0) {
                continue;
            }

            StringBuilder lineText = new StringBuilder("• ")
                .append(serviceTag)
                .append(": ");

            boolean hasPrev = false;

            if (summary.newSubscribers > 0) {
                lineText.append(summary.newSubscribers)
                    .append(" new subscriber")
                    .append(summary.newSubscribers > 1 ? "s" : "");
                hasPrev = true;
            }

            if (summary.revenue > 0) {
                if (hasPrev) {
                    lineText.append(", ");
                }
                lineText.append("$")
                    .append(formatAmount(summary.revenue))
                    .append(" revenue");
            }

            provider.sendMessage(
                Text.literal(lineText.toString()).formatted(Formatting.WHITE),
                false
            );
        }

        // Clear and save after delivery
        providerSummaries.remove(providerUuid);
        saveNotifications();

        LOGGER.info("Delivered service activity notifications to provider {}", provider.getName().getString());
    }

    /**
     * Load notifications from disk at server start.
     */
    public void loadNotifications(MinecraftServer server) {
        if (server == null) {
            LOGGER.warn("Cannot load service notifications: server is null");
            return;
        }

        File file = FileUtil.getServerFile(server, NOTIFICATIONS_FILE);
        if (!file.exists()) {
            LOGGER.info("No service notifications file found");
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Gson gson = new GsonBuilder().create();
            Map<String, Object> raw =
                gson.fromJson(reader, Map.class);

            providerSummaries.clear();

            if (raw != null) {
                for (Map.Entry<String, Object> entry : raw.entrySet()) {
                    try {
                        UUID providerUuid = UUID.fromString(entry.getKey());
                        Map<String, ServiceSummary> serviceMap = new HashMap<>();

                        // Because of type erasure, we may need to reconstruct ServiceSummary manually
                        Map<?, ?> rawServices = (Map<?, ?>) entry.getValue();
                        if (rawServices != null) {
                            for (Map.Entry<?, ?> serviceEntry : rawServices.entrySet()) {
                                String tag = String.valueOf(serviceEntry.getKey());
                                Object val = serviceEntry.getValue();
                                if (val instanceof Map<?, ?> m) {
                                    ServiceSummary summary = new ServiceSummary();
                                    Object subs = m.get("newSubscribers");
                                    Object rev = m.get("revenue");
                                    if (subs instanceof Number n) {
                                        summary.newSubscribers = n.intValue();
                                    }
                                    if (rev instanceof Number n) {
                                        summary.revenue = n.doubleValue();
                                    }
                                    serviceMap.put(tag, summary);
                                }
                            }
                        }

                        providerSummaries.put(providerUuid, serviceMap);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Invalid provider UUID in service notifications file: {}", entry.getKey());
                    }
                }
            }

            LOGGER.info("Loaded service notifications for {} providers", providerSummaries.size());
        } catch (IOException e) {
            LOGGER.error("Failed to load service notifications: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Error parsing service notifications: {}", e.getMessage());
        }
    }

    /**
     * Save notifications to disk.
     */
    private void saveNotifications() {
        MinecraftServer server = CurrencyMod.getServer();
        if (server == null) {
            return;
        }

        File file = FileUtil.getServerFile(server, NOTIFICATIONS_FILE);

        try (FileWriter writer = new FileWriter(file)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            // Convert UUID keys to strings for JSON
            Map<String, Map<String, ServiceSummary>> serializable = new HashMap<>();
            for (Map.Entry<UUID, Map<String, ServiceSummary>> entry : providerSummaries.entrySet()) {
                serializable.put(entry.getKey().toString(), entry.getValue());
            }

            gson.toJson(serializable, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save service notifications: {}", e.getMessage());
        }
    }

    /**
     * Send a live notification to the provider if they're online.
     * @param providerUuid The provider's UUID
     * @param subscriberUuid The subscriber's UUID (null for revenue notifications)
     * @param subscriberName The subscriber's name (can be null, will be looked up if needed)
     * @param serviceTag The service tag
     * @param isSubscription True if this is a subscription notification, false for revenue
     * @param amount The revenue amount (only used for revenue notifications)
     * @return true if the provider was online and received the notification, false otherwise
     */
    private boolean notifyProviderIfOnline(UUID providerUuid, UUID subscriberUuid, String subscriberName, 
                                          String serviceTag, boolean isSubscription, double amount) {
        MinecraftServer server = CurrencyMod.getServer();
        if (server == null) {
            return false;
        }

        ServerPlayerEntity provider = server.getPlayerManager().getPlayer(providerUuid);
        if (provider == null) {
            return false;
        }

        // Provider is online, send live notification
        if (isSubscription) {
            // Subscription notification
            String nameToUse = subscriberName;
            if (nameToUse == null && subscriberUuid != null) {
                // Try to get name from online player first
                ServerPlayerEntity subscriber = server.getPlayerManager().getPlayer(subscriberUuid);
                if (subscriber != null) {
                    nameToUse = subscriber.getName().getString();
                } else {
                    // Try to get name from ServiceManager subscription
                    try {
                        ServiceManager serviceManager = ServiceManager.getInstance();
                        ServiceSubscription subscription = serviceManager.getSubscription(subscriberUuid, serviceTag);
                        if (subscription != null) {
                            nameToUse = subscription.getSubscriberName();
                        }
                    } catch (Exception e) {
                        // Fallback to UUID substring if lookup fails
                        nameToUse = subscriberUuid.toString().substring(0, 8) + "...";
                    }
                }
            }
            if (nameToUse == null) {
                nameToUse = "Unknown Player";
            }

            Text message = Text.literal("+ ").formatted(Formatting.GREEN)
                .append(Text.literal(nameToUse + " subscribed to your service ").formatted(Formatting.WHITE))
                .append(Text.literal(serviceTag).formatted(Formatting.YELLOW, Formatting.BOLD));

            provider.sendMessage(message, false);
        } else {
            // Revenue notification
            String formattedAmount = formatAmount(amount);
            Text message = Text.literal("$ ").formatted(Formatting.GOLD)
                .append(Text.literal("You received ").formatted(Formatting.WHITE))
                .append(Text.literal("$" + formattedAmount).formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal(" from service ").formatted(Formatting.WHITE))
                .append(Text.literal(serviceTag).formatted(Formatting.YELLOW, Formatting.BOLD));

            provider.sendMessage(message, false);
        }

        return true;
    }

    private String formatAmount(double amount) {
        if (amount == Math.floor(amount)) {
            return String.format("%.0f", amount);
        } else {
            return String.format("%.2f", amount);
        }
    }

    /**
     * Simple summary for a single service.
     */
    private static class ServiceSummary {
        int newSubscribers = 0;
        double revenue = 0.0;
    }
}


