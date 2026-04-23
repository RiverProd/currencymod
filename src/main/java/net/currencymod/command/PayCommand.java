package net.currencymod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.currencymod.CurrencyMod;
import net.currencymod.economy.OfflinePaymentManager;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class PayCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            literal("pay")
                .then(argument("player", GameProfileArgumentType.gameProfile()) // Use GameProfile to allow targeting offline players
                    .then(argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(PayCommand::execute)
                    )
                )
        );
    }
    
    private static int execute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        
        // Check if the command was run by a player
        if (!(source.getEntity() instanceof ServerPlayerEntity sender)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        // Get the target player profile and amount arguments
        Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "player");
        if (profiles.isEmpty()) {
            source.sendError(Text.literal("Player not found"));
            return 0;
        }
        
        // Get the first profile (command should only target one player)
        GameProfile receiverProfile = profiles.iterator().next();
        UUID receiverUuid = receiverProfile.getId();
        String receiverName = receiverProfile.getName();
        double amount = DoubleArgumentType.getDouble(context, "amount");
        
        // Don't allow players to pay themselves
        if (sender.getUuid().equals(receiverUuid)) {
            source.sendError(Text.literal("You can't pay yourself"));
            return 0;
        }
        
        // Format the amount to two decimal places for display
        String formattedAmount = String.format("%.2f", amount);
        
        // Try to transfer the money
        boolean success = CurrencyMod.getEconomyManager().transferMoney(
            sender.getUuid(), 
            receiverUuid, 
            amount
        );
        
        if (success) {
            // Notify the sender about the successful transaction
            source.sendFeedback(() -> Text.literal("💰 You sent ")
                .append(Text.literal("$" + formattedAmount)
                    .formatted(Formatting.GREEN, Formatting.BOLD))
                .append(Text.literal(" to ")
                    .formatted(Formatting.WHITE))
                .append(Text.literal(receiverName)
                    .formatted(Formatting.YELLOW)), false);
            
            // Check if the receiver is online
            ServerPlayerEntity onlineReceiver = source.getServer().getPlayerManager().getPlayer(receiverUuid);
            if (onlineReceiver != null) {
                // Receiver is online, notify them immediately
                onlineReceiver.sendMessage(Text.literal("💰 Received ")
                    .formatted(Formatting.GOLD)
                    .append(Text.literal("$" + formattedAmount)
                        .formatted(Formatting.GREEN, Formatting.BOLD))
                    .append(Text.literal(" from ")
                        .formatted(Formatting.WHITE))
                    .append(Text.literal(sender.getName().getString())
                        .formatted(Formatting.YELLOW)));
            } else {
                // Receiver is offline, record the payment for later notification
                OfflinePaymentManager.getInstance().recordOfflinePayment(
                    receiverUuid,
                    sender.getName().getString(),
                    amount
                );
                
                // Inform the sender that the receiver is offline
                source.sendFeedback(() -> Text.literal("Note: ")
                    .formatted(Formatting.YELLOW)
                    .append(Text.literal(receiverName)
                        .formatted(Formatting.AQUA))
                    .append(Text.literal(" is offline but will be notified when they log in.")
                        .formatted(Formatting.YELLOW)), false);
            }
            return 1;
        } else {
            // Notify the sender that the transaction failed
            source.sendError(Text.literal("Payment failed. Make sure you have enough money."));
            return 0;
        }
    }
} 