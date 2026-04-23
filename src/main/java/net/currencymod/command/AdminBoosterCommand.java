package net.currencymod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.authlib.GameProfile;
import net.currencymod.CurrencyMod;
import net.currencymod.jobs.BoosterType;
import net.currencymod.jobs.JobManager;
import net.currencymod.jobs.PlayerBoosterData;
import net.currencymod.jobs.PlayerJobLevel;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Admin commands for managing player boosters
 */
public class AdminBoosterCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        // Register /currencymod boosters apply command
        dispatcher.register(
            literal("currencymod")
                .requires(source -> source.hasPermissionLevel(2)) // Require OP level 2
                .then(literal("boosters")
                    .then(literal("apply")
                        .executes(AdminBoosterCommand::applyBoosters)
                    )
                )
        );
        
        // Register /adminboostergive command
        dispatcher.register(
            literal("adminboostergive")
                .requires(source -> source.hasPermissionLevel(2)) // Require OP level 2
                .then(argument("player", GameProfileArgumentType.gameProfile())
                    .then(argument("boostertype", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("premium");
                            builder.suggest("basic");
                            return builder.buildFuture();
                        })
                        .executes(context -> giveBooster(
                            context,
                            GameProfileArgumentType.getProfileArgument(context, "player"),
                            StringArgumentType.getString(context, "boostertype")
                        ))
                    )
                )
        );
        
        // Register /adminboosterremove command
        dispatcher.register(
            literal("adminboosterremove")
                .requires(source -> source.hasPermissionLevel(2)) // Require OP level 2
                .then(argument("player", GameProfileArgumentType.gameProfile())
                    .then(argument("boostertype", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("premium");
                            builder.suggest("basic");
                            return builder.buildFuture();
                        })
                        .executes(context -> removeBooster(
                            context,
                            GameProfileArgumentType.getProfileArgument(context, "player"),
                            StringArgumentType.getString(context, "boostertype")
                        ))
                    )
                )
        );
    }
    
    /**
     * Apply boosters to all players based on their job level
     */
    private static int applyBoosters(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        JobManager jobManager = JobManager.getInstance();
        
        source.sendFeedback(() -> Text.literal("Starting application of level-based boosters to all players...")
            .formatted(Formatting.YELLOW), true);
            
        Map<UUID, PlayerJobLevel> allPlayerLevels = jobManager.getAllPlayerJobLevels();
        
        if (allPlayerLevels.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No players found with job levels.")
                .formatted(Formatting.RED), true);
            return 0;
        }
        
        int totalPlayers = 0;
        int basicBoosters = 0;
        int premiumBoosters = 0;
        Map<String, List<String>> playerBoosterReport = new HashMap<>();
        
        // Process each player
        for (Map.Entry<UUID, PlayerJobLevel> entry : allPlayerLevels.entrySet()) {
            UUID playerUuid = entry.getKey();
            PlayerJobLevel jobLevel = entry.getValue();
            int level = jobLevel.getLevel();
            
            if (level <= 0) continue; // Skip players with no levels
            
            totalPlayers++;
            PlayerBoosterData boosterData = jobManager.getPlayerBoosterData(playerUuid);
            String playerName = getPlayerName(source, playerUuid);
            List<String> boosters = new ArrayList<>();
            
            // Award boosters based on level:
            // - Premium boosters for levels 5, 10, 15, 20, 25, 30
            // - Basic boosters for all other levels
            
            // Premium boosters for milestone levels
            if (level >= 5) {
                boosterData.addBooster(BoosterType.PREMIUM);
                premiumBoosters++;
                boosters.add("Premium (Level 5)");
            }
            
            if (level >= 10) {
                boosterData.addBooster(BoosterType.PREMIUM);
                premiumBoosters++;
                boosters.add("Premium (Level 10)");
            }
            
            if (level >= 15) {
                boosterData.addBooster(BoosterType.PREMIUM);
                premiumBoosters++;
                boosters.add("Premium (Level 15)");
            }
            
            if (level >= 20) {
                boosterData.addBooster(BoosterType.PREMIUM);
                premiumBoosters++;
                boosters.add("Premium (Level 20)");
            }
            
            if (level >= 25) {
                boosterData.addBooster(BoosterType.PREMIUM);
                premiumBoosters++;
                boosters.add("Premium (Level 25)");
            }
            
            if (level >= 30) {
                boosterData.addBooster(BoosterType.PREMIUM);
                premiumBoosters++;
                boosters.add("Premium (Level 30)");
            }
            
            // Basic boosters for all other levels (excluding milestone levels)
            int basicCount = level;
            
            // Subtract milestone levels where they got premium boosters instead
            if (level >= 5) basicCount--;
            if (level >= 10) basicCount--;
            if (level >= 15) basicCount--;
            if (level >= 20) basicCount--;
            if (level >= 25) basicCount--;
            if (level >= 30) basicCount--;
            
            for (int i = 0; i < basicCount; i++) {
                boosterData.addBooster(BoosterType.BASIC);
                basicBoosters++;
                boosters.add("Basic");
            }
            
            // Store report data
            playerBoosterReport.put(playerName + " (Level " + level + ")", boosters);
        }
        
        // Save data
        jobManager.save();
        
        // Send summary to admin
        source.sendFeedback(() -> Text.literal("Booster application complete!")
            .formatted(Formatting.GREEN, Formatting.BOLD), true);
            
        final int finalBasicBoosters = basicBoosters;
        final int finalPremiumBoosters = premiumBoosters;
        final int finalTotalPlayers = totalPlayers;
            
        source.sendFeedback(() -> Text.literal("Summary: ")
            .formatted(Formatting.GOLD)
            .append(Text.literal("Applied ").formatted(Formatting.WHITE))
            .append(Text.literal(finalBasicBoosters + " Basic").formatted(Formatting.AQUA))
            .append(Text.literal(" and ").formatted(Formatting.WHITE))
            .append(Text.literal(finalPremiumBoosters + " Premium").formatted(Formatting.GOLD))
            .append(Text.literal(" boosters to ").formatted(Formatting.WHITE))
            .append(Text.literal(finalTotalPlayers + " players").formatted(Formatting.GREEN)), true);
            
        // Send detailed report
        source.sendFeedback(() -> Text.literal("Detailed Report:").formatted(Formatting.GOLD, Formatting.BOLD), true);
        
        List<String> playerNames = new ArrayList<>(playerBoosterReport.keySet());
        Collections.sort(playerNames);
        
        for (String playerName : playerNames) {
            List<String> boosters = playerBoosterReport.get(playerName);
            
            int playerBasic = (int) boosters.stream().filter(b -> b.equals("Basic")).count();
            int playerPremium = (int) boosters.stream().filter(b -> !b.equals("Basic")).count();
            
            source.sendFeedback(() -> Text.literal("• ")
                .formatted(Formatting.GRAY)
                .append(Text.literal(playerName).formatted(Formatting.YELLOW))
                .append(Text.literal(": ").formatted(Formatting.GRAY))
                .append(Text.literal(playerBasic + " Basic").formatted(Formatting.AQUA))
                .append(Text.literal(", ").formatted(Formatting.GRAY))
                .append(Text.literal(playerPremium + " Premium").formatted(Formatting.GOLD)), true);
        }
        
        return 1;
    }
    
    /**
     * Give a booster to a player
     */
    private static int giveBooster(CommandContext<ServerCommandSource> context, Collection<GameProfile> profiles, String boosterTypeStr) {
        ServerCommandSource source = context.getSource();
        
        // Validate booster type
        BoosterType type;
        try {
            type = parseBoosterType(boosterTypeStr);
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("Invalid booster type: " + boosterTypeStr)
                .formatted(Formatting.RED));
            return 0;
        }
        
        // Check if we have at least one profile
        if (profiles.isEmpty()) {
            source.sendError(Text.literal("Player not found")
                .formatted(Formatting.RED));
            return 0;
        }
        
        JobManager jobManager = JobManager.getInstance();
        int successCount = 0;
        
        for (GameProfile profile : profiles) {
            UUID playerUuid = profile.getId();
            String playerName = profile.getName();
            
            // Get or create player booster data
            PlayerBoosterData boosterData = jobManager.getPlayerBoosterData(playerUuid);
            
            // Add the booster
            boosterData.addBooster(type);
            successCount++;
            
            // Send feedback
            source.sendFeedback(() -> Text.literal("Gave ")
                .formatted(Formatting.GREEN)
                .append(Text.literal(type.getDisplayName())
                    .formatted(type == BoosterType.PREMIUM ? Formatting.GOLD : Formatting.AQUA)
                    .formatted(Formatting.BOLD))
                .append(Text.literal(" to ")
                    .formatted(Formatting.GREEN))
                .append(Text.literal(playerName)
                    .formatted(Formatting.YELLOW)), true);
            
            // Notify player if online
            ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(playerUuid);
            if (player != null) {
                player.sendMessage(Text.literal("💫 ")
                    .formatted(Formatting.GOLD)
                    .append(Text.literal("An admin gave you a ")
                        .formatted(Formatting.YELLOW))
                    .append(Text.literal(type.getDisplayName())
                        .formatted(type == BoosterType.PREMIUM ? Formatting.GOLD : Formatting.AQUA)
                        .formatted(Formatting.BOLD))
                    .append(Text.literal("!")
                        .formatted(Formatting.YELLOW)));
            }
        }
        
        // Save changes
        jobManager.save();
        
        // Final feedback for multiple players
        if (successCount > 1) {
            final int finalSuccessCount = successCount;
            source.sendFeedback(() -> Text.literal("Successfully gave ")
                .formatted(Formatting.GREEN)
                .append(Text.literal(type.getDisplayName())
                    .formatted(type == BoosterType.PREMIUM ? Formatting.GOLD : Formatting.AQUA))
                .append(Text.literal(" to ")
                    .formatted(Formatting.GREEN))
                .append(Text.literal(finalSuccessCount + " players")
                    .formatted(Formatting.YELLOW)), true);
        }
        
        return successCount;
    }
    
    /**
     * Remove a booster from a player
     */
    private static int removeBooster(CommandContext<ServerCommandSource> context, Collection<GameProfile> profiles, String boosterTypeStr) {
        ServerCommandSource source = context.getSource();
        
        // Validate booster type
        BoosterType type;
        try {
            type = parseBoosterType(boosterTypeStr);
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("Invalid booster type: " + boosterTypeStr)
                .formatted(Formatting.RED));
            return 0;
        }
        
        // Check if we have at least one profile
        if (profiles.isEmpty()) {
            source.sendError(Text.literal("Player not found")
                .formatted(Formatting.RED));
            return 0;
        }
        
        JobManager jobManager = JobManager.getInstance();
        int successCount = 0;
        
        for (GameProfile profile : profiles) {
            UUID playerUuid = profile.getId();
            String playerName = profile.getName();
            
            // Get player booster data
            PlayerBoosterData boosterData = jobManager.getPlayerBoosterData(playerUuid);
            
            // Check if player has the booster
            int boosterCount = boosterData.getBoosterCount(type);
            if (boosterCount <= 0) {
                source.sendFeedback(() -> Text.literal(playerName)
                    .formatted(Formatting.YELLOW)
                    .append(Text.literal(" doesn't have any ")
                        .formatted(Formatting.RED))
                    .append(Text.literal(type.getDisplayName())
                        .formatted(type == BoosterType.PREMIUM ? Formatting.GOLD : Formatting.AQUA))
                    .append(Text.literal(" boosters.")
                        .formatted(Formatting.RED)), false);
                continue;
            }
            
            // Remove one booster of the specified type
            // We do this by manipulating the count directly in the EnumMap
            Map<BoosterType, Integer> ownedBoosters = boosterData.getOwnedBoosters();
            ownedBoosters.put(type, boosterCount - 1);
            successCount++;
            
            // Send feedback
            source.sendFeedback(() -> Text.literal("Removed one ")
                .formatted(Formatting.GREEN)
                .append(Text.literal(type.getDisplayName())
                    .formatted(type == BoosterType.PREMIUM ? Formatting.GOLD : Formatting.AQUA)
                    .formatted(Formatting.BOLD))
                .append(Text.literal(" from ")
                    .formatted(Formatting.GREEN))
                .append(Text.literal(playerName)
                    .formatted(Formatting.YELLOW))
                .append(Text.literal(" (")
                    .formatted(Formatting.GRAY))
                .append(Text.literal((boosterCount - 1) + " remaining")
                    .formatted(Formatting.WHITE))
                .append(Text.literal(")")
                    .formatted(Formatting.GRAY)), true);
            
            // Notify player if online
            ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(playerUuid);
            if (player != null) {
                player.sendMessage(Text.literal("💫 ")
                    .formatted(Formatting.RED)
                    .append(Text.literal("An admin removed one ")
                        .formatted(Formatting.YELLOW))
                    .append(Text.literal(type.getDisplayName())
                        .formatted(type == BoosterType.PREMIUM ? Formatting.GOLD : Formatting.AQUA)
                        .formatted(Formatting.BOLD))
                    .append(Text.literal(" from your inventory.")
                        .formatted(Formatting.YELLOW)));
            }
        }
        
        // Save changes if there were any successful removals
        if (successCount > 0) {
            jobManager.save();
        }
        
        // Final feedback for multiple players
        if (successCount > 1) {
            final int finalSuccessCount = successCount;
            source.sendFeedback(() -> Text.literal("Successfully removed ")
                .formatted(Formatting.GREEN)
                .append(Text.literal(type.getDisplayName())
                    .formatted(type == BoosterType.PREMIUM ? Formatting.GOLD : Formatting.AQUA))
                .append(Text.literal(" from ")
                    .formatted(Formatting.GREEN))
                .append(Text.literal(finalSuccessCount + " players")
                    .formatted(Formatting.YELLOW)), true);
        }
        
        return successCount;
    }
    
    /**
     * Parse a booster type from a string
     */
    private static BoosterType parseBoosterType(String typeStr) {
        if (typeStr.equalsIgnoreCase("premium")) {
            return BoosterType.PREMIUM;
        } else if (typeStr.equalsIgnoreCase("basic")) {
            return BoosterType.BASIC;
        } else {
            throw new IllegalArgumentException("Unknown booster type: " + typeStr);
        }
    }
    
    /**
     * Get a player's name from their UUID
     */
    private static String getPlayerName(ServerCommandSource source, UUID uuid) {
        // Try to get from online players
        ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(uuid);
        if (player != null) {
            return player.getName().getString();
        }
        
        // Try to get from user cache
        if (source.getServer().getUserCache() != null) {
            Optional<GameProfile> profile = source.getServer().getUserCache().getByUuid(uuid);
            if (profile.isPresent()) {
                return profile.get().getName();
            }
        }
        
        // Fall back to UUID
        return uuid.toString().substring(0, 8);
    }
} 