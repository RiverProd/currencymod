package net.currencymod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.currencymod.CurrencyMod;
import net.currencymod.auction.AuctionManager;
import net.currencymod.economy.EconomyManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.item.ItemStack;
import com.mojang.authlib.GameProfile;

import java.util.UUID;
import java.util.Optional;

/**
 * Command for bidding on auctions
 */
public class BidCommand {
    // Standard bid increments for suggested bids
    private static final double[] BID_INCREMENTS = {0.0, 5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1000.0};
    
    // Quick bid fixed increments
    private static final double[] FIXED_INCREMENTS = {1.0, 10.0, 100.0};
    
    // Quick bid percentage increments
    private static final double[] PERCENTAGE_INCREMENTS = {0.01, 0.1, 1.0}; // 1%, 10%, 100%
    
    /**
     * Register the bid command
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            CommandManager.literal("bid")
                .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.01))
                    .executes(context -> placeBid(
                        context,
                        DoubleArgumentType.getDouble(context, "amount")
                    ))
                )
                .executes(BidCommand::showActiveBid)
        );
    }
    
    /**
     * Place a bid on the current auction
     */
    private static int placeBid(CommandContext<ServerCommandSource> context, double bidAmount) {
        ServerCommandSource source = context.getSource();
        
        try {
            ServerPlayerEntity player = source.getPlayer();
            AuctionManager auctionManager = AuctionManager.getInstance();
            
            // Check if there's an active auction
            AuctionManager.Auction auction = auctionManager.getCurrentAuction();
            if (auction == null || auction.isEnded()) {
                source.sendError(Text.literal("There is no active auction to bid on.")
                    .formatted(Formatting.RED));
                return 0;
            }
            
            // Check if the player is the seller
            if (auction.getSellerUuid().equals(player.getUuid())) {
                source.sendError(Text.literal("You cannot bid on your own auction.")
                    .formatted(Formatting.RED));
                return 0;
            }
            
            // Check if the player has enough money
            EconomyManager economyManager = CurrencyMod.getEconomyManager();
            double playerBalance = economyManager.getBalance(player.getUuid());
            if (playerBalance < bidAmount) {
                source.sendError(Text.literal("You don't have enough money to place this bid. Your balance: ")
                    .formatted(Formatting.RED)
                    .append(Text.literal("$" + String.format("%.2f", playerBalance))
                        .formatted(Formatting.GOLD)));
                return 0;
            }
            
            // Check if the bid is high enough
            double minimumBid = auction.getMinimumBid();
            if (bidAmount < minimumBid) {
                source.sendError(Text.literal("Your bid is too low. Minimum bid: ")
                    .formatted(Formatting.RED)
                    .append(Text.literal("$" + String.format("%.2f", minimumBid))
                        .formatted(Formatting.GOLD)));
                return 0;
            }
            
            // Place the bid
            boolean success = auctionManager.placeBid(player.getUuid(), bidAmount);
            if (!success) {
                source.sendError(Text.literal("Failed to place bid. The auction may have ended.")
                    .formatted(Formatting.RED));
                return 0;
            }
            
            // Notify the player about their bid
            source.sendFeedback(() -> Text.literal("✓ ")
                .formatted(Formatting.GREEN, Formatting.BOLD)
                .append(Text.literal("You placed a bid of ")
                    .formatted(Formatting.WHITE))
                .append(Text.literal("$" + String.format("%.2f", bidAmount))
                    .formatted(Formatting.GREEN))
                .append(Text.literal(" on ")
                    .formatted(Formatting.WHITE))
                .append(Text.literal(auction.getItem().getCount() + "x " + auction.getItem().getName().getString())
                    .formatted(Formatting.AQUA)), false);
            
            // Broadcast the bid to all players
            broadcastBid(source, player.getName().getString(), auction, bidAmount);
            
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("This command can only be executed by a player."));
            return 0;
        }
    }
    
    /**
     * Show information about the active auction
     */
    private static int showActiveBid(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        AuctionManager auctionManager = AuctionManager.getInstance();
        AuctionManager.Auction auction = auctionManager.getCurrentAuction();
        
        if (auction == null || auction.isEnded()) {
            source.sendFeedback(() -> Text.literal("There is no active auction right now.")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        // Get auction details
        UUID sellerUuid = auction.getSellerUuid();
        String sellerName = getPlayerName(source, sellerUuid);
        ItemStack item = auction.getItem();
        double currentBid = auction.getCurrentPrice();
        double minimumBid = auction.getMinimumBid();
        long timeLeft = auction.getTimeLeft();
        
        // Create the auction info text with hoverable item
        Text itemComponent = auctionManager.createItemHoverTooltip(item);
        
        // Format current bid information
        Text bidInfo;
        if (auction.getCurrentBid() != null) {
            UUID bidderUuid = auction.getCurrentBid().getBidderUuid();
            String bidderName = getPlayerName(source, bidderUuid);
            bidInfo = Text.literal("Current bid: ")
                .formatted(Formatting.WHITE)
                .append(Text.literal("$" + String.format("%.2f", currentBid))
                    .formatted(Formatting.GREEN))
                .append(Text.literal(" by ")
                    .formatted(Formatting.WHITE))
                .append(Text.literal(bidderName)
                    .formatted(Formatting.YELLOW));
        } else {
            bidInfo = Text.literal("Starting bid: ")
                .formatted(Formatting.WHITE)
                .append(Text.literal("$" + String.format("%.2f", currentBid))
                    .formatted(Formatting.GREEN));
        }
        
        // Create auction header
        Text auctionHeader = Text.literal("🔨 Active Auction ")
            .formatted(Formatting.GOLD, Formatting.BOLD)
            .append(Text.literal("(" + AuctionManager.formatTimeLeft(timeLeft) + ")")
                .formatted(Formatting.GRAY));
        
        // Create auction info
        Text auctionInfo = Text.literal("Seller: ")
            .formatted(Formatting.WHITE)
            .append(Text.literal(sellerName)
                .formatted(Formatting.YELLOW))
            .append(Text.literal("\nItem: ")
                .formatted(Formatting.WHITE))
            .append(itemComponent)
            .append(Text.literal("\n"))
            .append(bidInfo)
            .append(Text.literal("\nMinimum bid: ")
                .formatted(Formatting.WHITE))
            .append(Text.literal("$" + String.format("%.2f", minimumBid))
                .formatted(Formatting.GREEN));
        
        // Send the auction info
        source.sendFeedback(() -> auctionHeader, false);
        source.sendFeedback(() -> auctionInfo, false);
        
        // Show suggested bids if the command was run by a player
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            showSuggestedBids(player, auction);
        }
        
        return 1;
    }
    
    /**
     * Show suggested bids to a player
     */
    private static void showSuggestedBids(ServerPlayerEntity player, AuctionManager.Auction auction) {
        double minBid = auction.getMinimumBid();
        double currentPrice = auction.getCurrentPrice();
        
        // Create standard bid suggestions
        Text standardBids = createStandardBidSuggestions(player, auction, minBid);
        
        // Create fixed increment bid options (+$1, +$10, +$100)
        Text fixedIncrementBids = createFixedIncrementBids(currentPrice, minBid);
        
        // Create percentage increment bid options (+1%, +10%, +100%)
        Text percentageIncrementBids = createPercentageIncrementBids(currentPrice, minBid);
        
        // Send all bid suggestions to the player
        player.sendMessage(standardBids, false);
        player.sendMessage(Text.literal(""), false); // Empty line for spacing
        player.sendMessage(fixedIncrementBids, false);
        player.sendMessage(percentageIncrementBids, false);
    }
    
    /**
     * Create standard bid suggestions (like in the original code)
     */
    private static Text createStandardBidSuggestions(ServerPlayerEntity player, AuctionManager.Auction auction, double minBid) {
        // Find appropriate bid increments based on the minimum bid
        double[] suggestedBids = new double[3];
        
        // First suggestion is always the minimum bid
        suggestedBids[0] = minBid;
        
        // Find increments for the other suggestions
        double increment = 0;
        for (double bidIncrement : BID_INCREMENTS) {
            if (minBid >= bidIncrement) {
                increment = bidIncrement;
            } else {
                break;
            }
        }
        
        // If we have a very low price, use 50% and 100% increments
        if (increment <= 0) {
            suggestedBids[1] = Math.ceil((minBid * 1.5) * 100) / 100; // 50% more
            suggestedBids[2] = Math.ceil((minBid * 2.0) * 100) / 100; // Double
        } else {
            // Otherwise use appropriate increments
            suggestedBids[1] = Math.ceil((minBid + increment) * 100) / 100;
            suggestedBids[2] = Math.ceil((minBid + increment * 2) * 100) / 100;
        }
        
        // Create clickable bid options
        Text message = Text.literal("📊 Standard Bids: ")
            .formatted(Formatting.YELLOW);
        
        for (int i = 0; i < suggestedBids.length; i++) {
            if (i > 0) {
                message = message.copy().append(Text.literal(" | ").formatted(Formatting.GRAY));
            }
            
            double bidAmount = suggestedBids[i];
            message = message.copy().append(createClickableBid(bidAmount));
        }
        
        // Add custom bid option
        message = message.copy()
            .append(Text.literal(" | ").formatted(Formatting.GRAY))
            .append(Text.literal("Custom Bid")
                .formatted(Formatting.AQUA)
                .styled(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/bid "))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                        Text.literal("Enter a custom bid amount")
                            .formatted(Formatting.GOLD))))
            );
        
        return message;
    }
    
    /**
     * Create fixed increment bid options (+$1, +$10, +$100)
     */
    private static Text createFixedIncrementBids(double currentPrice, double minBid) {
        Text message = Text.literal("📈 Fixed Increments: ")
            .formatted(Formatting.GREEN);
        
        for (int i = 0; i < FIXED_INCREMENTS.length; i++) {
            if (i > 0) {
                message = message.copy().append(Text.literal(" | ").formatted(Formatting.GRAY));
            }
            
            double increment = FIXED_INCREMENTS[i];
            double bidAmount = Math.max(minBid, Math.ceil((currentPrice + increment) * 100) / 100);
            
            message = message.copy().append(
                Text.literal("+$" + String.format("%.0f", increment))
                    .formatted(Formatting.GOLD)
                    .styled(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bid " + bidAmount))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                            Text.literal("Bid $" + String.format("%.2f", bidAmount) + 
                                " (Current + $" + String.format("%.0f", increment) + ")")
                                .formatted(Formatting.YELLOW))))
            );
        }
        
        return message;
    }
    
    /**
     * Create percentage increment bid options (+1%, +10%, +100%)
     */
    private static Text createPercentageIncrementBids(double currentPrice, double minBid) {
        Text message = Text.literal("📊 Percentage Increments: ")
            .formatted(Formatting.AQUA);
        
        for (int i = 0; i < PERCENTAGE_INCREMENTS.length; i++) {
            if (i > 0) {
                message = message.copy().append(Text.literal(" | ").formatted(Formatting.GRAY));
            }
            
            double percentage = PERCENTAGE_INCREMENTS[i];
            int percentDisplay = (int)(percentage * 100);
            double increment = currentPrice * percentage;
            double bidAmount = Math.max(minBid, Math.ceil((currentPrice + increment) * 100) / 100);
            
            message = message.copy().append(
                Text.literal("+" + percentDisplay + "%")
                    .formatted(Formatting.LIGHT_PURPLE)
                    .styled(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bid " + bidAmount))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                            Text.literal("Bid $" + String.format("%.2f", bidAmount) + 
                                " (Current + " + percentDisplay + "%)")
                                .formatted(Formatting.YELLOW))))
            );
        }
        
        return message;
    }
    
    /**
     * Create a clickable bid text
     */
    private static Text createClickableBid(double amount) {
        return Text.literal("$" + String.format("%.2f", amount))
            .formatted(Formatting.GREEN)
            .styled(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bid " + amount))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    Text.literal("Click to bid $" + String.format("%.2f", amount))
                        .formatted(Formatting.GOLD)))
            );
    }
    
    /**
     * Broadcast a bid to all players
     */
    private static void broadcastBid(ServerCommandSource source, String bidderName, AuctionManager.Auction auction, double bidAmount) {
        Text message = Text.literal("🔨 New Bid! ")
            .formatted(Formatting.GOLD, Formatting.BOLD)
            .append(Text.literal(bidderName)
                .formatted(Formatting.AQUA))
            .append(Text.literal(" bid ")
                .formatted(Formatting.WHITE))
            .append(Text.literal("$" + String.format("%.2f", bidAmount))
                .formatted(Formatting.GREEN))
            .append(Text.literal(" on ")
                .formatted(Formatting.WHITE))
            .append(Text.literal(auction.getItem().getCount() + "x " + auction.getItem().getName().getString())
                .formatted(Formatting.YELLOW))
            .append(Text.literal(" (")
                .formatted(Formatting.WHITE))
            .append(Text.literal(AuctionManager.formatTimeLeft(auction.getTimeLeft()) + " left")
                .formatted(Formatting.RED))
            .append(Text.literal(")")
                .formatted(Formatting.WHITE));
        
        source.getServer().getPlayerManager().broadcast(message, false);
    }

    /**
     * Get a player's name from their UUID
     * @param source The command source
     * @param uuid The UUID to look up
     * @return The player's name or a placeholder if not found
     */
    private static String getPlayerName(ServerCommandSource source, UUID uuid) {
        // Try to find the player on the server
        ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(uuid);
        if (player != null) {
            return player.getName().getString();
        }
        
        // If player is offline, try to get their name from the user cache
        var userCache = source.getServer().getUserCache();
        if (userCache != null) {
            Optional<GameProfile> profileOpt = userCache.getByUuid(uuid);
            if (profileOpt.isPresent()) {
                return profileOpt.get().getName();
            }
        }
        
        // If we can't find the name, use a shortened UUID
        return shortenUuid(uuid);
    }
    
    /**
     * Shorten a UUID for display purposes
     */
    private static String shortenUuid(UUID uuid) {
        String uuidString = uuid.toString();
        return uuidString.substring(0, 8) + "...";
    }
} 