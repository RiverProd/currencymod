package net.currencymod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.currencymod.CurrencyMod;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class BalanceCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            literal("bal")
                .executes(BalanceCommand::executeOwn)
                .then(argument("player", EntityArgumentType.player())
                    .executes(BalanceCommand::executeOther)
                )
        );

        dispatcher.register(
            literal("balance")
                .executes(BalanceCommand::executeOwn)
                .then(argument("player", EntityArgumentType.player())
                    .executes(BalanceCommand::executeOther)
                )
        );
    }
    
    /**
     * Execute the balance command for the player who ran it
     */
    private static int executeOwn(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // Check if the command was run by a player
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            double balance = CurrencyMod.getEconomyManager().getBalance(player.getUuid());
            
            // Format the balance to show only 2 decimal places if there are decimals
            String formattedBalance = formatBalance(balance);
            
            // Send the balance message to the player
            source.sendFeedback(() -> Text.literal("Your balance: $" + formattedBalance).formatted(Formatting.GOLD), false);
            return 1;
        } else {
            // The command was not run by a player
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
    }
    
    /**
     * Execute the balance command to check another player's balance
     */
    private static int executeOther(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
        
        // Only players or the server console can check other players' balances
        if (source.getEntity() instanceof ServerPlayerEntity || source.hasPermissionLevel(2)) {
            double balance = CurrencyMod.getEconomyManager().getBalance(targetPlayer.getUuid());
            String formattedBalance = formatBalance(balance);
            
            // Send the balance message
            source.sendFeedback(() -> Text.literal(targetPlayer.getName().getString() + "'s balance: $" + formattedBalance)
                .formatted(Formatting.GOLD), false);
            return 1;
        } else {
            source.sendError(Text.literal("You don't have permission to check other players' balances"));
            return 0;
        }
    }
    
    /**
     * Format a balance to show only 2 decimal places if there are decimals
     */
    private static String formatBalance(double balance) {
        return balance == Math.floor(balance) 
            ? String.format("%.0f", balance) 
            : String.format("%.2f", balance);
    }
} 