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
 * Command for selling plots
 */
public class SellPlotCommand {
    
    /**
     * Register the sellplot command
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            literal("sellplot")
                .executes(SellPlotCommand::showHelp)
                .then(argument("type", StringArgumentType.word())
                    .executes(context -> sellPlot(context, StringArgumentType.getString(context, "type")))
                )
        );
    }
    
    /**
     * Display help for the sellplot command
     */
    private static int showHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        Text header = Text.literal("🏞️ ").formatted(Formatting.RED)
            .append(Text.literal("Plot Selling Guide").formatted(Formatting.RED, Formatting.BOLD));
            
        Text typesHeader = Text.literal("\nSell your plots for a 20% refund:").formatted(Formatting.WHITE);
        
        StringBuilder typesInfo = new StringBuilder();
        for (PlotType type : PlotType.values()) {
            int refundAmount = (int)(type.getPurchasePrice() * 0.2);
            typesInfo.append("\n• ")
                .append(type.getDisplayName())
                .append(" - Refund: $")
                .append(refundAmount)
                .append(" (20% of $")
                .append(type.getPurchasePrice())
                .append(")");
        }
        
        Text usage = Text.literal("\n\nUsage:").formatted(Formatting.WHITE)
            .append(Text.literal("\n/sellplot <type>").formatted(Formatting.YELLOW))
            .append(Text.literal(" - Sell a plot of the specified type"));
            
        source.sendFeedback(() -> header
            .copy()
            .append(typesHeader)
            .append(Text.literal(typesInfo.toString()).formatted(Formatting.GOLD))
            .append(usage), false);
            
        return 1;
    }
    
    /**
     * Sell a plot of the specified type
     */
    private static int sellPlot(CommandContext<ServerCommandSource> context, String typeName) throws CommandSyntaxException {
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
        
        // Try to sell the plot
        boolean success = PlotManager.getInstance().sellPlot(player, plotType);
        
        return success ? 1 : 0;
    }
} 