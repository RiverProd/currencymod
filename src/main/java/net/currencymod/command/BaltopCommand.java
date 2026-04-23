package net.currencymod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.currencymod.CurrencyMod;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import com.mojang.authlib.GameProfile;

import java.util.*;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.literal;

public class BaltopCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            literal("baltop")
                .executes(BaltopCommand::execute)
        );
    }
    
    /**
     * Execute the baltop command to show the top 10 richest players
     */
    private static int execute(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // Make sure the current player is in the economy system
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            // This will add the player to the economy if they don't exist yet
            CurrencyMod.getEconomyManager().getBalance(player.getUuid());
        }
        
        // Get all player balances
        Map<UUID, Double> allBalances = CurrencyMod.getEconomyManager().getAllBalances();
        
        // Check if there are any balances
        if (allBalances.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No player balances found. The economy system may not be properly initialized.").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Sort the balances in descending order and take the top 10
        List<Map.Entry<UUID, Double>> topPlayers = allBalances.entrySet().stream()
            .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
            .limit(10)
            .collect(Collectors.toList());
        
        // Send header message
        int playerCount = topPlayers.size();
        source.sendFeedback(() -> Text.literal("Top " + playerCount + " Richest Players:").formatted(Formatting.GOLD, Formatting.BOLD), false);
        
        // Send each player's rank and balance
        int rank = 1;
        for (Map.Entry<UUID, Double> entry : topPlayers) {
            UUID playerUuid = entry.getKey();
            double balance = entry.getValue();
            
            // Format the balance
            String formattedBalance = formatBalance(balance);
            
            // Try to get the player's name from the server
            String playerName = getPlayerNameFromUuid(source, playerUuid);
            
            // Create final copies for use in lambda
            final int currentRank = rank;
            final Formatting rankColor = getRankColor(currentRank);
            
            // Format and send the message
            source.sendFeedback(() -> Text.literal("#" + currentRank + " ")
                .formatted(rankColor)
                .append(Text.literal(playerName)
                    .formatted(Formatting.WHITE))
                .append(Text.literal(": $" + formattedBalance)
                    .formatted(Formatting.YELLOW)), false);
            
            rank++;
        }
        
        return 1;
    }
    
    /**
     * Get a player's name from their UUID.
     * If the player is offline, try to find their name in the user cache.
     */
    private static String getPlayerNameFromUuid(ServerCommandSource source, UUID playerUuid) {
        // Try to find the player on the server
        ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(playerUuid);
        if (player != null) {
            return player.getName().getString();
        }
        
        // If player is offline, try to get their name from the user cache
        var userCache = source.getServer().getUserCache();
        if (userCache != null) {
            Optional<GameProfile> profileOpt = userCache.getByUuid(playerUuid);
            if (profileOpt.isPresent()) {
                GameProfile profile = profileOpt.get();
                return profile.getName();
            }
        }
        
        // If we can't find the name, use a shortened UUID
        return shortenUuid(playerUuid);
    }
    
    /**
     * Shorten a UUID for display purposes
     */
    private static String shortenUuid(UUID uuid) {
        String uuidString = uuid.toString();
        return uuidString.substring(0, 8) + "...";
    }
    
    /**
     * Get the color for a specific rank
     */
    private static Formatting getRankColor(int rank) {
        return switch (rank) {
            case 1 -> Formatting.GOLD;
            case 2 -> Formatting.GRAY;
            case 3 -> Formatting.DARK_RED;
            default -> Formatting.GREEN;
        };
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