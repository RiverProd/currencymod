package net.currencymod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.currencymod.trade.TradeManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class TradeCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            literal("tradehand")
                .executes(TradeCommand::showHelp)
                .then(literal("accept")
                    .executes(TradeCommand::acceptTrade)
                )
                .then(literal("deny")
                    .executes(TradeCommand::denyTrade)
                )
                .then(argument("player", EntityArgumentType.player())
                    .then(argument("price", DoubleArgumentType.doubleArg(0.01))
                        .executes(TradeCommand::createTradeRequest)
                    )
                )
        );
    }
    
    /**
     * Show help for the tradehand command
     */
    private static int showHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        Text header = Text.literal("📦 ").formatted(Formatting.GOLD)
            .append(Text.literal("Trade Command Help").formatted(Formatting.GOLD, Formatting.BOLD));
            
        Text usage = Text.literal("\nUsage:")
            .append(Text.literal("\n• /tradehand <player> <price>").formatted(Formatting.YELLOW))
            .append(Text.literal(" - Send a trade request for the item in your hand"))
            .append(Text.literal("\n• /tradehand accept").formatted(Formatting.GREEN))
            .append(Text.literal(" - Accept an incoming trade request"))
            .append(Text.literal("\n• /tradehand deny").formatted(Formatting.RED))
            .append(Text.literal(" - Deny an incoming trade request"));
            
        Text note = Text.literal("\n\nNotes:")
            .append(Text.literal("\n• When you create a trade request, the item is immediately removed from your inventory"))
            .append(Text.literal("\n• If the trade expires (after 1 minute) or is denied, the item is returned to you"))
            .append(Text.literal("\n• You'll receive reminders as the trade request approaches expiration"));
            
        source.sendFeedback(() -> header.copy().append(usage).append(note), false);
        return 1;
    }
    
    /**
     * Create a new trade request
     */
    private static int createTradeRequest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        
        // Check if the command was run by a player
        if (!(source.getEntity() instanceof ServerPlayerEntity sender)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        // Get the target player and price
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
        double price = DoubleArgumentType.getDouble(context, "price");
        
        // Don't allow trading with yourself
        if (sender.equals(target)) {
            source.sendError(Text.literal("You can't trade with yourself"));
            return 0;
        }
        
        // Check if the player has an item in their hand
        // Make sure to use Hand.MAIN_HAND explicitly for consistency
        ItemStack handItem = sender.getStackInHand(Hand.MAIN_HAND);
        if (handItem.isEmpty()) {
            source.sendError(Text.literal("You need to hold an item in your main hand to trade"));
            return 0;
        }
        
        // Create the trade request
        boolean success = TradeManager.getInstance().createTradeRequest(sender, target, handItem, price);
        
        if (!success) {
            source.sendError(Text.literal("That player already has a pending trade request. Try again later."));
            return 0;
        }
        
        return 1;
    }
    
    /**
     * Accept an incoming trade request
     */
    private static int acceptTrade(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // Check if the command was run by a player
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        // Accept the trade request
        boolean success = TradeManager.getInstance().acceptTradeRequest(player);
        
        if (!success) {
            source.sendError(Text.literal("You don't have any pending trade requests to accept"));
            return 0;
        }
        
        return 1;
    }
    
    /**
     * Deny an incoming trade request
     */
    private static int denyTrade(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // Check if the command was run by a player
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        // Deny the trade request
        boolean success = TradeManager.getInstance().denyTradeRequest(player);
        
        if (!success) {
            source.sendError(Text.literal("You don't have any pending trade requests to deny"));
            return 0;
        }
        
        return 1;
    }
} 