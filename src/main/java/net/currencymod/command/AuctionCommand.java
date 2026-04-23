package net.currencymod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.currencymod.CurrencyMod;
import net.currencymod.auction.AuctionManager;
import net.currencymod.economy.EconomyManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class AuctionCommand {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Auction limits
    private static final int MIN_DURATION = 1; // 1 minute
    private static final int MAX_DURATION = 5; // 5 minutes
    private static final double MIN_PRICE = 1.0; // Minimum starting price

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess commandRegistryAccess, CommandManager.RegistrationEnvironment registrationEnvironment) {
        dispatcher.register(
            CommandManager.literal("auction")
                .then(CommandManager.literal("create")
                    .then(CommandManager.argument("startingPrice", DoubleArgumentType.doubleArg(MIN_PRICE))
                        .then(CommandManager.argument("durationMinutes", IntegerArgumentType.integer(MIN_DURATION, MAX_DURATION))
                            .executes(context -> createAuction(
                                context,
                                DoubleArgumentType.getDouble(context, "startingPrice"),
                                IntegerArgumentType.getInteger(context, "durationMinutes")
                            ))
                        )
                    )
                )
                .then(CommandManager.literal("view")
                    .executes(AuctionCommand::viewCurrentAuction)
                )
                .then(CommandManager.literal("cancel")
                    .executes(AuctionCommand::cancelAuction)
                )
                .executes(context -> showHelp(context.getSource()))
        );
    }

    /**
     * Show help information for the auction command
     */
    private static int showHelp(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("=== Auction Commands ===")
            .formatted(Formatting.GOLD), false);
        
        source.sendFeedback(() -> Text.literal("/auction create <startingPrice> <durationMinutes>")
            .formatted(Formatting.YELLOW)
            .append(Text.literal(" - Create an auction with the item in your hand")
                .formatted(Formatting.WHITE)), false);
        
        source.sendFeedback(() -> Text.literal("/auction view")
            .formatted(Formatting.YELLOW)
            .append(Text.literal(" - View the current auction")
                .formatted(Formatting.WHITE)), false);
        
        source.sendFeedback(() -> Text.literal("/auction cancel")
            .formatted(Formatting.YELLOW)
            .append(Text.literal(" - Cancel your auction")
                .formatted(Formatting.WHITE)), false);
        
        source.sendFeedback(() -> Text.literal("/bid <amount>")
            .formatted(Formatting.YELLOW)
            .append(Text.literal(" - Place a bid on the current auction")
                .formatted(Formatting.WHITE)), false);
        
        return 1;
    }

    /**
     * Create an auction with the item in hand
     */
    private static int createAuction(CommandContext<ServerCommandSource> context, double startingPrice, int durationMinutes) {
        ServerCommandSource source = context.getSource();
        
        try {
            ServerPlayerEntity player = source.getPlayer();
            
            // Get the item in the player's main hand
            ItemStack itemInHand = player.getStackInHand(Hand.MAIN_HAND);
            
            // Check if the player is holding an item
            if (itemInHand.isEmpty()) {
                source.sendError(Text.literal("You must hold an item in your main hand to create an auction.")
                    .formatted(Formatting.RED));
                return 0;
            }
            
            // Make a copy of the item to auction
            ItemStack auctionItem = itemInHand.copy();
            
            // Create the auction
            AuctionManager auctionManager = AuctionManager.getInstance();
            boolean success = auctionManager.createAuction(player.getUuid(), auctionItem, startingPrice, durationMinutes);
            
            if (!success) {
                source.sendError(Text.literal("Could not create auction. There may already be an active auction.")
                    .formatted(Formatting.RED));
                return 0;
            }
            
            // Remove the item from the player's hand
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            
            // Notify the seller
            source.sendFeedback(() -> Text.literal("You created an auction for ")
                .formatted(Formatting.GREEN)
                .append(Text.literal(auctionItem.getCount() + "x " + auctionItem.getName().getString())
                    .formatted(Formatting.AQUA))
                .append(Text.literal(" starting at ")
                    .formatted(Formatting.GREEN))
                .append(Text.literal("$" + String.format("%.2f", startingPrice))
                    .formatted(Formatting.GOLD))
                .append(Text.literal(" for ")
                    .formatted(Formatting.GREEN))
                .append(Text.literal(durationMinutes + " minute" + (durationMinutes == 1 ? "" : "s"))
                    .formatted(Formatting.YELLOW)), false);
            
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("This command can only be executed by a player."));
            return 0;
        }
    }
    
    /**
     * View the current auction
     */
    private static int viewCurrentAuction(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        AuctionManager auctionManager = AuctionManager.getInstance();
        AuctionManager.Auction auction = auctionManager.getCurrentAuction();
        
        if (auction == null || auction.isEnded()) {
            source.sendFeedback(() -> Text.literal("There is no active auction.")
                .formatted(Formatting.YELLOW), false);
            return 0;
        }
        
        // Get seller name
        String sellerName = getPlayerName(source, auction.getSellerUuid());
        
        // Format time left
        long timeLeftSeconds = auction.getTimeLeft();
        String timeLeftFormatted = AuctionManager.formatTimeLeft(timeLeftSeconds);
        
        // Format auction details
        ItemStack item = auction.getItem();
        
        final Text auctionInfo = Text.literal("🔨 Current Auction ")
            .formatted(Formatting.GOLD)
            .append(Text.literal("\n")
                .formatted(Formatting.RESET))
            .append(Text.literal("Item: ")
                .formatted(Formatting.WHITE))
            .append(Text.literal(item.getCount() + "x " + item.getName().getString())
                .formatted(Formatting.AQUA))
            .append(Text.literal("\nSeller: ")
                .formatted(Formatting.WHITE))
            .append(Text.literal(sellerName)
                .formatted(Formatting.YELLOW))
            .append(Text.literal("\nCurrent Price: ")
                .formatted(Formatting.WHITE))
            .append(Text.literal("$" + String.format("%.2f", auction.getCurrentPrice()))
                .formatted(Formatting.GREEN, Formatting.BOLD));
        
        // Add highest bidder if there is one
        AuctionManager.Bid currentBid = auction.getCurrentBid();
        final Text finalAuctionInfo;
        if (currentBid != null) {
            String bidderName = getPlayerName(source, currentBid.getBidderUuid());
            finalAuctionInfo = auctionInfo.copy()
                .append(Text.literal("\nHighest Bidder: ")
                    .formatted(Formatting.WHITE))
                .append(Text.literal(bidderName)
                    .formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal("\nMinimum Bid: ")
                    .formatted(Formatting.WHITE))
                .append(Text.literal("$" + String.format("%.2f", auction.getMinimumBid()))
                    .formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal("\nTime Left: ")
                    .formatted(Formatting.WHITE))
                .append(Text.literal(timeLeftFormatted)
                    .formatted(Formatting.RED));
        } else {
            finalAuctionInfo = auctionInfo.copy()
                .append(Text.literal("\nMinimum Bid: ")
                    .formatted(Formatting.WHITE))
                .append(Text.literal("$" + String.format("%.2f", auction.getMinimumBid()))
                    .formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal("\nTime Left: ")
                    .formatted(Formatting.WHITE))
                .append(Text.literal(timeLeftFormatted)
                    .formatted(Formatting.RED));
        }
        
        source.sendFeedback(() -> finalAuctionInfo, false);
        
        // Show suggested bids to players who aren't the seller
        try {
            ServerPlayerEntity player = source.getPlayer();
            if (!player.getUuid().equals(auction.getSellerUuid())) {
                showSuggestedBids(player, auction);
            }
        } catch (Exception e) {
            // Not a player, ignore
        }
        
        return 1;
    }
    
    /**
     * Cancel the current auction
     */
    private static int cancelAuction(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            ServerPlayerEntity player = source.getPlayer();
            AuctionManager auctionManager = AuctionManager.getInstance();
            
            boolean success = auctionManager.cancelAuction(player.getUuid());
            
            if (!success) {
                AuctionManager.Auction auction = auctionManager.getCurrentAuction();
                if (auction == null || auction.isEnded()) {
                    source.sendError(Text.literal("There is no active auction to cancel.")
                        .formatted(Formatting.RED));
                } else if (!auction.getSellerUuid().equals(player.getUuid())) {
                    source.sendError(Text.literal("You cannot cancel someone else's auction.")
                        .formatted(Formatting.RED));
                } else {
                    source.sendError(Text.literal("The auction could not be canceled.")
                        .formatted(Formatting.RED));
                }
                return 0;
            }
            
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("This command can only be executed by a player."));
            return 0;
        }
    }
    
    /**
     * Show suggested bid amounts to a player
     */
    private static void showSuggestedBids(ServerPlayerEntity player, AuctionManager.Auction auction) {
        double currentPrice = auction.getCurrentPrice();
        double minBid = auction.getMinimumBid();
        
        // Generate some suggested bids
        double[] suggestions = new double[3];
        suggestions[0] = minBid; // Minimum bid
        suggestions[1] = Math.ceil((minBid * 1.5) * 100) / 100; // 50% more than minimum
        suggestions[2] = Math.ceil((minBid * 2.0) * 100) / 100; // Double the minimum
        
        // Create clickable bid options
        Text message = Text.literal("📊 Quick Bid Options: ")
            .formatted(Formatting.YELLOW);
        
        for (int i = 0; i < suggestions.length; i++) {
            if (i > 0) {
                message = message.copy().append(Text.literal(" | ").formatted(Formatting.GRAY));
            }
            
            double amount = suggestions[i];
            message = message.copy().append(
                Text.literal("$" + String.format("%.2f", amount))
                    .formatted(Formatting.GREEN)
                    .styled(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bid " + amount))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                            Text.literal("Click to bid $" + String.format("%.2f", amount))
                                .formatted(Formatting.GOLD))))
            );
        }
        
        // Add a custom bid option
        message = message.copy()
            .append(Text.literal(" | ").formatted(Formatting.GRAY))
            .append(
                Text.literal("Custom Bid")
                    .formatted(Formatting.AQUA)
                    .styled(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/bid "))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                            Text.literal("Enter a custom bid amount")
                                .formatted(Formatting.GOLD))))
            );
        
        player.sendMessage(message, false);
    }
    
    /**
     * Broadcast a message about a new auction
     */
    private static void broadcastNewAuction(ServerCommandSource source, ServerPlayerEntity seller, ItemStack item, double startingPrice, int durationMinutes) {
        String sellerName = seller.getName().getString();
        
        // Calculate end time
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(durationMinutes);
        String formattedEndTime = endTime.format(TIME_FORMATTER);
        
        // Get the auction manager
        AuctionManager auctionManager = AuctionManager.getInstance();
        
        // Create the message with hover tooltip for the item
        Text itemComponent = auctionManager.createItemHoverTooltip(item);
        
        Text message = Text.literal("🔨 New Auction! ")
            .formatted(Formatting.GOLD)
            .append(Text.literal(sellerName)
                .formatted(Formatting.YELLOW))
            .append(Text.literal(" is auctioning "))
            .append(itemComponent)
            .append(Text.literal("\nStarting bid: ")
                .formatted(Formatting.WHITE))
            .append(Text.literal("$" + String.format("%.2f", startingPrice))
                .formatted(Formatting.GREEN, Formatting.BOLD))
            .append(Text.literal(" • Ends in: ")
                .formatted(Formatting.WHITE))
            .append(Text.literal(durationMinutes + " minutes")
                .formatted(Formatting.RED))
            .append(Text.literal(" (" + formattedEndTime + ")")
                .formatted(Formatting.GRAY))
            .append(Text.literal("\nType ")
                .formatted(Formatting.WHITE))
            .append(Text.literal("/bid <amount>")
                .formatted(Formatting.YELLOW, Formatting.ITALIC))
            .append(Text.literal(" to place a bid!")
                .formatted(Formatting.WHITE));
        
        // Broadcast to all players
        MinecraftServer server = source.getServer();
        server.getPlayerManager().broadcast(message, false);
        
        // Log the auction creation
        CurrencyMod.LOGGER.info("{} created an auction for {}x {} at ${} for {} minutes", 
            sellerName, item.getCount(), item.getName().getString(), startingPrice, durationMinutes);
    }
    
    /**
     * Get a player's name from their UUID
     */
    private static String getPlayerName(ServerCommandSource source, UUID uuid) {
        // Try to get from the player manager
        ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(uuid);
        if (player != null) {
            return player.getName().getString();
        }
        
        // Try to get from the user cache
        if (source.getServer().getUserCache() != null) {
            var playerOpt = source.getServer().getUserCache().getByUuid(uuid);
            if (playerOpt.isPresent()) {
                return playerOpt.get().getName();
            }
        }
        
        // Return a shortened UUID if we can't find the name
        return shortenUuid(uuid);
    }
    
    /**
     * Shorten a UUID for display
     */
    private static String shortenUuid(UUID uuid) {
        String uuidStr = uuid.toString();
        return uuidStr.substring(0, 8);
    }
} 
