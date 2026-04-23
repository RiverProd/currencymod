package net.currencymod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.currencymod.CurrencyMod;
import net.currencymod.jobs.Job;
import net.currencymod.jobs.JobManager;
import net.currencymod.jobs.PlayerJobLevel;
import net.currencymod.jobs.PlayerJobStreak;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.item.Item;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.server.MinecraftServer;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.currencymod.jobs.Booster;
import net.currencymod.jobs.BoosterType;
import net.currencymod.jobs.PlayerBoosterData;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.ClickEvent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.currencymod.config.GuiPreferenceManager;
import net.currencymod.ui.JobsScreenHandler;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

public class JobCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            literal("jobs")
                .executes(JobCommand::openOrHelp)
                .then(literal("list")
                    .executes(JobCommand::openOrList)
                )
                .then(literal("activate")
                    .then(argument("jobId", StringArgumentType.greedyString())
                        .executes(context -> activateJob(context, StringArgumentType.getString(context, "jobId")))
                    )
                    .executes(context -> {
                        ServerCommandSource source = context.getSource();
                        source.sendFeedback(() -> Text.literal("Use ").append(
                            Text.literal("/jobs list").formatted(Formatting.YELLOW)
                        ).append(" to see available jobs and click on one to activate it."), false);
                        return 1;
                    })
                )
                .then(literal("abandon")
                    .executes(JobCommand::promptAbandonJob)
                    .then(literal("confirm")
                        .executes(JobCommand::confirmAbandonJob)
                    )
                )
                .then(literal("complete")
                    .executes(JobCommand::completeJob)
                )
                .then(literal("info")
                    .executes(JobCommand::showActiveJob)
                )
                .then(literal("level")
                    .executes(JobCommand::showPlayerLevel)
                )
                .then(literal("boosters")
                    .executes(context -> showBoosters(context.getSource()))
                    .then(literal("use")
                        .then(argument("type", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                for (BoosterType type : BoosterType.values()) {
                                    builder.suggest(type.name().toLowerCase());
                                }
                                return builder.buildFuture();
                            })
                            .executes(context -> useBooster(
                                context.getSource(), 
                                StringArgumentType.getString(context, "type")
                            ))
                        )
                    )
                )
                .then(literal("skip")
                    .executes(JobCommand::promptSkipJob)
                    .then(literal("confirm")
                        .executes(JobCommand::confirmSkipJob)
                    )
                )
        );
    }
    
    /** Opens GUI if enabled, otherwise falls back to the help text. */
    private static int openOrHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (source.getEntity() instanceof ServerPlayerEntity player
                && GuiPreferenceManager.getInstance().isGuiEnabled(player.getUuid())) {
            JobsScreenHandler.open(player);
            return 1;
        }
        return showHelp(context);
    }

    /** Opens GUI if enabled, otherwise falls back to the text job list. */
    private static int openOrList(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (source.getEntity() instanceof ServerPlayerEntity player
                && GuiPreferenceManager.getInstance().isGuiEnabled(player.getUuid())) {
            JobsScreenHandler.open(player);
            return 1;
        }
        return listJobs(context);
    }

    /**
     * Show command help
     */
    private static int showHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        Text helpHeader = Text.literal("=== Jobs Help ===").formatted(Formatting.GOLD, Formatting.BOLD);
        
        // Description of the system
        Text description = Text.literal("Complete jobs to earn money. Jobs require collecting specific items and have varying rewards. Higher job levels provide bonus rewards and unlock more available jobs.").formatted(Formatting.YELLOW);
        
        // Commands list
        Text commandsHeader = Text.literal("\nCommands:").formatted(Formatting.GOLD);
        
        Text listCmd = Text.literal("\n/jobs list").formatted(Formatting.GREEN)
            .append(Text.literal(" - View your available jobs").formatted(Formatting.GRAY));
        
        Text activeCmd = Text.literal("\n/jobs active").formatted(Formatting.GREEN)
            .append(Text.literal(" - View your current active job").formatted(Formatting.GRAY));
        
        Text activateCmd = Text.literal("\n/jobs activate <id>").formatted(Formatting.GREEN)
            .append(Text.literal(" - Activate a job by its ID").formatted(Formatting.GRAY));
        
        Text completeCmd = Text.literal("\n/jobs complete").formatted(Formatting.GREEN)
            .append(Text.literal(" - Complete your active job").formatted(Formatting.GRAY));
        
        Text abandonCmd = Text.literal("\n/jobs abandon").formatted(Formatting.GREEN)
            .append(Text.literal(" - Abandon your active job (with penalty)").formatted(Formatting.GRAY));
        
        Text levelCmd = Text.literal("\n/jobs level").formatted(Formatting.GREEN)
            .append(Text.literal(" - View your job level progress").formatted(Formatting.GRAY));
        
        // Job slots information
        Text jobSlotsHeader = Text.literal("\n\nJob Slots:").formatted(Formatting.GOLD);
        
        Text jobSlotsInfo = Text.literal("\nYour job level determines how many jobs you can choose from:").formatted(Formatting.YELLOW);
        Text level0Info = Text.literal("\n • Levels 0-4: 5 jobs").formatted(Formatting.GRAY);
        Text level5Info = Text.literal("\n • Levels 5-9: 6 jobs").formatted(Formatting.GRAY);
        Text level10Info = Text.literal("\n • Levels 10-14: 7 jobs").formatted(Formatting.GRAY);
        Text level15Info = Text.literal("\n • Levels 15-19: 8 jobs").formatted(Formatting.GRAY);
        Text level20Info = Text.literal("\n • Levels 20-24: 10 jobs").formatted(Formatting.GRAY);
        Text level25Info = Text.literal("\n • Levels 25-29: 12 jobs").formatted(Formatting.GRAY);
        Text level30Info = Text.literal("\n • Level 30: 14 jobs").formatted(Formatting.GRAY);
        
        // Send help messages
        source.sendFeedback(() -> helpHeader, false);
        source.sendFeedback(() -> description, false);
        source.sendFeedback(() -> commandsHeader, false);
        source.sendFeedback(() -> listCmd, false);
        source.sendFeedback(() -> activeCmd, false);
        source.sendFeedback(() -> activateCmd, false);
        source.sendFeedback(() -> completeCmd, false);
        source.sendFeedback(() -> abandonCmd, false);
        source.sendFeedback(() -> levelCmd, false);
        
        // Send job slots information
        source.sendFeedback(() -> jobSlotsHeader, false);
        source.sendFeedback(() -> jobSlotsInfo, false);
        source.sendFeedback(() -> level0Info, false);
        source.sendFeedback(() -> level5Info, false);
        source.sendFeedback(() -> level10Info, false);
        source.sendFeedback(() -> level15Info, false);
        source.sendFeedback(() -> level20Info, false);
        source.sendFeedback(() -> level25Info, false);
        source.sendFeedback(() -> level30Info, false);
        
        return 1;
    }
    
    /**
     * Helper method to check and end expired streaks
     * This should be called at the beginning of each job command
     */
    private static void checkAndEndExpiredStreak(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // Only proceed if this is a player
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            return;
        }
        
        MinecraftServer server = player.getServer();
        if (server == null) return;
        
        JobManager jobManager = JobManager.getInstance();
        PlayerJobStreak streak = jobManager.getPlayerJobStreak(player);
        
        // Only check if player had an active streak
        if (streak.getStreakLength() > 0) {
            if (streak.checkAndEndExpiredStreak(server)) {
                // The streak was ended - silently update data
                jobManager.save();
            }
        }
    }
    
    /**
     * Shows a list of all jobs and their rewards.
     *
     * @param context The command context
     * @return 1 if successful
     */
    private static int listJobs(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // Check if the command was run by a player
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        List<Job> jobs = JobManager.getInstance().getPlayerJobs(player);
        PlayerJobLevel jobLevel = JobManager.getInstance().getPlayerJobLevel(player);
        PlayerJobStreak jobStreak = JobManager.getInstance().getPlayerJobStreak(player);
        PlayerBoosterData boosterData = JobManager.getInstance().getPlayerBoosterData(player);
        
        // Get the active job for this player
        Job activeJob = JobManager.getInstance().getActiveJob(player);
        
        // Get all bonuses
        int[] bonuses = JobManager.getBonusPercentages(jobLevel, jobStreak, boosterData);
        int levelBonus = bonuses[0];
        int streakBonus = bonuses[1];
        int boosterBonus = bonuses[2];
        int totalBonusPercent = bonuses[3];
        
        double totalMultiplier = 1.0 + (totalBonusPercent / 100.0);
        
        // Create a sorted list for display - active job first, then sorted by reward
        List<Job> sortedJobs = new ArrayList<>(jobs);
        sortedJobs.sort((job1, job2) -> {
            boolean job1Active = job1.equals(activeJob);
            boolean job2Active = job2.equals(activeJob);
            if (job1Active != job2Active) {
                return job1Active ? -1 : 1;
            }
            return Integer.compare(job2.getReward(), job1.getReward());
        });
        
        // Display job list header
        source.sendFeedback(() -> Text.literal("Available Jobs:").formatted(Formatting.GOLD, Formatting.BOLD), false);
        
        // Display job skips
        net.currencymod.jobs.PlayerJobSkipData skipData = JobManager.getInstance().getPlayerSkipData(player);
        int skipCount = skipData.getSkipCount();
        source.sendFeedback(() -> Text.literal("Job Skips Available: ").formatted(Formatting.GRAY)
                .append(Text.literal(String.valueOf(skipCount)).formatted(Formatting.GOLD)), false);
        
        // Display bonuses if any
        if (totalBonusPercent > 0) {
            MutableText bonusText = Text.literal("Bonuses: ").formatted(Formatting.YELLOW);
            
            if (levelBonus > 0) {
                bonusText.append(Text.literal("+" + levelBonus + "% (Level " + jobLevel.getLevel() + ") ").formatted(Formatting.GREEN));
            }
            
            if (streakBonus > 0) {
                bonusText.append(Text.literal("+" + streakBonus + "% (Streak) ").formatted(Formatting.AQUA));
            }
            
            if (boosterBonus > 0) {
                Booster activeBooster = boosterData.getActiveBooster();
                if (activeBooster != null) {
                    bonusText.append(Text.literal("+" + boosterBonus + "% (" + activeBooster.getType().getDisplayName() + ") ")
                            .formatted(Formatting.LIGHT_PURPLE));
                }
            }
            
            bonusText.append(Text.literal("= +" + totalBonusPercent + "% Total").formatted(Formatting.GOLD));
            source.sendFeedback(() -> bonusText, false);
        }
        
        // Show an empty line
        source.sendFeedback(() -> Text.literal(""), false);
        
        // Display each job
        for (Job job : sortedJobs) {
            boolean isActive = job.equals(activeJob);
            
            // Calculate adjusted reward based on bonuses
            int baseReward = job.getReward();
            int adjustedReward = (int) Math.ceil(baseReward * totalMultiplier);
            
            // Calculate adjusted quantity based on boosters
            int originalQuantity = job.getQuantity();
            int adjustedQuantity = JobManager.getAdjustedQuantity(originalQuantity, boosterData);
            
            // Use the new consolidated method that handles both reward and quantity adjustments
            Text clickableText = job.createClickableTextWithAdjustments(
                "/jobs activate ", 
                adjustedQuantity, 
                adjustedQuantity != originalQuantity, 
                adjustedReward, 
                baseReward != adjustedReward);
            
            source.sendFeedback(() -> clickableText, false);
        }
        
        return 1;
    }
    
    /**
     * Activate a job for a player
     */
    private static int activateJob(CommandContext<ServerCommandSource> context, String jobId) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        // Check if streak should end
        checkAndEndExpiredStreak(context);
        
        // Trim the job ID to handle any extra whitespace that might be causing issues
        String cleanJobId = jobId.trim();
        
        // Log the job ID being used for debugging
        CurrencyMod.LOGGER.debug("Attempting to activate job with ID: '{}'", cleanJobId);
        
        // Try to activate the job
        JobManager jobManager = JobManager.getInstance();
        boolean success = jobManager.activateJob(player, cleanJobId);
        
        if (!success) {
            // If there was an existing active job, first check if the job actually exists
            Job activeJob = jobManager.getActiveJob(player);
            
            if (activeJob != null) {
                // Player already has an active job - error message is sent by the job manager
                return 0;
            }
            
            // Log this situation for debugging
            CurrencyMod.LOGGER.warn("Failed to activate job for player {}, jobId: '{}'", 
                player.getName().getString(), cleanJobId);
            
            // Try to provide helpful feedback if the job doesn't exist
            source.sendFeedback(() -> Text.literal("⚠️ Could not activate the job. Please use ")
                .append(Text.literal("/jobs list").formatted(Formatting.YELLOW))
                .append(Text.literal(" to see available jobs.")), false);
                
            return 0;
        }
        
        // Get the activated job and calculate adjusted values
        Job activatedJob = jobManager.getActiveJob(player);
        if (activatedJob != null) {
            // Get player's boosters, level, and streak for adjustments
            PlayerJobLevel jobLevel = jobManager.getPlayerJobLevel(player);
            PlayerJobStreak jobStreak = jobManager.getPlayerJobStreak(player);
            PlayerBoosterData boosterData = jobManager.getPlayerBoosterData(player);
            
            // Calculate adjusted reward and quantity
            double totalMultiplier = JobManager.getTotalMultiplier(jobLevel, jobStreak, boosterData);
            int adjustedReward = (int) Math.ceil(activatedJob.getReward() * totalMultiplier);
            int adjustedQuantity = JobManager.getAdjustedQuantity(activatedJob.getQuantity(), boosterData);
            
            // Show the job with both adjusted reward and quantity
            source.sendFeedback(() -> activatedJob.getDisplayTextWithAdjustments(
                true, // includeStatus
                adjustedQuantity,
                boosterData.hasActiveBooster(), // showOriginalQuantity if there's a booster
                adjustedReward,
                totalMultiplier > 1.0), // showBaseReward if there's any bonus
                false);
            
            source.sendFeedback(() -> Text.literal("Job activated! Collect the items and use /jobs complete to finish.")
                .formatted(Formatting.GREEN), false);
        }
        
        return 1;
    }
    
    /**
     * Prompt to abandon the active job
     */
    private static int promptAbandonJob(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        // Check if streak should end
        checkAndEndExpiredStreak(context);
        
        // Get the active job to show the penalty before abandoning
        Job activeJob = JobManager.getInstance().getActiveJob(player);
        if (activeJob == null) {
            source.sendFeedback(() -> Text.literal("You don't have an active job to abandon.").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Check if this is a mega job - if so, no abandonment fee
        if (activeJob.isMegaJob()) {
            source.sendFeedback(() -> Text.literal("✓ MEGA jobs can be abandoned without a penalty. ")
                .formatted(Formatting.GREEN)
                .append(Text.literal("Type ")
                .formatted(Formatting.YELLOW))
                .append(Text.literal("/jobs abandon confirm")
                .formatted(Formatting.AQUA))
                .append(Text.literal(" to confirm.")
                .formatted(Formatting.YELLOW)), false);
            return 1;
        }
        
        // For regular jobs, calculate the penalty
        PlayerJobLevel jobLevel = JobManager.getInstance().getPlayerJobLevel(player);
        PlayerJobStreak jobStreak = JobManager.getInstance().getPlayerJobStreak(player);
        int level = jobLevel.getLevel();
        
        // Calculate the adjusted reward with level and streak bonuses
        int baseReward = activeJob.getReward();
        double totalMultiplier = JobManager.getTotalMultiplier(jobLevel, jobStreak);
        int adjustedReward = (int) Math.ceil(baseReward * totalMultiplier);
        
        // Calculate penalty as 75% of the adjusted reward
        int penalty = Math.max(1, (int)(adjustedReward * 0.75));
        
        source.sendFeedback(() -> Text.literal("⚠️ Warning: ").formatted(Formatting.RED, Formatting.BOLD)
                .append(Text.literal("Abandoning this job will cost you ")
                .formatted(Formatting.YELLOW))
                .append(Text.literal("$" + penalty).formatted(Formatting.RED, Formatting.BOLD))
                .append(Text.literal(". Type ").formatted(Formatting.YELLOW))
                .append(Text.literal("/jobs abandon confirm").formatted(Formatting.RED))
                .append(Text.literal(" to confirm.").formatted(Formatting.YELLOW)), false);
        
        return 1;
    }
    
    /**
     * Confirm and abandon the active job for the player
     */
    private static int confirmAbandonJob(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        // Check if streak should end
        checkAndEndExpiredStreak(context);
        
        boolean success = JobManager.getInstance().abandonJob(player);
        
        if (!success) {
            // Error message is sent by the job manager
            return 0;
        }
        
        return 1;
    }
    
    /**
     * Complete the active job for the player
     */
    private static int completeJob(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        // Check if streak should end
        checkAndEndExpiredStreak(context);
        
        // Get necessary job info before completing it
        Job job = JobManager.getInstance().getActiveJob(player);
        if (job == null) {
            source.sendError(Text.literal("You don't have an active job to complete"));
            return 0;
        }
        
        // Get bonus and reward information before completing the job
        PlayerJobLevel jobLevel = JobManager.getInstance().getPlayerJobLevel(player);
        PlayerJobStreak jobStreak = JobManager.getInstance().getPlayerJobStreak(player);
        PlayerBoosterData boosterData = JobManager.getInstance().getPlayerBoosterData(player);
        
        // Calculate reward with bonuses
        double totalMultiplier = JobManager.getTotalMultiplier(jobLevel, jobStreak, boosterData);
        int baseReward = job.getReward();
        int finalReward = (int) Math.ceil(baseReward * totalMultiplier);
        String itemName = job.getItem().getName().getString();
        int quantity = JobManager.getAdjustedQuantity(job.getQuantity(), boosterData);
        
        // Complete the job
        boolean success = JobManager.getInstance().completeJob(player);
        
        if (!success) {
            source.sendError(Text.literal("You don't have enough items to complete your job"));
            return 0;
        }
        
        // Send success messages
        source.sendFeedback(() -> Text.literal("✓ ").formatted(Formatting.GREEN)
            .append(Text.literal("Job Completed!").formatted(Formatting.GREEN, Formatting.BOLD)), false);
            
        source.sendFeedback(() -> Text.literal("• You sold: ").formatted(Formatting.GRAY)
            .append(Text.literal(quantity + " " + itemName).formatted(Formatting.AQUA)), false);
            
        source.sendFeedback(() -> Text.literal("• You earned: ").formatted(Formatting.GRAY)
            .append(Text.literal("$" + finalReward).formatted(Formatting.GOLD)), false);
        
        // Show bonus info if there was a bonus
        if (finalReward > baseReward) {
            int[] bonuses = JobManager.getBonusPercentages(jobLevel, jobStreak, boosterData);
            int totalBonusPercent = bonuses[3];
            
            source.sendFeedback(() -> Text.literal("  (").formatted(Formatting.GRAY)
                .append(Text.literal("+" + totalBonusPercent + "%").formatted(Formatting.GREEN))
                .append(Text.literal(" bonus applied)").formatted(Formatting.GRAY)), false);
        }

        return 1;
    }
    
    /**
     * Shows information about a player's active job.
     *
     * @param context The command context
     * @return 1 if successful, 0 if no active job
     */
    private static int showActiveJob(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // Check if the command was run by a player
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        UUID playerUuid = player.getUuid();
        
        // Get the active job
        Job job = JobManager.getInstance().getActiveJob(player);
        if (job == null) {
            source.sendFeedback(() -> Text.literal("You don't have an active job. Use /jobs list to see available jobs.").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Get player job level and streak
        PlayerJobLevel jobLevel = JobManager.getInstance().getPlayerJobLevel(playerUuid);
        PlayerJobStreak jobStreak = JobManager.getInstance().getPlayerJobStreak(player);
        PlayerBoosterData boosterData = JobManager.getInstance().getPlayerBoosterData(player);
        
        // Get bonuses
        int[] bonuses = JobManager.getBonusPercentages(jobLevel, jobStreak, boosterData);
        int levelBonus = bonuses[0];
        int streakBonus = bonuses[1];
        int boosterBonus = bonuses[2];
        int totalBonusPercent = bonuses[3];
        
        // Calculate the actual reward
        double totalMultiplier = 1.0 + (totalBonusPercent / 100.0);
        int baseReward = job.getReward();
        int actualReward = (int) Math.ceil(baseReward * totalMultiplier);
        
        // Calculate adjusted quantity if a booster with quantity reduction is active
        int originalQuantity = job.getQuantity();
        int adjustedQuantity = JobManager.getAdjustedQuantity(originalQuantity, boosterData);
        
        // Get counts of collected items
        int itemsCollected = JobManager.getInstance().getCollectedItems(playerUuid, job.getItem());
        int itemsRemaining = adjustedQuantity - itemsCollected;
        float percentComplete = (float) itemsCollected / adjustedQuantity * 100;
        
        // Create header
        source.sendFeedback(() -> Text.literal("Your Active Job:").formatted(Formatting.GOLD, Formatting.BOLD), false);
        
        // Use the new consolidated method to display job details with both adjusted quantity and reward
        MutableText jobDisplayText = job.getDisplayTextWithAdjustments(
            true, // Include active status
            adjustedQuantity, 
            originalQuantity != adjustedQuantity, // Show original quantity if different
            actualReward,
            baseReward != actualReward // Show base reward if different
        );
        source.sendFeedback(() -> jobDisplayText, false);
        
        // Display individual bonuses if any
        if (totalBonusPercent > 0) {
            MutableText bonusText = Text.literal("  Bonuses: ").formatted(Formatting.GRAY);
            
            if (levelBonus > 0) {
                bonusText.append(Text.literal("+" + levelBonus + "% (Level " + jobLevel.getLevel() + ") ").formatted(Formatting.GREEN));
            }
            
            if (streakBonus > 0) {
                bonusText.append(Text.literal("+" + streakBonus + "% (Streak) ").formatted(Formatting.AQUA));
            }
            
            if (boosterBonus > 0) {
                Booster activeBooster = boosterData.getActiveBooster();
                if (activeBooster != null) {
                    bonusText.append(Text.literal("+" + boosterBonus + "% (" + activeBooster.getType().getDisplayName() + ") ")
                            .formatted(Formatting.LIGHT_PURPLE));
                }
            }
            
            source.sendFeedback(() -> bonusText, false);
        }
        
        // Progress details
        source.sendFeedback(() -> Text.literal("• Progress: ")
            .formatted(Formatting.GRAY)
            .append(Text.literal(itemsCollected + "/" + adjustedQuantity + " (" + String.format("%.1f", percentComplete) + "%)").formatted(Formatting.WHITE)), false);
        
        // Progress bar
        int barLength = 20;
        int filledBars = (int) (barLength * percentComplete / 100);
        StringBuilder progressBar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            if (i < filledBars) {
                progressBar.append("■");
            } else {
                progressBar.append("□");
            }
        }
        progressBar.append("]");
        
        // Make barColor final before using it in lambda
        final Formatting barColor;
        if (percentComplete >= 85) {
            barColor = Formatting.GREEN;
        } else if (percentComplete >= 50) {
            barColor = Formatting.YELLOW;
        } else if (percentComplete >= 25) {
            barColor = Formatting.GOLD;
        } else {
            barColor = Formatting.RED;
        }
        
        source.sendFeedback(() -> Text.literal("  " + progressBar.toString()).formatted(barColor), false);
        
        // Action buttons
        MutableText actionText = Text.literal("• Actions: ").formatted(Formatting.GRAY);
        
        // Add abandon button
        Text abandonButton = Text.literal("[Abandon Job]")
            .styled(style -> style
                .withColor(Formatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jobs abandon"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Abandon this job (no reward)")))
            );
        actionText.append(abandonButton);
        
        // Add complete button if job is complete
        if (itemsCollected >= adjustedQuantity) {
            actionText.append(Text.literal(" ").formatted(Formatting.GRAY));
            Text completeButton = Text.literal("[Complete Job]")
                .styled(style -> style
                    .withColor(Formatting.GREEN)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jobs complete"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Complete this job and get your reward!")))
                );
            actionText.append(completeButton);
        }
        
        source.sendFeedback(() -> actionText, false);
        
        return 1;
    }
    
    /**
     * Shows the player's job level.
     *
     * @param context The command context
     * @return 1 if successful
     */
    private static int showPlayerLevel(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // Check if the command was run by a player
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        PlayerJobLevel jobLevel = JobManager.getInstance().getPlayerJobLevel(player);
        PlayerJobStreak jobStreak = JobManager.getInstance().getPlayerJobStreak(player);
        PlayerBoosterData boosterData = JobManager.getInstance().getPlayerBoosterData(player);
        
        // Get current level data
        int level = jobLevel.getLevel();
        int jobsCompleted = jobLevel.getCompletedJobs();
        int jobsRequired = jobLevel.getRequiredJobsForNextLevel();
        int nextLevel = level + 1;
        boolean isMaxLevel = level >= PlayerJobLevel.MAX_LEVEL;
        
        // Get all bonuses
        int[] bonuses = JobManager.getBonusPercentages(jobLevel, jobStreak, boosterData);
        int levelBonus = bonuses[0];
        int streakBonus = bonuses[1]; 
        int boosterBonus = bonuses[2];
        int totalBonusPercent = bonuses[3];
        
        // Calculate percent complete
        float percentComplete = (float) jobsCompleted / jobsRequired * 100;
        
        // Create progress bar
        int barLength = 20;
        int filledBars = (int) (barLength * percentComplete / 100);
        StringBuilder progressBar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            if (i < filledBars) {
                progressBar.append("■");
            } else {
                progressBar.append("□");
            }
        }
        progressBar.append("]");
        
        // Make barColor final before using it in lambda
        final Formatting barColor;
        if (percentComplete >= 85) {
            barColor = Formatting.GREEN;
        } else if (percentComplete >= 50) {
            barColor = Formatting.YELLOW;
        } else if (percentComplete >= 25) {
            barColor = Formatting.GOLD;
        } else {
            barColor = Formatting.RED;
        }
        
        // Display level info
        source.sendFeedback(() -> Text.literal("Your Job Stats:").formatted(Formatting.GOLD, Formatting.BOLD), false);
        
        // Level info
        MutableText levelText = Text.literal("• Level: ").formatted(Formatting.GRAY)
            .append(Text.literal(String.valueOf(level)).formatted(Formatting.AQUA, Formatting.BOLD));
        
        if (level > 0) {
            levelText.append(Text.literal(" (+" + levelBonus + "% reward bonus)").formatted(Formatting.GREEN));
        }
        source.sendFeedback(() -> levelText, false);
        
        // Progress to next level
        if (!isMaxLevel) {
            source.sendFeedback(() -> Text.literal("• Next Level: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal(jobsCompleted + "/" + jobsRequired + " jobs (" + String.format("%.1f", percentComplete) + "%)").formatted(Formatting.WHITE)), false);
            
            // Progress bar
            source.sendFeedback(() -> Text.literal("  " + progressBar.toString()).formatted(barColor), false);
            
            source.sendFeedback(() -> Text.literal("• Reaching Level " + nextLevel + ": ")
                .formatted(Formatting.GRAY)
                .append(Text.literal("+" + (levelBonus + PlayerJobLevel.LEVEL_BONUS_PERCENT) + "% reward bonus").formatted(Formatting.GREEN)), false);
        } else {
            source.sendFeedback(() -> Text.literal("• Maximum level reached!").formatted(Formatting.GREEN, Formatting.BOLD), false);
        }
        
        // Streak info
        int streakLength = jobStreak.getStreakLength();
        source.sendFeedback(() -> Text.literal("• Streak: ").formatted(Formatting.GRAY)
            .append(Text.literal(String.valueOf(streakLength) + " day" + (streakLength == 1 ? "" : "s")).formatted(Formatting.AQUA)), false);
        
        if (streakLength > 0) {
            source.sendFeedback(() -> Text.literal("  • Streak Bonus: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal("+" + streakBonus + "% to rewards").formatted(Formatting.AQUA)), false);
            
            source.sendFeedback(() -> Text.literal("  • Tip: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal("Complete at least 1 job every Minecraft day to maintain your streak!").formatted(Formatting.YELLOW)), false);
        } else {
            source.sendFeedback(() -> Text.literal("  • Tip: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal("Complete jobs on consecutive Minecraft days to earn streak bonuses!").formatted(Formatting.YELLOW)), false);
        }
        
        // Active booster info
        Booster activeBooster = boosterData.getActiveBooster();
        if (activeBooster != null) {
            BoosterType type = activeBooster.getType();
            source.sendFeedback(() -> Text.literal("• Active Booster: ").formatted(Formatting.GRAY)
                .append(Text.literal(type.getDisplayName()).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)), false);
            
            source.sendFeedback(() -> Text.literal("  • Bonus: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal("+" + type.getRewardBonus() + "% to rewards").formatted(Formatting.LIGHT_PURPLE)), false);
            
            if (type.getQuantityReduction() > 0) {
                source.sendFeedback(() -> Text.literal("  • Item Reduction: ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal("-" + type.getQuantityReduction() + "% to required items").formatted(Formatting.LIGHT_PURPLE)), false);
            }
            
            source.sendFeedback(() -> Text.literal("  • Time Remaining: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal(activeBooster.getFormattedRemainingTime()).formatted(Formatting.YELLOW)), false);
        }
        
        // Total bonus info
        if (totalBonusPercent > 0) {
            source.sendFeedback(() -> Text.literal("• Total Bonus: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal("+" + totalBonusPercent + "% to rewards").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        }
        
        // Jobs allowed
        int jobsAllowed = jobLevel.getJobsAllowed();
        source.sendFeedback(() -> Text.literal("• Jobs Allowed: ")
            .formatted(Formatting.GRAY)
            .append(Text.literal(String.valueOf(jobsAllowed)).formatted(Formatting.WHITE)), false);
        
        return 1;
    }

    /**
     * Shows the player's boosters.
     *
     * @param source The command source
     * @return 1 if successful
     */
    private static int showBoosters(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        PlayerBoosterData boosterData = JobManager.getInstance().getPlayerBoosterData(player);
        
        // Display header
        source.sendFeedback(() -> Text.literal("Your Job Boosters:").formatted(Formatting.GOLD, Formatting.BOLD), false);
        
        // Show active booster if any
        Booster activeBooster = boosterData.getActiveBooster();
        if (activeBooster != null) {
            BoosterType type = activeBooster.getType();
            
            source.sendFeedback(() -> Text.literal("• Active: ").formatted(Formatting.GRAY)
                .append(Text.literal(type.getDisplayName()).formatted(
                    type == BoosterType.PREMIUM ? Formatting.GOLD : Formatting.AQUA, Formatting.BOLD)), false);
            
            // Show remaining time
            source.sendFeedback(() -> Text.literal("  • Time Remaining: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal(activeBooster.getFormattedRemainingTime()).formatted(Formatting.YELLOW)), false);
            
            // Show bonuses
            source.sendFeedback(() -> Text.literal("  • Reward Bonus: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal("+" + type.getRewardBonus() + "%").formatted(Formatting.GREEN)), false);
            
            if (type.getQuantityReduction() > 0) {
                source.sendFeedback(() -> Text.literal("  • Item Reduction: ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal("-" + type.getQuantityReduction() + "%").formatted(Formatting.LIGHT_PURPLE)), false);
            }
        } else {
            source.sendFeedback(() -> Text.literal("• No Active Booster").formatted(Formatting.GRAY), false);
        }
        
        // Show owned boosters
        source.sendFeedback(() -> Text.literal("• Owned Boosters:").formatted(Formatting.YELLOW), false);
        
        boolean hasAnyBoosters = false;
        for (BoosterType type : BoosterType.values()) {
            int count = boosterData.getBoosterCount(type);
            if (count > 0) {
                hasAnyBoosters = true;
                
                // Create formatted booster text
                MutableText boosterText = Text.literal("  • ").formatted(Formatting.GRAY)
                    .append(Text.literal(type.getDisplayName()).formatted(
                        type == BoosterType.PREMIUM ? Formatting.GOLD : Formatting.AQUA))
                    .append(Text.literal(" x" + count).formatted(Formatting.WHITE))
                    .append(Text.literal(" (+" + type.getRewardBonus() + "% rewards").formatted(Formatting.GREEN));
                
                if (type.getQuantityReduction() > 0) {
                    boosterText.append(Text.literal(", -" + type.getQuantityReduction() + "% items").formatted(Formatting.LIGHT_PURPLE));
                }
                
                boosterText.append(Text.literal(")").formatted(Formatting.GREEN));
                
                // Add use button if no active booster
                if (activeBooster == null) {
                    Text useButton = Text.literal(" [Use]")
                        .styled(style -> style
                            .withColor(Formatting.YELLOW)
                            .withUnderline(true)
                            .withClickEvent(new ClickEvent(
                                ClickEvent.Action.RUN_COMMAND, 
                                "/jobs boosters use " + type.name().toLowerCase()
                            ))
                            .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT, 
                                Text.literal("Activate this booster")
                            ))
                        );
                    boosterText.append(useButton);
                }
                
                source.sendFeedback(() -> boosterText, false);
            }
        }
        
        if (!hasAnyBoosters) {
            source.sendFeedback(() -> Text.literal("  • None").formatted(Formatting.RED), false);
            source.sendFeedback(() -> Text.literal("  • Complete jobs to have a chance of finding boosters!").formatted(Formatting.YELLOW), false);
        }
        
        // Display information about booster effects
        source.sendFeedback(() -> Text.literal("• Info:").formatted(Formatting.AQUA), false);
        source.sendFeedback(() -> Text.literal("  • Boosters last for 30 minutes").formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> Text.literal("  • Only one booster can be active at a time").formatted(Formatting.GRAY), false);
        
        return 1;
    }
    
    /**
     * Uses a booster.
     *
     * @param source The command source
     * @param typeStr The booster type string
     * @return 1 if successful
     */
    private static int useBooster(ServerCommandSource source, String typeStr) {
        ServerPlayerEntity player = source.getPlayer();
        PlayerBoosterData boosterData = JobManager.getInstance().getPlayerBoosterData(player);
        
        // Check if player already has an active booster
        if (boosterData.hasActiveBooster()) {
            source.sendError(Text.literal("You already have an active booster! Wait for it to expire before using another."));
            return 0;
        }
        
        // Parse booster type
        BoosterType type;
        try {
            type = BoosterType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("Invalid booster type: " + typeStr));
            return 0;
        }
        
        // Try to use the booster
        if (boosterData.useBooster(type)) {
            // Save changes
            JobManager.getInstance().save();
            
            // Send success message
            source.sendFeedback(() -> Text.literal("You activated a ").formatted(Formatting.GREEN)
                .append(Text.literal(type.getDisplayName()).formatted(
                    type == BoosterType.PREMIUM ? Formatting.GOLD : Formatting.AQUA, Formatting.BOLD))
                .append(Text.literal("!").formatted(Formatting.GREEN)), false);
            
            // Show the benefits
            source.sendFeedback(() -> Text.literal("• Duration: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal(type.getDurationMinutes() + " minutes").formatted(Formatting.YELLOW)), false);
            
            source.sendFeedback(() -> Text.literal("• Reward Bonus: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal("+" + type.getRewardBonus() + "%").formatted(Formatting.GREEN)), false);
            
            if (type.getQuantityReduction() > 0) {
                source.sendFeedback(() -> Text.literal("• Item Reduction: ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal("-" + type.getQuantityReduction() + "%").formatted(Formatting.LIGHT_PURPLE)), false);
            }
            
            // Let the player know how to check booster time
            source.sendFeedback(() -> Text.literal("Type ")
                .formatted(Formatting.GRAY)
                .append(Text.literal("/jobs boosters").formatted(Formatting.YELLOW))
                .append(Text.literal(" to check your booster status.").formatted(Formatting.GRAY)), false);
            
            return 1;
        } else {
            source.sendError(Text.literal("You don't have any " + type.getDisplayName() + " boosters!"));
            return 0;
        }
    }
    
    /**
     * Prompt to skip the active job
     */
    private static int promptSkipJob(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        // Check if streak should end
        checkAndEndExpiredStreak(context);
        
        // Get the active job
        Job activeJob = JobManager.getInstance().getActiveJob(player);
        if (activeJob == null) {
            source.sendFeedback(() -> Text.literal("You don't have an active job to skip.").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Get player's skip count
        net.currencymod.jobs.PlayerJobSkipData skipData = JobManager.getInstance().getPlayerSkipData(player);
        int skipCount = skipData.getSkipCount();
        
        if (skipCount <= 0) {
            source.sendFeedback(() -> Text.literal("You don't have any job skips available.").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Show confirmation message
        source.sendFeedback(() -> Text.literal("⚠️ ").formatted(Formatting.YELLOW, Formatting.BOLD)
                .append(Text.literal("Skip this job? ").formatted(Formatting.YELLOW))
                .append(Text.literal("This will complete your current job without penalty and generate a new one. ").formatted(Formatting.GRAY))
                .append(Text.literal("Type ").formatted(Formatting.YELLOW))
                .append(Text.literal("/jobs skip confirm").formatted(Formatting.RED))
                .append(Text.literal(" to confirm.").formatted(Formatting.YELLOW)), false);
        
        source.sendFeedback(() -> Text.literal("Job Skips Available: ").formatted(Formatting.GRAY)
                .append(Text.literal(String.valueOf(skipCount)).formatted(Formatting.GOLD)), false);
        
        return 1;
    }
    
    /**
     * Confirm and skip the active job for the player
     */
    private static int confirmSkipJob(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        // Check if streak should end
        checkAndEndExpiredStreak(context);
        
        boolean success = JobManager.getInstance().useJobSkip(player);
        
        if (!success) {
            // Error message is sent by the job manager
            return 0;
        }
        
        return 1;
    }
} 