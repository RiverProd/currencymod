package net.currencymod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.currencymod.CurrencyMod;
import net.currencymod.economy.EconomyManager;
import net.currencymod.services.Service;
import net.currencymod.services.ServiceManager;
import net.currencymod.services.ServiceSubscription;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Command handler for service management.
 */
public class ServiceCommand {
    
    /**
     * Suggestion provider for update fields.
     */
    private static final SuggestionProvider<ServerCommandSource> UPDATE_FIELD_SUGGESTIONS = 
        (context, builder) -> {
            builder.suggest("Price");
            builder.suggest("ContractDays");
            return builder.buildFuture();
        };
    
    /**
     * Register the service command.
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, 
                               CommandRegistryAccess registryAccess, 
                               CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            literal("service")
                .executes(ServiceCommand::showHelp)
                .then(literal("create")
                    .then(argument("tag", StringArgumentType.word())
                        .then(argument("dailyPrice", IntegerArgumentType.integer(1))
                            .then(argument("contractDays", IntegerArgumentType.integer(0))
                                .executes(ServiceCommand::createService)
                            )
                        )
                    )
                )
                .then(literal("delete")
                    .then(argument("tag", StringArgumentType.word())
                        .executes(ServiceCommand::deleteService)
                        .then(literal("confirm")
                            .executes(context -> confirmDeleteService(context, StringArgumentType.getString(context, "tag")))
                        )
                    )
                )
                .then(literal("view")
                    .then(argument("tag", StringArgumentType.word())
                        .executes(context -> viewService(context, StringArgumentType.getString(context, "tag")))
                    )
                )
                .then(literal("update")
                    .then(argument("tag", StringArgumentType.word())
                        .then(argument("field", StringArgumentType.word())
                            .suggests(UPDATE_FIELD_SUGGESTIONS)
                            .then(argument("value", IntegerArgumentType.integer(1))
                                .executes(ServiceCommand::updateService)
                            )
                        )
                    )
                )
                .then(literal("subscribe")
                    .then(argument("tag", StringArgumentType.word())
                        .executes(context -> subscribeToService(context, StringArgumentType.getString(context, "tag")))
                        .then(literal("confirm")
                            .executes(context -> confirmSubscribe(context, StringArgumentType.getString(context, "tag")))
                        )
                    )
                )
                .then(literal("unsubscribe")
                    .then(argument("tag", StringArgumentType.word())
                        .executes(context -> unsubscribeFromService(context, StringArgumentType.getString(context, "tag")))
                        .then(literal("confirm")
                            .executes(context -> confirmUnsubscribe(context, StringArgumentType.getString(context, "tag")))
                        )
                    )
                )
                .then(literal("subscriptions")
                    .executes(ServiceCommand::listSubscriptions)
                )
        );
    }
    
    /**
     * Show help message.
     */
    private static int showHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        MutableText help = Text.literal("=== Service Commands ===\n")
            .formatted(Formatting.GOLD, Formatting.BOLD);
        
        help.append(Text.literal("Provider Commands:\n").formatted(Formatting.WHITE));
        help.append(Text.literal("  /service create <Tag> <DailyPrice> <ContractDays>\n").formatted(Formatting.WHITE));
        help.append(Text.literal("  /service delete <Tag>\n").formatted(Formatting.WHITE));
        help.append(Text.literal("  /service view <Tag>\n").formatted(Formatting.WHITE));
        help.append(Text.literal("  /service update <Tag> <Price|ContractDays> <Value>\n").formatted(Formatting.WHITE));
        
        help.append(Text.literal("\nSubscriber Commands:\n").formatted(Formatting.WHITE));
        help.append(Text.literal("  /service subscribe <Tag>\n").formatted(Formatting.WHITE));
        help.append(Text.literal("  /service unsubscribe <Tag>\n").formatted(Formatting.WHITE));
        help.append(Text.literal("  /service subscriptions\n").formatted(Formatting.WHITE));
        
