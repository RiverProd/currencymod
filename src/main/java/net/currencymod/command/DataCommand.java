package net.currencymod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.currencymod.data.DataManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Command for managing mod data, backups, and saves
 */
public class DataCommand {

    /**
     * Register the data command
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            literal("currencydata")
                .requires(source -> source.hasPermissionLevel(3)) // Require admin permission level
                .executes(DataCommand::showHelp)
                .then(literal("save")
                    .executes(DataCommand::forceSave)
                )
                .then(literal("status")
                    .executes(DataCommand::showStatus)
                )
                .then(literal("backup")
                    .executes(DataCommand::createBackup)
                )
                .then(literal("backups")
                    .executes(DataCommand::listBackups)
                )
                .then(literal("restore")
                    .then(argument("backup", StringArgumentType.greedyString())
                        .executes(context -> restoreBackup(context, StringArgumentType.getString(context, "backup")))
                    )
                )
        );
    }
    
    /**
     * Display help for the data command
     */
    private static int showHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        Text header = Text.literal("💾 ").formatted(Formatting.GOLD)
            .append(Text.literal("Currency Mod Data Management").formatted(Formatting.GOLD, Formatting.BOLD));
            
        source.sendFeedback(() -> header, false);
        source.sendFeedback(() -> Text.literal("Available commands:").formatted(Formatting.WHITE), false);
        source.sendFeedback(() -> Text.literal("/currencydata save").formatted(Formatting.YELLOW)
            .append(Text.literal(" - Force an immediate save of all data").formatted(Formatting.GRAY)), false);
        source.sendFeedback(() -> Text.literal("/currencydata status").formatted(Formatting.YELLOW)
            .append(Text.literal(" - Show current data system status").formatted(Formatting.GRAY)), false);
        source.sendFeedback(() -> Text.literal("/currencydata backup").formatted(Formatting.YELLOW)
            .append(Text.literal(" - Create a new backup").formatted(Formatting.GRAY)), false);
        source.sendFeedback(() -> Text.literal("/currencydata backups").formatted(Formatting.YELLOW)
            .append(Text.literal(" - List available backups").formatted(Formatting.GRAY)), false);
        source.sendFeedback(() -> Text.literal("/currencydata restore <backup>").formatted(Formatting.YELLOW)
            .append(Text.literal(" - Restore from a backup").formatted(Formatting.GRAY)), false);
            
        return 1;
    }
    
    /**
     * Force an immediate save of all data
     */
    private static int forceSave(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        source.sendFeedback(() -> Text.literal("Forcing data save...").formatted(Formatting.YELLOW), true);
        
        try {
            DataManager.getInstance().forceSave("command");
            source.sendFeedback(() -> Text.literal("All data saved successfully.").formatted(Formatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("Failed to save data: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * Show the current data system status
     */
    private static int showStatus(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        String status = DataManager.getInstance().getStatus();
        String[] lines = status.split("\n");
        
        source.sendFeedback(() -> Text.literal("📊 Data System Status:").formatted(Formatting.AQUA, Formatting.BOLD), true);
        
        for (String line : lines) {
            if (line.contains("Data Manager Status")) continue; // Skip header line
            source.sendFeedback(() -> Text.literal(line).formatted(Formatting.WHITE), false);
        }
        
        int sinceLastSave = DataManager.getInstance().getSecondsSinceLastSave();
        if (sinceLastSave >= 0) {
            Text timeText = formatTimeAgo(sinceLastSave);
            source.sendFeedback(() -> Text.literal("Last save was ").formatted(Formatting.WHITE)
                .append(timeText), false);
        }
        
        // Add info about next auto-save
        source.sendFeedback(() -> Text.literal("Auto-saves occur every 5 minutes").formatted(Formatting.GRAY), false);
        
        return 1;
    }
    
    /**
     * Create a new backup
     */
    private static int createBackup(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        source.sendFeedback(() -> Text.literal("Creating backup...").formatted(Formatting.YELLOW), true);
        
        try {
            DataManager.getInstance().createBackup();
            source.sendFeedback(() -> Text.literal("Backup created successfully.").formatted(Formatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("Failed to create backup: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * List available backups
     */
    private static int listBackups(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        String[] backups = DataManager.getInstance().getAvailableBackups();
        
        if (backups.length == 0) {
            source.sendFeedback(() -> Text.literal("No backups available.").formatted(Formatting.YELLOW), false);
            return 0;
        }
        
        source.sendFeedback(() -> Text.literal("📁 Available backups:").formatted(Formatting.AQUA, Formatting.BOLD), true);
        
        for (String backup : backups) {
            String timestamp = backup.substring(7); // Remove "backup_" prefix
            timestamp = timestamp.replace('_', ' ');
            
            final String finalTimestamp = timestamp;
            source.sendFeedback(() -> Text.literal(" • ").formatted(Formatting.GRAY)
                .append(Text.literal(finalTimestamp).formatted(Formatting.YELLOW))
                .append(Text.literal(" - "))
                .append(Text.literal("/currencydata restore ").formatted(Formatting.WHITE))
                .append(Text.literal(finalTimestamp).formatted(Formatting.GREEN)), false);
        }
        
        return 1;
    }
    
    /**
     * Restore from a backup
     */
    private static int restoreBackup(CommandContext<ServerCommandSource> context, String backupName) {
        ServerCommandSource source = context.getSource();
        
        // Confirm with the user
        source.sendFeedback(() -> Text.literal("⚠ WARNING: This will overwrite all current data! ⚠")
            .formatted(Formatting.RED, Formatting.BOLD), true);
        source.sendFeedback(() -> Text.literal("Restoring from backup: ").formatted(Formatting.YELLOW)
            .append(Text.literal(backupName).formatted(Formatting.GREEN)), true);
        source.sendFeedback(() -> Text.literal("A backup of the current data will be created first.").formatted(Formatting.GRAY), false);
        
        try {
            boolean success = DataManager.getInstance().restoreFromBackup(backupName);
            
            if (success) {
                source.sendFeedback(() -> Text.literal("✅ Backup restored successfully.").formatted(Formatting.GREEN, Formatting.BOLD), true);
                return 1;
            } else {
                source.sendError(Text.literal("❌ Failed to restore backup. See console for details."));
                return 0;
            }
        } catch (Exception e) {
            source.sendError(Text.literal("Error restoring backup: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * Format a time ago in a human-readable format
     */
    private static Text formatTimeAgo(int seconds) {
        if (seconds < 60) {
            return Text.literal(seconds + " seconds ago").formatted(getTimeColor(seconds));
        } else if (seconds < 3600) {
            int minutes = seconds / 60;
            return Text.literal(minutes + " minute" + (minutes > 1 ? "s" : "") + " ago").formatted(getTimeColor(seconds));
        } else {
            int hours = seconds / 3600;
            int minutes = (seconds % 3600) / 60;
            return Text.literal(hours + " hour" + (hours > 1 ? "s" : "") + 
                (minutes > 0 ? " " + minutes + " minute" + (minutes > 1 ? "s" : "") : "") + " ago")
                .formatted(getTimeColor(seconds));
        }
    }
    
    /**
     * Get a color based on how long ago something happened
     */
    private static Formatting getTimeColor(int seconds) {
        if (seconds < 60) {
            return Formatting.GREEN; // Last minute
        } else if (seconds < 300) { // 5 minutes
            return Formatting.YELLOW;
        } else if (seconds < 1800) { // 30 minutes
            return Formatting.GOLD;
        } else {
            return Formatting.RED;
        }
    }
} 