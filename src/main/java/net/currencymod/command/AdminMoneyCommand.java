package net.currencymod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.currencymod.CurrencyMod;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import com.mojang.authlib.GameProfile;

import java.util.Collection;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Commands for administrators to modify player balances
 */
public class AdminMoneyCommand {

    /**
     * Register both adminpay and adminfine commands
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        // Register adminpay command
        dispatcher.register(
            literal("adminpay")
                .requires(source -> source.hasPermissionLevel(2)) // Require permission level 2 (admin/op)
                .then(argument("player", GameProfileArgumentType.gameProfile()) // Use GameProfile to allow targeting offline players
                    .then(argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(context -> adminPay(context))
                    )
                )
        );

        // Register adminfine command
        dispatcher.register(
            literal("adminfine")
                .requires(source -> source.hasPermissionLevel(2)) // Require permission level 2 (admin/op)
                .then(argument("player", GameProfileArgumentType.gameProfile()) // Use GameProfile to allow targeting offline players
                    .then(argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(context -> adminFine(context))
                    )
                )
        );
    }

    /**
     * Handle the adminpay command - adds funds to a player's balance
     */
    private static int adminPay(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        
        // Get the target player profile
        Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "player");
        if (profiles.isEmpty()) {
            source.sendError(Text.literal("Player not found"));
            return 0;
        }
        
        // Get the first profile (command should only target one player)
        GameProfile targetProfile = profiles.iterator().next();
        UUID targetUuid = targetProfile.getId();
        String targetName = targetProfile.getName();
        
        // Get the amount
        double amount = DoubleArgumentType.getDouble(context, "amount");
        
        // Format the amount for display
        String formattedAmount = formatAmount(amount);
        
        // Add money to the target player's balance
        CurrencyMod.getEconomyManager().addBalance(targetUuid, amount);
        
        // Notify the admin
        source.sendFeedback(() -> Text.literal("💰 Added ")
            .append(Text.literal("$" + formattedAmount)
                .formatted(Formatting.GREEN, Formatting.BOLD))
            .append(Text.literal(" to " + targetName + "'s account.")), true);
        
        // Notify the target player if they're online
        ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(targetUuid);
        if (targetPlayer != null) {
            targetPlayer.sendMessage(Text.literal("💰 You received ")
                .formatted(Formatting.GOLD)
                .append(Text.literal("$" + formattedAmount)
                    .formatted(Formatting.GREEN, Formatting.BOLD))
                .append(Text.literal(" from an administrator.")));
        }
        
        return 1;
    }
    
    /**
     * Handle the adminfine command - removes funds from a player's balance
     */
    private static int adminFine(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        
        // Get the target player profile
        Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "player");
        if (profiles.isEmpty()) {
            source.sendError(Text.literal("Player not found"));
            return 0;
        }
        
        // Get the first profile (command should only target one player)
        GameProfile targetProfile = profiles.iterator().next();
        UUID targetUuid = targetProfile.getId();
        String targetName = targetProfile.getName();
        
        // Get the amount
        double amount = DoubleArgumentType.getDouble(context, "amount");
        
        // Format the amount for display
        String formattedAmount = formatAmount(amount);
        
        // Remove money from the target player's balance
        // Note: We're not checking if they have enough since this is an admin command
        // and should work regardless of the player's current balance
        CurrencyMod.getEconomyManager().removeBalance(targetUuid, amount);
        
        // Notify the admin
        source.sendFeedback(() -> Text.literal("💰 Removed ")
            .append(Text.literal("$" + formattedAmount)
                .formatted(Formatting.RED, Formatting.BOLD))
            .append(Text.literal(" from " + targetName + "'s account.")), true);
        
        // Notify the target player if they're online
        ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(targetUuid);
        if (targetPlayer != null) {
            targetPlayer.sendMessage(Text.literal("💰 You have been fined ")
                .formatted(Formatting.GOLD)
                .append(Text.literal("$" + formattedAmount)
                    .formatted(Formatting.RED, Formatting.BOLD))
                .append(Text.literal(" by an administrator.")));
        }
        
        return 1;
    }
    
    /**
     * Format a currency amount with proper decimal places
     */
    private static String formatAmount(double amount) {
        // Format with 2 decimal places if there are any cents, otherwise as a whole number
        return amount == Math.floor(amount) 
            ? String.format("%.0f", amount) 
            : String.format("%.2f", amount);
    }
} 