        source.sendFeedback(() -> help, false);
        return 1;
    }
    
    /**
     * Create a service.
     */
    private static int createService(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        String tag = StringArgumentType.getString(context, "tag");
        int dailyPrice = IntegerArgumentType.getInteger(context, "dailyPrice");
        int contractDays = IntegerArgumentType.getInteger(context, "contractDays");
        
        if (tag.length() > 3) {
            source.sendError(Text.literal("Service tag must be 3 letters or less"));
            return 0;
        }
        
        ServiceManager manager = ServiceManager.getInstance();
        boolean success = manager.createService(player, tag, dailyPrice, contractDays);
        
        if (success) {
            MutableText message = Text.literal("Service ")
                .formatted(Formatting.WHITE)
                .append(Text.literal(tag.toUpperCase()).formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal(" created successfully!\n").formatted(Formatting.WHITE));
            message.append(Text.literal("  Daily Price: ").formatted(Formatting.WHITE))
                .append(Text.literal("$" + dailyPrice).formatted(Formatting.GOLD));
            message.append(Text.literal("\n  Contract Days: ").formatted(Formatting.WHITE))
                .append(Text.literal(contractDays == 0 ? "None" : String.valueOf(contractDays))
                    .formatted(Formatting.AQUA));
            
            source.sendFeedback(() -> message, false);
            return 1;
        } else {
            source.sendError(Text.literal("Failed to create service. Tag may already exist."));
            return 0;
        }
    }
    
    /**
     * Delete a service (with confirmation).
     */
    private static int deleteService(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        String tag = StringArgumentType.getString(context, "tag");
        ServiceManager manager = ServiceManager.getInstance();
        Service service = manager.getService(tag);
        
        if (service == null) {
            source.sendError(Text.literal("Service not found"));
            return 0;
        }
        
        if (!service.getProviderUuid().equals(player.getUuid())) {
            source.sendError(Text.literal("You can only delete your own services"));
            return 0;
        }
        
        List<UUID> subscribers = manager.getServiceSubscribers(tag);
        int subscriberCount = subscribers.size();
        
        MutableText message = Text.literal("⚠️ Warning: ").formatted(Formatting.RED, Formatting.BOLD)
            .append(Text.literal("Deleting service ").formatted(Formatting.WHITE))
            .append(Text.literal(tag.toUpperCase()).formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal(" will unsubscribe ").formatted(Formatting.WHITE))
            .append(Text.literal(String.valueOf(subscriberCount)).formatted(Formatting.RED))
            .append(Text.literal(" subscriber(s).\n").formatted(Formatting.WHITE));
        message.append(Text.literal("Type ").formatted(Formatting.WHITE))
            .append(Text.literal("/service delete " + tag + " confirm").formatted(Formatting.RED))
            .append(Text.literal(" to confirm.").formatted(Formatting.WHITE));
        
        source.sendFeedback(() -> message, false);
        return 1;
    }
    
    /**
     * Confirm and delete a service.
     */
    private static int confirmDeleteService(CommandContext<ServerCommandSource> context, String tag) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        ServiceManager manager = ServiceManager.getInstance();
        boolean success = manager.deleteService(player, tag);
        
        if (success) {
            source.sendFeedback(() -> Text.literal("Service ")
                .formatted(Formatting.WHITE)
                .append(Text.literal(tag.toUpperCase()).formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal(" deleted successfully.").formatted(Formatting.WHITE)), false);
            return 1;
        } else {
            source.sendError(Text.literal("Failed to delete service. You may not own this service."));
            return 0;
        }
    }
    
    /**
     * View a service.
     */
    private static int viewService(CommandContext<ServerCommandSource> context, String tag) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        ServiceManager manager = ServiceManager.getInstance();
        Service service = manager.getService(tag);
        
        if (service == null) {
            source.sendError(Text.literal("Service not found"));
            return 0;
        }
        
        if (!service.getProviderUuid().equals(player.getUuid())) {
            source.sendError(Text.literal("You can only view your own services"));
            return 0;
        }
        
        List<UUID> subscribers = manager.getServiceSubscribers(tag);
        
        MutableText message = Text.literal("=== Service ").formatted(Formatting.GOLD, Formatting.BOLD)
            .append(Text.literal(tag.toUpperCase()).formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal(" ===\n").formatted(Formatting.GOLD, Formatting.BOLD));
        
        message.append(Text.literal("Daily Price: ").formatted(Formatting.WHITE))
            .append(Text.literal("$" + service.getDailyPrice()).formatted(Formatting.GOLD));
        message.append(Text.literal("\nContract Days: ").formatted(Formatting.WHITE))
            .append(Text.literal(service.getContractDays() == 0 ? "None" : String.valueOf(service.getContractDays()))
                .formatted(Formatting.AQUA));
        message.append(Text.literal("\nTotal Revenue: ").formatted(Formatting.WHITE))
            .append(Text.literal("$" + String.format("%.2f", service.getTotalRevenue())).formatted(Formatting.GOLD));
        message.append(Text.literal("\nSubscribers: ").formatted(Formatting.WHITE))
            .append(Text.literal(String.valueOf(subscribers.size())).formatted(Formatting.YELLOW));
        
        if (!subscribers.isEmpty()) {
            message.append(Text.literal("\n\nSubscriber List:").formatted(Formatting.WHITE));
            for (UUID subscriberUuid : subscribers) {
                ServiceSubscription subscription = manager.getSubscription(subscriberUuid, tag);
                if (subscription != null) {
                    // Use stored username, or try to get current username if online
                    String playerName = subscription.getSubscriberName();
                    ServerPlayerEntity onlinePlayer = source.getServer().getPlayerManager().getPlayer(subscriberUuid);
                    if (onlinePlayer != null) {
                        // Update stored name if player is online (in case they changed their name)
                        playerName = onlinePlayer.getName().getString();
                    }
                    
                    message.append(Text.literal("\n  • ").formatted(Formatting.WHITE))
                        .append(Text.literal(playerName).formatted(Formatting.AQUA))
                        .append(Text.literal(" - Days Paid: ").formatted(Formatting.WHITE))
                        .append(Text.literal(String.valueOf(subscription.getDaysPaid())).formatted(Formatting.AQUA));
                    
                    if (subscription.getContractDays() > 0) {
                        int remaining = subscription.getRemainingContractDays();
                        message.append(Text.literal(" (Contract: ").formatted(Formatting.GRAY))
                            .append(Text.literal(String.valueOf(remaining)).formatted(Formatting.YELLOW))
                            .append(Text.literal(" days remaining)").formatted(Formatting.GRAY));
                    }
                }
            }
        }
        
        source.sendFeedback(() -> message, false);
        return 1;
    }
    
    /**
     * Update a service.
     */
    private static int updateService(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        String tag = StringArgumentType.getString(context, "tag");
        String field = StringArgumentType.getString(context, "field");
        int value = IntegerArgumentType.getInteger(context, "value");
        
        if (!field.equalsIgnoreCase("Price") && !field.equalsIgnoreCase("ContractDays")) {
            source.sendError(Text.literal("Field must be 'Price' or 'ContractDays'"));
            return 0;
        }
        
        ServiceManager manager = ServiceManager.getInstance();
        boolean success = manager.updateService(player, tag, field, value);
        
        if (success) {
            MutableText message = Text.literal("Service ")
                .formatted(Formatting.WHITE)
                .append(Text.literal(tag.toUpperCase()).formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal(" updated successfully!\n").formatted(Formatting.WHITE));
            message.append(Text.literal("  ").formatted(Formatting.WHITE))
                .append(Text.literal(field).formatted(Formatting.AQUA))
                .append(Text.literal(" set to: ").formatted(Formatting.WHITE))
                .append(Text.literal(String.valueOf(value)).formatted(Formatting.GOLD));

            if (field.equalsIgnoreCase("Price")) {
                message.append(Text.literal("\n  Note: ").formatted(Formatting.WHITE))
                    .append(Text.literal("This price now applies to all current and future subscribers.")
                        .formatted(Formatting.YELLOW));
            } else {
                message.append(Text.literal("\n  Note: ").formatted(Formatting.WHITE))
                    .append(Text.literal("This contract change only affects new subscribers.")
                        .formatted(Formatting.YELLOW));
            }
            
            source.sendFeedback(() -> message, false);
            return 1;
        } else {
            source.sendError(Text.literal("Failed to update service. You may not own this service."));
            return 0;
        }
    }
    
    /**
     * Subscribe to a service (with confirmation).
     */
    private static int subscribeToService(CommandContext<ServerCommandSource> context, String tag) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        ServiceManager manager = ServiceManager.getInstance();
        Service service = manager.getService(tag);
        
        if (service == null) {
            source.sendError(Text.literal("Service not found"));
            return 0;
        }
        
        if (manager.hasSubscription(player.getUuid(), tag)) {
            source.sendError(Text.literal("You are already subscribed to this service"));
            return 0;
        }
        
        EconomyManager economyManager = CurrencyMod.getEconomyManager();
        double balance = economyManager.getBalance(player.getUuid());
        
        int dailyPrice = service.getDailyPrice();
        int contractDays = service.getContractDays();
        
        MutableText message = Text.literal("Subscribe to service ").formatted(Formatting.WHITE)
            .append(Text.literal(tag.toUpperCase()).formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal("?\n").formatted(Formatting.WHITE));
        
        if (contractDays == 0) {
            int upfrontCost = dailyPrice * 10;
            message.append(Text.literal("  You will pay ").formatted(Formatting.WHITE))
                .append(Text.literal("$" + upfrontCost).formatted(Formatting.GOLD))
                .append(Text.literal(" upfront for the first 10 days.\n").formatted(Formatting.WHITE));
            message.append(Text.literal("  After 10 days, you will be charged ").formatted(Formatting.WHITE))
                .append(Text.literal("$" + dailyPrice).formatted(Formatting.GOLD))
                .append(Text.literal(" per day you are online.\n").formatted(Formatting.WHITE));
            
            if (balance < upfrontCost) {
                message.append(Text.literal("\n  ⚠️ Insufficient funds! ").formatted(Formatting.RED))
                    .append(Text.literal("You need ").formatted(Formatting.RED))
                    .append(Text.literal("$" + upfrontCost).formatted(Formatting.RED, Formatting.BOLD))
                    .append(Text.literal(" but only have ").formatted(Formatting.RED))
                    .append(Text.literal("$" + String.format("%.2f", balance)).formatted(Formatting.RED, Formatting.BOLD));
            }
        } else {
            message.append(Text.literal("  Daily Price: ").formatted(Formatting.WHITE))
                .append(Text.literal("$" + dailyPrice).formatted(Formatting.GOLD))
                .append(Text.literal(" per day you are online.\n").formatted(Formatting.WHITE));
            message.append(Text.literal("  Contract: Minimum ").formatted(Formatting.WHITE))
                .append(Text.literal(String.valueOf(contractDays)).formatted(Formatting.AQUA))
                .append(Text.literal(" days. Breaking early will result in a pro-rated charge.\n").formatted(Formatting.WHITE));
            message.append(Text.literal("  Example: If you've paid for 10/").formatted(Formatting.WHITE))
                .append(Text.literal(String.valueOf(contractDays)).formatted(Formatting.AQUA))
                .append(Text.literal(" days, you'll pay the remaining ").formatted(Formatting.WHITE))
                .append(Text.literal(String.valueOf(contractDays - 10)).formatted(Formatting.YELLOW))
                .append(Text.literal(" days worth.\n").formatted(Formatting.WHITE));
            
            if (balance < dailyPrice) {
                message.append(Text.literal("\n  ⚠️ Insufficient funds! ").formatted(Formatting.RED))
                    .append(Text.literal("You need ").formatted(Formatting.RED))
                    .append(Text.literal("$" + dailyPrice).formatted(Formatting.RED, Formatting.BOLD))
                    .append(Text.literal(" but only have ").formatted(Formatting.RED))
                    .append(Text.literal("$" + String.format("%.2f", balance)).formatted(Formatting.RED, Formatting.BOLD));
            }
        }
        
        message.append(Text.literal("\nType ").formatted(Formatting.WHITE))
            .append(Text.literal("/service subscribe " + tag + " confirm").formatted(Formatting.GREEN))
            .append(Text.literal(" to confirm.").formatted(Formatting.WHITE));
        
        source.sendFeedback(() -> message, false);
        return 1;
    }
    
    /**
     * Confirm subscription.
     */
    private static int confirmSubscribe(CommandContext<ServerCommandSource> context, String tag) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        ServiceManager manager = ServiceManager.getInstance();
        boolean success = manager.subscribeToService(player, tag);
        
        if (success) {
            Service service = manager.getService(tag);
            int contractDays = service != null ? service.getContractDays() : 0;
            
            MutableText message = Text.literal("✓ Subscribed to service ").formatted(Formatting.GREEN)
                .append(Text.literal(tag.toUpperCase()).formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal("!\n").formatted(Formatting.GREEN));
            
            if (contractDays == 0) {
                message.append(Text.literal("  You've been charged for the first 10 days upfront.\n").formatted(Formatting.GRAY))
                    .append(Text.literal("  You will continue to be charged daily after day 10.").formatted(Formatting.GRAY));
            } else {
                message.append(Text.literal("  You've been charged for the first day.\n").formatted(Formatting.GRAY))
                    .append(Text.literal("  Contract: ").formatted(Formatting.GRAY))
                    .append(Text.literal(String.valueOf(contractDays)).formatted(Formatting.AQUA))
                    .append(Text.literal(" days minimum.").formatted(Formatting.GRAY));
            }
            
            source.sendFeedback(() -> message, false);
            return 1;
        } else {
            source.sendError(Text.literal("Failed to subscribe. You may already be subscribed or have insufficient funds."));
            return 0;
        }
    }
    
    /**
     * Unsubscribe from a service (with confirmation).
     */
    private static int unsubscribeFromService(CommandContext<ServerCommandSource> context, String tag) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        ServiceManager manager = ServiceManager.getInstance();
        ServiceSubscription subscription = manager.getSubscription(player.getUuid(), tag);
        
        if (subscription == null) {
            source.sendError(Text.literal("You are not subscribed to this service"));
            return 0;
        }
        
        MutableText message = Text.literal("Unsubscribe from service ").formatted(Formatting.WHITE)
            .append(Text.literal(tag.toUpperCase()).formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal("?\n").formatted(Formatting.WHITE));
        
        if (!subscription.isContractFulfilled()) {
            int remainingDays = subscription.getRemainingContractDays();
            int proRatedCharge = remainingDays * subscription.getDailyPrice();
            
            message.append(Text.literal("  ⚠️ Contract not fulfilled! ").formatted(Formatting.RED))
                .append(Text.literal("You've paid for ").formatted(Formatting.WHITE))
                .append(Text.literal(String.valueOf(subscription.getDaysPaid())).formatted(Formatting.AQUA))
                .append(Text.literal("/").formatted(Formatting.WHITE))
                .append(Text.literal(String.valueOf(subscription.getContractDays())).formatted(Formatting.AQUA))
                .append(Text.literal(" days.\n").formatted(Formatting.WHITE));
            message.append(Text.literal("  You will be charged ").formatted(Formatting.WHITE))
                .append(Text.literal("$" + proRatedCharge).formatted(Formatting.RED, Formatting.BOLD))
                .append(Text.literal(" for the remaining ").formatted(Formatting.WHITE))
                .append(Text.literal(String.valueOf(remainingDays)).formatted(Formatting.YELLOW))
                .append(Text.literal(" days.\n").formatted(Formatting.WHITE));
            
            EconomyManager economyManager = CurrencyMod.getEconomyManager();
            double balance = economyManager.getBalance(player.getUuid());
            
            if (balance < proRatedCharge) {
                message.append(Text.literal("\n  ⚠️ Insufficient funds! ").formatted(Formatting.RED))
                    .append(Text.literal("You need ").formatted(Formatting.RED))
                    .append(Text.literal("$" + proRatedCharge).formatted(Formatting.RED, Formatting.BOLD))
                    .append(Text.literal(" but only have ").formatted(Formatting.RED))
                    .append(Text.literal("$" + String.format("%.2f", balance)).formatted(Formatting.RED, Formatting.BOLD));
            }
        } else {
            message.append(Text.literal("  You will be unsubscribed from this service.").formatted(Formatting.WHITE));
        }
        
        message.append(Text.literal("\nType ").formatted(Formatting.WHITE))
            .append(Text.literal("/service unsubscribe " + tag + " confirm").formatted(Formatting.RED))
            .append(Text.literal(" to confirm.").formatted(Formatting.WHITE));
        
        source.sendFeedback(() -> message, false);
        return 1;
    }
    
    /**
     * Confirm unsubscribe.
     */
    private static int confirmUnsubscribe(CommandContext<ServerCommandSource> context, String tag) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        ServiceManager manager = ServiceManager.getInstance();
        boolean success = manager.unsubscribeFromService(player, tag);
        
        if (success) {
            source.sendFeedback(() -> Text.literal("✓ Unsubscribed from service ")
                .formatted(Formatting.GREEN)
                .append(Text.literal(tag.toUpperCase()).formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal(".").formatted(Formatting.GREEN)), false);
            return 1;
        } else {
            source.sendError(Text.literal("Failed to unsubscribe. You may not have enough funds to break the contract."));
            return 0;
        }
    }
    
    /**
     * List all subscriptions for the player.
     */
    private static int listSubscriptions(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        ServiceManager manager = ServiceManager.getInstance();
        List<ServiceSubscription> subscriptions = manager.getPlayerSubscriptions(player.getUuid());
        
        if (subscriptions.isEmpty()) {
            source.sendFeedback(() -> Text.literal("You are not subscribed to any services.").formatted(Formatting.WHITE), false);
            return 1;
        }
        
        MutableText message = Text.literal("=== Your Subscriptions ===\n")
            .formatted(Formatting.GOLD, Formatting.BOLD);
        
        for (ServiceSubscription subscription : subscriptions) {
            Service service = manager.getService(subscription.getServiceTag());
            if (service == null) {
                continue; // Service was deleted
            }
            
            message.append(Text.literal("\n• ").formatted(Formatting.WHITE))
                .append(Text.literal(subscription.getServiceTag()).formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal(" - ").formatted(Formatting.WHITE))
                .append(Text.literal("$" + subscription.getDailyPrice()).formatted(Formatting.GOLD))
                .append(Text.literal("/day").formatted(Formatting.WHITE));
            
            if (subscription.getContractDays() > 0) {
                int remaining = subscription.getRemainingContractDays();
                message.append(Text.literal(" (Contract: ").formatted(Formatting.WHITE))
                    .append(Text.literal(String.valueOf(subscription.getDaysPaid())).formatted(Formatting.AQUA))
                    .append(Text.literal("/").formatted(Formatting.WHITE))
                    .append(Text.literal(String.valueOf(subscription.getContractDays())).formatted(Formatting.AQUA))
                    .append(Text.literal(" days, ").formatted(Formatting.WHITE))
                    .append(Text.literal(String.valueOf(remaining)).formatted(Formatting.YELLOW))
                    .append(Text.literal(" remaining)").formatted(Formatting.WHITE));
            } else {
                int prepaidRemaining = subscription.getDaysPaid();
                if (prepaidRemaining > 0) {
                    message.append(Text.literal(" (").formatted(Formatting.WHITE))
                        .append(Text.literal(String.valueOf(prepaidRemaining)).formatted(Formatting.AQUA))
                        .append(Text.literal(" prepaid days remaining)").formatted(Formatting.WHITE));
                }
            }
        }
        
        source.sendFeedback(() -> message, false);
        return 1;
    }
}

