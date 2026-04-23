package net.currencymod.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.currencymod.CurrencyMod;
import net.currencymod.economy.EconomyManager;
import net.currencymod.util.FileUtil;
import net.currencymod.util.UUIDAdapter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages services and subscriptions.
 */
public class ServiceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/ServiceManager");
    private static final String SERVICES_FILE = "currency_mod/services.json";
    private static final String SUBSCRIPTIONS_FILE = "currency_mod/service_subscriptions.json";
    private static ServiceManager instance;
    
    // Map of service tag -> Service
    private final Map<String, Service> services = new ConcurrentHashMap<>();
    
    // Map of subscriber UUID -> List of subscriptions
    private final Map<UUID, List<ServiceSubscription>> subscriptions = new ConcurrentHashMap<>();
    
    // Map of service tag -> List of subscriber UUIDs (for quick lookup)
    private final Map<String, List<UUID>> serviceSubscribers = new ConcurrentHashMap<>();
    
    // Track last processed day for each subscriber to avoid double-charging
    private final Map<UUID, Long> lastProcessedDay = new ConcurrentHashMap<>();
    
    private final Gson gson;
    
    /**
     * Gets the singleton instance.
     *
     * @return The ServiceManager instance
     */
    public static ServiceManager getInstance() {
        if (instance == null) {
            instance = new ServiceManager();
        }
        return instance;
    }
    
    /**
     * Private constructor for singleton pattern.
     */
    private ServiceManager() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(UUID.class, new UUIDAdapter())
            .create();
    }
    
    /**
     * Gets the current Minecraft day.
     *
     * @param server The Minecraft server
     * @return The current Minecraft day
     */
    public static long getCurrentDay(MinecraftServer server) {
        if (server == null || server.getOverworld() == null) {
            return 0;
        }
        return server.getOverworld().getTimeOfDay() / 24000L;
    }
    
    /**
     * Creates a new service.
     *
     * @param provider The service provider
     * @param tag The service tag (max 3 letters)
     * @param dailyPrice The daily price
     * @param contractDays The minimum contract days
     * @return True if successful, false if tag already exists
     */
    public boolean createService(ServerPlayerEntity provider, String tag, int dailyPrice, int contractDays) {
        if (tag == null || tag.length() > 3) {
            return false;
        }
        
        String upperTag = tag.toUpperCase();
        if (services.containsKey(upperTag)) {
            return false;
        }
        
        Service service = new Service(upperTag, provider.getUuid(), dailyPrice, contractDays);
        services.put(upperTag, service);
        serviceSubscribers.put(upperTag, new ArrayList<>());
        
        saveData();
        LOGGER.info("Service {} created by player {}", upperTag, provider.getName().getString());
        
        return true;
    }
    
    /**
     * Deletes a service.
     *
     * @param provider The service provider
     * @param tag The service tag
     * @return True if successful, false if service doesn't exist or provider doesn't own it
     */
    public boolean deleteService(ServerPlayerEntity provider, String tag) {
        String upperTag = tag.toUpperCase();
        Service service = services.get(upperTag);
        
        if (service == null) {
            return false;
        }
        
        if (!service.getProviderUuid().equals(provider.getUuid())) {
            return false;
        }
        
        // Unsubscribe all subscribers
        List<UUID> subscribers = serviceSubscribers.getOrDefault(upperTag, new ArrayList<>());
        for (UUID subscriberUuid : new ArrayList<>(subscribers)) {
            unsubscribeFromService(subscriberUuid, upperTag, false);
        }
        
        services.remove(upperTag);
        serviceSubscribers.remove(upperTag);
        
        saveData();
        LOGGER.info("Service {} deleted by provider {}", upperTag, provider.getName().getString());
        
        return true;
    }
    
    /**
     * Gets a service by tag.
     *
     * @param tag The service tag
     * @return The service, or null if not found
     */
    public Service getService(String tag) {
        return services.get(tag.toUpperCase());
    }
    
    /**
     * Updates a service's price or contract days (only affects new customers).
     *
     * @param provider The service provider
     * @param tag The service tag
     * @param field The field to update ("Price" or "ContractDays")
     * @param value The new value
     * @return True if successful
     */
    public boolean updateService(ServerPlayerEntity provider, String tag, String field, int value) {
        String upperTag = tag.toUpperCase();
        Service service = services.get(upperTag);
        
        if (service == null) {
            return false;
        }
        
        if (!service.getProviderUuid().equals(provider.getUuid())) {
            return false;
        }
        
        // Create a new service with updated values
        Service updatedService;
        if (field.equalsIgnoreCase("Price")) {
            updatedService = new Service(upperTag, provider.getUuid(), value, service.getContractDays(), service.getTotalRevenue());
        } else if (field.equalsIgnoreCase("ContractDays")) {
            updatedService = new Service(upperTag, provider.getUuid(), service.getDailyPrice(), value, service.getTotalRevenue());
        } else {
            return false;
        }

        // Apply updates
        services.put(upperTag, updatedService);

        // If price changed, update existing subscriptions so they are charged the new price
        if (field.equalsIgnoreCase("Price")) {
            for (Map.Entry<UUID, List<ServiceSubscription>> entry : subscriptions.entrySet()) {
                for (ServiceSubscription sub : entry.getValue()) {
                    if (sub.getServiceTag().equals(upperTag)) {
                        sub.setDailyPrice(value);
                    }
                }
            }
        }

        saveData();
        LOGGER.info("Service {} updated by provider {}: {} = {}", upperTag, provider.getName().getString(), field, value);
        
        return true;
    }
    
    /**
     * Subscribes a player to a service.
     *
     * @param subscriber The subscriber
     * @param tag The service tag
     * @return True if successful
     */
    public boolean subscribeToService(ServerPlayerEntity subscriber, String tag) {
        String upperTag = tag.toUpperCase();
        Service service = services.get(upperTag);
        
        if (service == null) {
            return false;
        }
        
        UUID subscriberUuid = subscriber.getUuid();
        UUID providerUuid = service.getProviderUuid();

        // Prevent providers from subscribing to their own service to avoid
        // money effectively cancelling out (debit and credit to same player)
        if (subscriberUuid.equals(providerUuid)) {
            subscriber.sendMessage(Text.literal("You cannot subscribe to your own service.")
                .formatted(Formatting.RED), false);
            return false;
        }
        
        // Check if already subscribed
        if (hasSubscription(subscriberUuid, upperTag)) {
            return false;
        }
        
        MinecraftServer server = subscriber.getServer();
        if (server == null) {
            return false;
        }
        
        long currentDay = getCurrentDay(server);
        int dailyPrice = service.getDailyPrice();
        int contractDays = service.getContractDays();
        
        // Create subscription
        String subscriberName = subscriber.getName().getString();
        ServiceSubscription subscription = new ServiceSubscription(
            subscriberUuid, subscriberName, upperTag, currentDay, contractDays, dailyPrice
        );
        
        // Handle initial payment
        EconomyManager economyManager = CurrencyMod.getEconomyManager();
        double balance = economyManager.getBalance(subscriberUuid);
        
        double revenue = 0;

        if (contractDays == 0) {
            // No contract: charge for first 10 days upfront
            int upfrontDays = 10;
            int upfrontCost = dailyPrice * upfrontDays;
            
            if (balance < upfrontCost) {
                return false;
            }
            
            // Charge subscriber and pay provider
            double newBalance = economyManager.removeBalance(subscriberUuid, upfrontCost);
            if (newBalance < 0) {
                return false;
            }
            economyManager.addBalance(providerUuid, upfrontCost);
            subscription.setLastChargedDay(currentDay);
            // Set daysPaid to 10 to represent prepaid days
            for (int i = 0; i < upfrontDays; i++) {
                subscription.incrementDaysPaid();
            }
            revenue = upfrontCost;
        } else {
            // Has contract: charge for first day
            if (balance < dailyPrice) {
                return false;
            }
            
            // Charge subscriber and pay provider
            double newBalance = economyManager.removeBalance(subscriberUuid, dailyPrice);
            if (newBalance < 0) {
                return false;
            }
            economyManager.addBalance(providerUuid, dailyPrice);
            subscription.incrementDaysPaid();
            subscription.setLastChargedDay(currentDay);
            revenue = dailyPrice;
        }
        
        // Add subscription
        subscriptions.computeIfAbsent(subscriberUuid, k -> new ArrayList<>()).add(subscription);
        serviceSubscribers.computeIfAbsent(upperTag, k -> new ArrayList<>()).add(subscriberUuid);
        
        // Add initial revenue
        service.addRevenue(revenue);

        // Record provider notification for new subscriber and initial revenue
        ServiceNotificationManager.getInstance().recordSubscription(providerUuid, subscriberUuid, upperTag);
        if (revenue > 0) {
            ServiceNotificationManager.getInstance().recordRevenue(providerUuid, upperTag, revenue);
        }
        
        saveData();
        LOGGER.info("Player {} subscribed to service {}", subscriber.getName().getString(), upperTag);
        
        return true;
    }
    
    /**
     * Unsubscribes a player from a service.
     *
     * @param subscriber The subscriber
     * @param tag The service tag
     * @return True if successful, false if not subscribed or contract not fulfilled
     */
    public boolean unsubscribeFromService(ServerPlayerEntity subscriber, String tag) {
        return unsubscribeFromService(subscriber.getUuid(), tag.toUpperCase(), true);
    }
    
    /**
     * Internal unsubscribe method.
     *
     * @param subscriberUuid The subscriber UUID
     * @param tag The service tag
     * @param checkContract Whether to check contract fulfillment
     * @return True if successful
     */
    private boolean unsubscribeFromService(UUID subscriberUuid, String tag, boolean checkContract) {
        String upperTag = tag.toUpperCase();
        List<ServiceSubscription> playerSubscriptions = subscriptions.get(subscriberUuid);
        
        if (playerSubscriptions == null) {
            return false;
        }
        
        ServiceSubscription subscription = null;
        for (ServiceSubscription sub : playerSubscriptions) {
            if (sub.getServiceTag().equals(upperTag)) {
                subscription = sub;
                break;
            }
        }
        
        if (subscription == null) {
            return false;
        }
        
        // Check contract if needed
        if (checkContract && !subscription.isContractFulfilled()) {
            // Calculate pro-rated charge
            int remainingDays = subscription.getRemainingContractDays();
            int proRatedCharge = remainingDays * subscription.getDailyPrice();
            
            EconomyManager economyManager = CurrencyMod.getEconomyManager();
            double balance = economyManager.getBalance(subscriberUuid);
            
            if (balance < proRatedCharge) {
                return false; // Not enough money to break contract
            }
            
            // Charge pro-rated amount and pay provider
            Service service = services.get(upperTag);
            if (service != null) {
                UUID providerUuid = service.getProviderUuid();
                
                double newBalance = economyManager.removeBalance(subscriberUuid, proRatedCharge);
                if (newBalance < 0) {
                    return false;
                }
                economyManager.addBalance(providerUuid, proRatedCharge);

                // Add revenue to service stats
                service.addRevenue(proRatedCharge);
            }
        }
        
        // Remove subscription
        playerSubscriptions.remove(subscription);
        if (playerSubscriptions.isEmpty()) {
            subscriptions.remove(subscriberUuid);
        }
        
        List<UUID> serviceSubs = serviceSubscribers.get(upperTag);
        if (serviceSubs != null) {
            serviceSubs.remove(subscriberUuid);
            if (serviceSubs.isEmpty()) {
                serviceSubscribers.remove(upperTag);
            }
        }
        
        saveData();
        LOGGER.info("Player {} unsubscribed from service {}", subscriberUuid, upperTag);
        
        return true;
    }
    
    /**
     * Checks if a player has a subscription to a service.
     *
     * @param subscriberUuid The subscriber UUID
     * @param tag The service tag
     * @return True if subscribed
     */
    public boolean hasSubscription(UUID subscriberUuid, String tag) {
        List<ServiceSubscription> playerSubscriptions = subscriptions.get(subscriberUuid);
        if (playerSubscriptions == null) {
            return false;
        }
        
        return playerSubscriptions.stream()
            .anyMatch(sub -> sub.getServiceTag().equals(tag.toUpperCase()));
    }
    
    /**
     * Gets a player's subscription to a service.
     *
     * @param subscriberUuid The subscriber UUID
     * @param tag The service tag
     * @return The subscription, or null if not found
     */
    public ServiceSubscription getSubscription(UUID subscriberUuid, String tag) {
        List<ServiceSubscription> playerSubscriptions = subscriptions.get(subscriberUuid);
        if (playerSubscriptions == null) {
            return null;
        }
        
        return playerSubscriptions.stream()
            .filter(sub -> sub.getServiceTag().equals(tag.toUpperCase()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Gets all subscriptions for a player.
     *
     * @param subscriberUuid The subscriber UUID
     * @return List of subscriptions
     */
    public List<ServiceSubscription> getPlayerSubscriptions(UUID subscriberUuid) {
        return subscriptions.getOrDefault(subscriberUuid, new ArrayList<>());
    }
    
    /**
     * Gets all subscribers for a service.
     *
     * @param tag The service tag
     * @return List of subscriber UUIDs
     */
    public List<UUID> getServiceSubscribers(String tag) {
        return new ArrayList<>(serviceSubscribers.getOrDefault(tag.toUpperCase(), new ArrayList<>()));
    }
    
    /**
     * Processes daily charges for all online subscribers.
     * Should be called once per Minecraft day.
     *
     * @param server The Minecraft server
     */
    public void processDailyCharges(MinecraftServer server) {
        if (server == null) {
            return;
        }
        
        long currentDay = getCurrentDay(server);
        
        // Process each online player
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID playerUuid = player.getUuid();
            
            // Skip if already processed today
            if (lastProcessedDay.getOrDefault(playerUuid, -1L) == currentDay) {
                continue;
            }
            
            List<ServiceSubscription> playerSubscriptions = subscriptions.get(playerUuid);
            if (playerSubscriptions == null || playerSubscriptions.isEmpty()) {
                continue;
            }
            
            EconomyManager economyManager = CurrencyMod.getEconomyManager();
            double balance = economyManager.getBalance(playerUuid);
            
            // Track per-service charges for this player today
            Map<String, Integer> dailyChargesByService = new HashMap<>();

            // Process each subscription
            for (ServiceSubscription subscription : new ArrayList<>(playerSubscriptions)) {
                String serviceTag = subscription.getServiceTag();
                Service service = services.get(serviceTag);
                
                if (service == null) {
                    // Service was deleted, remove subscription
                    unsubscribeFromService(playerUuid, serviceTag, false);
                    continue;
                }
                
                int dailyPrice = subscription.getDailyPrice();

                // If subscriber is also the provider (from legacy data), skip charges
                // to avoid no-op money movement and confusing messaging
                if (playerUuid.equals(service.getProviderUuid())) {
                    LOGGER.debug("Skipping daily charge for service {} because subscriber is provider (legacy subscription)", serviceTag);
                    continue;
                }
                
                // Check if subscription has prepaid days (for non-contract services)
                if (subscription.getContractDays() == 0 && subscription.getDaysPaid() > 0) {
                    // Use a prepaid day
                    subscription.decrementDaysPaid();
                    subscription.setLastChargedDay(currentDay);
                    LOGGER.debug("Player {} used a prepaid day for service {} ({} remaining)", 
                        player.getName().getString(), serviceTag, subscription.getDaysPaid());
                    continue;
                }
                
                // Charge for the day (either contract service or non-contract after prepaid days)
                if (balance >= dailyPrice) {
                    UUID providerUuid = service.getProviderUuid();
                    
                    double newBalance = economyManager.removeBalance(playerUuid, dailyPrice);
                    if (newBalance < 0) {
                        // Treat as insufficient funds / payment failure
                        LOGGER.info("Payment failed for player {} and service {}, unsubscribing",
                            player.getName().getString(), serviceTag);
                        player.sendMessage(Text.literal("You were unsubscribed from service ")
                            .append(Text.literal(serviceTag).formatted(Formatting.YELLOW))
                            .append(Text.literal(" due to payment failure.").formatted(Formatting.RED)), false);
                        unsubscribeFromService(playerUuid, serviceTag, false);
                        continue;
                    }
                    economyManager.addBalance(providerUuid, dailyPrice);
                    balance = newBalance;
                    
                    // Only increment daysPaid for contract services (for contract tracking)
                    if (subscription.getContractDays() > 0) {
                        subscription.incrementDaysPaid();
                    }
                    subscription.setLastChargedDay(currentDay);
                    
                    // Add revenue to service
                    service.addRevenue(dailyPrice);

                    // Record provider notification for daily revenue
                    ServiceNotificationManager.getInstance().recordRevenue(providerUuid, serviceTag, dailyPrice);

                    // Track for summary message
                    dailyChargesByService.merge(serviceTag, dailyPrice, Integer::sum);
                    
                    LOGGER.debug("Charged player {} ${} for service {}", 
                        player.getName().getString(), dailyPrice, serviceTag);
                } else {
                    // Not enough money - unsubscribe
                    LOGGER.info("Player {} cannot afford service {} daily charge, unsubscribing", 
                        player.getName().getString(), serviceTag);
                    
                    player.sendMessage(Text.literal("You were unsubscribed from service ")
                        .append(Text.literal(serviceTag).formatted(Formatting.YELLOW))
                        .append(Text.literal(" due to insufficient funds.").formatted(Formatting.RED)), false);
                    
                    unsubscribeFromService(playerUuid, serviceTag, false);
                }
            }
            
            lastProcessedDay.put(playerUuid, currentDay);

            // Send a brief overview message of today's service charges for this player
            if (!dailyChargesByService.isEmpty()) {
                Text header = Text.literal("Service subscriptions charged today:")
                    .formatted(Formatting.WHITE, Formatting.BOLD);
                player.sendMessage(header, false);

                for (Map.Entry<String, Integer> entry : dailyChargesByService.entrySet()) {
                    String serviceTag = entry.getKey();
                    int totalCharged = entry.getValue();

                    MutableText line = Text.literal(" - ")
                        .formatted(Formatting.WHITE)
                        .append(Text.literal(serviceTag).formatted(Formatting.YELLOW, Formatting.BOLD))
                        .append(Text.literal(": ").formatted(Formatting.WHITE))
                        .append(Text.literal("$" + totalCharged).formatted(Formatting.GOLD));

                    player.sendMessage(line, false);
                }
            }
        }
        
        saveData();
    }
    
    /**
     * Loads service data from files.
     *
     * @param server The Minecraft server
     */
    public void loadData(MinecraftServer server) {
        if (server == null) {
            LOGGER.warn("Cannot load service data: server is null");
            return;
        }
        
        // Load services
        File servicesFile = FileUtil.getServerFile(server, SERVICES_FILE);
        if (servicesFile.exists()) {
            try (FileReader reader = new FileReader(servicesFile)) {
                JsonObject rootObject = JsonParser.parseReader(reader).getAsJsonObject();
                
                services.clear();
                serviceSubscribers.clear();
                
                for (String tag : rootObject.keySet()) {
                    JsonObject serviceData = rootObject.getAsJsonObject(tag);
                    UUID providerUuid = UUID.fromString(serviceData.get("providerUuid").getAsString());
                    int dailyPrice = serviceData.get("dailyPrice").getAsInt();
                    int contractDays = serviceData.get("contractDays").getAsInt();
                    double totalRevenue = serviceData.has("totalRevenue") ? 
                        serviceData.get("totalRevenue").getAsDouble() : 0.0;
                    
                    Service service = new Service(tag, providerUuid, dailyPrice, contractDays, totalRevenue);
                    services.put(tag, service);
                    serviceSubscribers.put(tag, new ArrayList<>());
                }
                
                LOGGER.info("Loaded {} services", services.size());
            } catch (Exception e) {
                LOGGER.error("Failed to load services data: {}", e.getMessage());
            }
        }
        
        // Load subscriptions
        File subscriptionsFile = FileUtil.getServerFile(server, SUBSCRIPTIONS_FILE);
        if (subscriptionsFile.exists()) {
            try (FileReader reader = new FileReader(subscriptionsFile)) {
                JsonObject rootObject = JsonParser.parseReader(reader).getAsJsonObject();
                
                subscriptions.clear();
                
                for (String subscriberUuidStr : rootObject.keySet()) {
                    UUID subscriberUuid = UUID.fromString(subscriberUuidStr);
                    JsonObject subscriberData = rootObject.getAsJsonObject(subscriberUuidStr);
                    
                    List<ServiceSubscription> playerSubscriptions = new ArrayList<>();
                    
                    if (subscriberData.has("subscriptions")) {
                        for (var subscriptionElement : subscriberData.getAsJsonArray("subscriptions")) {
                            JsonObject subData = subscriptionElement.getAsJsonObject();
                            String serviceTag = subData.get("serviceTag").getAsString();
                            long subscriptionDate = subData.get("subscriptionDate").getAsLong();
                            int contractDays = subData.get("contractDays").getAsInt();
                            int dailyPrice = subData.get("dailyPrice").getAsInt();
                            int daysPaid = subData.has("daysPaid") ? subData.get("daysPaid").getAsInt() : 0;
                            long lastChargedDay = subData.has("lastChargedDay") ? 
                                subData.get("lastChargedDay").getAsLong() : -1;
                            String subscriberName = subData.has("subscriberName") ? 
                                subData.get("subscriberName").getAsString() : "Unknown Player";
                            
                            ServiceSubscription subscription = new ServiceSubscription(
                                subscriberUuid, subscriberName, serviceTag, subscriptionDate, contractDays, 
                                dailyPrice, daysPaid, lastChargedDay
                            );
                            
                            playerSubscriptions.add(subscription);
                            
                            // Update service subscribers map
                            serviceSubscribers.computeIfAbsent(serviceTag, k -> new ArrayList<>())
                                .add(subscriberUuid);
                        }
                    }
                    
                    if (!playerSubscriptions.isEmpty()) {
                        subscriptions.put(subscriberUuid, playerSubscriptions);
                    }
                }
                
                LOGGER.info("Loaded subscriptions for {} players", subscriptions.size());
            } catch (Exception e) {
                LOGGER.error("Failed to load subscriptions data: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Saves service data to files.
     */
    public void saveData() {
        MinecraftServer server = CurrencyMod.getServer();
        if (server == null) {
            LOGGER.warn("Cannot save service data: server is null");
            return;
        }
        
        // Save services
        File servicesFile = FileUtil.getServerFile(server, SERVICES_FILE);
        try (FileWriter writer = new FileWriter(servicesFile)) {
            JsonObject rootObject = new JsonObject();
            
            for (Service service : services.values()) {
                JsonObject serviceData = new JsonObject();
                serviceData.addProperty("providerUuid", service.getProviderUuid().toString());
                serviceData.addProperty("dailyPrice", service.getDailyPrice());
                serviceData.addProperty("contractDays", service.getContractDays());
                serviceData.addProperty("totalRevenue", service.getTotalRevenue());
                
                rootObject.add(service.getTag(), serviceData);
            }
            
            gson.toJson(rootObject, writer);
            LOGGER.debug("Saved {} services", services.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save services data: {}", e.getMessage());
        }
        
        // Save subscriptions
        File subscriptionsFile = FileUtil.getServerFile(server, SUBSCRIPTIONS_FILE);
        try (FileWriter writer = new FileWriter(subscriptionsFile)) {
            JsonObject rootObject = new JsonObject();
            
            for (Map.Entry<UUID, List<ServiceSubscription>> entry : subscriptions.entrySet()) {
                UUID subscriberUuid = entry.getKey();
                List<ServiceSubscription> playerSubscriptions = entry.getValue();
                
                JsonObject subscriberData = new JsonObject();
                var subscriptionsArray = new com.google.gson.JsonArray();
                
                for (ServiceSubscription subscription : playerSubscriptions) {
                    JsonObject subData = new JsonObject();
                    subData.addProperty("serviceTag", subscription.getServiceTag());
                    subData.addProperty("subscriberName", subscription.getSubscriberName());
                    subData.addProperty("subscriptionDate", subscription.getSubscriptionDate());
                    subData.addProperty("contractDays", subscription.getContractDays());
                    subData.addProperty("dailyPrice", subscription.getDailyPrice());
                    subData.addProperty("daysPaid", subscription.getDaysPaid());
                    subData.addProperty("lastChargedDay", subscription.getLastChargedDay());
                    
                    subscriptionsArray.add(subData);
                }
                
                subscriberData.add("subscriptions", subscriptionsArray);
                rootObject.add(subscriberUuid.toString(), subscriberData);
            }
            
            gson.toJson(rootObject, writer);
            LOGGER.debug("Saved subscriptions for {} players", subscriptions.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save subscriptions data: {}", e.getMessage());
        }
    }
}

