package net.currencymod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.currencymod.plots.PlotManager;
import net.currencymod.plots.PlotType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Command for buying plots
 */
public class BuyPlotCommand {
    
    /**
     * Register the buyplot command
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            literal("buyplot")
                .executes(BuyPlotCommand::showHelp)
                .then(argument("type", StringArgumentType.word())
                    .executes(context -> buyPlot(context, StringArgumentType.getString(context, "type")))
                )
        );
    }
    
    /**
     * Display help for the buyplot command
     */
    private static int showHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        Text header = Text.literal("🏞️ ").formatted(Formatting.GREEN)
            .append(Text.literal("Plot Purchase Guide").formatted(Formatting.GREEN, Formatting.BOLD));
            
        Text typesHeader = Text.literal("\nAvailable plot types:").formatted(Formatting.WHITE);
        
        StringBuilder typesInfo = new StringBuilder();
        for (PlotType type : PlotType.values()) {
            typesInfo.append("\n• ")
                .append(type.getDisplayName())
                .append(" - $")
                .append(type.getPurchasePrice())
                .append(" (Tax: $")
                .append(type.getDailyTax())
                .append("/day)");
        }
        
        Text usage = Text.literal("\n\nUsage:").formatted(Formatting.WHITE)
            .append(Text.literal("\n/buyplot <type>").formatted(Formatting.YELLOW))
            .append(Text.literal(" - Purchase a plot of the specified type"));
            
        source.sendFeedback(() -> header
            .copy()
            .append(typesHeader)
            .append(Text.literal(typesInfo.toString()).formatted(Formatting.GOLD))
            .append(usage), false);
            
        return 1;
    }
    
    /**
     * Buy a plot of the specified type
     */
    private static int buyPlot(CommandContext<ServerCommandSource> context, String typeName) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        
        // Check if the command was run by a player
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        // Parse the plot type
        PlotType plotType = PlotType.fromString(typeName);
        if (plotType == null) {
            source.sendError(Text.literal("Unknown plot type: ").append(Text.literal(typeName).formatted(Formatting.RED)));
            source.sendFeedback(() -> Text.literal("Available types: ")
                .append(Text.literal(String.join(", ", 
                    PlotType.PERSONAL.getDisplayName(), 
                    PlotType.FARM.getDisplayName(), 
                    PlotType.BUSINESS.getDisplayName(),
                    PlotType.INDUSTRIAL.getDisplayName()
                )).formatted(Formatting.YELLOW)), false);
            return 0;
        }
        
        // Try to buy the plot
        boolean success = PlotManager.getInstance().buyPlot(player, plotType);
        
        return success ? 1 : 0;
    }
} 