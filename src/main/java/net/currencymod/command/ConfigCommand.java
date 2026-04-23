package net.currencymod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.currencymod.config.ModConfig;
import net.currencymod.plots.PlotType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import net.minecraft.server.MinecraftServer;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Command for managing and viewing the mod configuration
 */
public class ConfigCommand {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigCommand.class);
    
    /**
     * Register the config command
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            literal("currencyconfig")
                .requires(source -> source.hasPermissionLevel(2)) // Require permission level 2 (admin/op)
                .executes(ConfigCommand::showConfig)
                .then(literal("reload")
                    .executes(ConfigCommand::reloadConfig)
                )
                .then(literal("save")
                    .executes(ConfigCommand::saveConfig)
                )
                .then(literal("debug")
                    .executes(ConfigCommand::debugConfig)
                )
        );
    }
    
    /**
     * Display the current configuration values
     */
    private static int showConfig(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        Text header = Text.literal("⚙️ ").formatted(Formatting.GOLD)
            .append(Text.literal("CurrencyMod Configuration").formatted(Formatting.GOLD, Formatting.BOLD));
        
        // Show plot configuration
        Text plotsHeader = Text.literal("\n\nPlot Prices and Taxes:").formatted(Formatting.YELLOW, Formatting.BOLD);
        
        StringBuilder plotInfo = new StringBuilder();
        for (PlotType type : PlotType.values()) {
            plotInfo.append("\n• ")
                .append(type.name())
                .append(": ")
                .append(type.getDisplayName())
                .append(" - $")
                .append(type.getPurchasePrice())
                .append(" (Tax: $")
                .append(type.getDailyTax())
                .append("/day)");
        }
        
        // Show job multipliers
        ModConfig config = ModConfig.getInstance();
        Text jobMultipliersHeader = Text.literal("\n\nJob Multipliers:").formatted(Formatting.YELLOW, Formatting.BOLD);
        Text jobMultipliersInfo = Text.literal("\n• Job Quantity Multiplier: ")
            .append(Text.literal(String.valueOf(config.getJobQuantityMultiplier())).formatted(Formatting.AQUA))
            .append(Text.literal("\n• Job Payout Multiplier: ")
            .append(Text.literal(String.valueOf(config.getJobPayoutMultiplier())).formatted(Formatting.AQUA)))
            .formatted(Formatting.WHITE);
        
        Text reloadHelp = Text.literal("\n\nUse ")
            .append(Text.literal("/currencyconfig reload").formatted(Formatting.GREEN))
            .append(Text.literal(" to reload the configuration from file"))
            .formatted(Formatting.WHITE);
        
        Text configPath = Text.literal("\nConfiguration is stored in ")
            .append(Text.literal("currency_mod/config/config.json").formatted(Formatting.AQUA))
            .formatted(Formatting.WHITE);
        
        source.sendFeedback(() -> header
            .copy()
            .append(plotsHeader)
            .append(Text.literal(plotInfo.toString()).formatted(Formatting.WHITE))
            .append(jobMultipliersHeader)
            .append(jobMultipliersInfo)
            .append(reloadHelp)
            .append(configPath), false);
        
        return 1;
    }
    
    /**
     * Reload the configuration from disk
     */
    private static int reloadConfig(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // Reload the configuration
        ModConfig.getInstance().load(source.getServer());
        
        source.sendFeedback(() -> Text.literal("⚙️ Configuration reloaded from disk!").formatted(Formatting.GREEN), true);
        
        // Show the current configuration after reload
        return showConfig(context);
    }

    /**
     * Force-save the configuration
     */
    private static int saveConfig(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // Save the configuration
        try {
            LOGGER.info("Manual save initiated via command");
            ModConfig.getInstance().save(source.getServer());
            source.sendFeedback(() -> Text.literal("⚙️ Configuration saved to disk!").formatted(Formatting.GREEN), true);
        } catch (Exception e) {
            LOGGER.error("Error saving config: {}", e.getMessage(), e);
            source.sendError(Text.literal("Error saving config: " + e.getMessage()).formatted(Formatting.RED));
        }
        
        return 1;
    }

    /**
     * Show debug information about the configuration
     */
    private static int debugConfig(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        MinecraftServer server = source.getServer();
        
        // Show the paths
        Path runDir = server.getRunDirectory();
        Path configDirPath = runDir.resolve(ModConfig.getConfigDir());
        Path configFilePath = configDirPath.resolve(ModConfig.getConfigFile());
        
        File configDir = configDirPath.toFile();
        File configFile = configFilePath.toFile();
        
        Text header = Text.literal("⚙️ ").formatted(Formatting.GOLD)
            .append(Text.literal("CurrencyMod Configuration Debug Info").formatted(Formatting.GOLD, Formatting.BOLD));
        
        Text paths = Text.literal("\n\nPaths:").formatted(Formatting.YELLOW, Formatting.BOLD)
            .append(Text.literal("\nServer Run Directory: ").formatted(Formatting.WHITE))
            .append(Text.literal(runDir.toString()).formatted(Formatting.AQUA))
            .append(Text.literal("\nConfig Directory: ").formatted(Formatting.WHITE))
            .append(Text.literal(configDir.getAbsolutePath()).formatted(Formatting.AQUA))
            .append(Text.literal("\nConfig File: ").formatted(Formatting.WHITE))
            .append(Text.literal(configFile.getAbsolutePath()).formatted(Formatting.AQUA));
        
        Text existence = Text.literal("\n\nFile Status:").formatted(Formatting.YELLOW, Formatting.BOLD)
            .append(Text.literal("\nConfig Directory Exists: ").formatted(Formatting.WHITE))
            .append(Text.literal(String.valueOf(configDir.exists())).formatted(configDir.exists() ? Formatting.GREEN : Formatting.RED))
            .append(Text.literal("\nConfig File Exists: ").formatted(Formatting.WHITE))
            .append(Text.literal(String.valueOf(configFile.exists())).formatted(configFile.exists() ? Formatting.GREEN : Formatting.RED));
        
        MutableText rwPerms = Text.literal("\n\nPermissions:").formatted(Formatting.YELLOW, Formatting.BOLD);
        
        if (configDir.exists()) {
            rwPerms.append(Text.literal("\nDirectory Readable: ").formatted(Formatting.WHITE))
                .append(Text.literal(String.valueOf(configDir.canRead())).formatted(configDir.canRead() ? Formatting.GREEN : Formatting.RED))
                .append(Text.literal("\nDirectory Writable: ").formatted(Formatting.WHITE))
                .append(Text.literal(String.valueOf(configDir.canWrite())).formatted(configDir.canWrite() ? Formatting.GREEN : Formatting.RED));
        } else {
            rwPerms.append(Text.literal("\nParent Directory Readable: ").formatted(Formatting.WHITE))
                .append(Text.literal(String.valueOf(configDir.getParentFile().canRead())).formatted(configDir.getParentFile().canRead() ? Formatting.GREEN : Formatting.RED))
                .append(Text.literal("\nParent Directory Writable: ").formatted(Formatting.WHITE))
                .append(Text.literal(String.valueOf(configDir.getParentFile().canWrite())).formatted(configDir.getParentFile().canWrite() ? Formatting.GREEN : Formatting.RED));
        }
        
        if (configFile.exists()) {
            rwPerms.append(Text.literal("\nConfig File Readable: ").formatted(Formatting.WHITE))
                .append(Text.literal(String.valueOf(configFile.canRead())).formatted(configFile.canRead() ? Formatting.GREEN : Formatting.RED))
                .append(Text.literal("\nConfig File Writable: ").formatted(Formatting.WHITE))
                .append(Text.literal(String.valueOf(configFile.canWrite())).formatted(configFile.canWrite() ? Formatting.GREEN : Formatting.RED));
        }
        
        source.sendFeedback(() -> header
            .copy()
            .append(paths)
            .append(existence)
            .append(rwPerms), false);
        
        return 1;
    }
} 