package net.currencymod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.currencymod.plots.PlotManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Command for administrators to trigger plot tax collection
 */
public class TaxCommand {

    /**
     * Register the tax command
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            literal("tax")
                .requires(source -> source.hasPermissionLevel(2)) // Require permission level 2 (admin/op)
                .executes(TaxCommand::executeTax)
        );
    }
    
    /**
     * Execute the tax command - collect taxes from all online players
     */
    private static int executeTax(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // Trigger tax collection in the PlotManager
        int playersCount = PlotManager.getInstance().collectManualTax(source.getServer());
        
        // Send feedback to the admin based on how many players were taxed
        if (playersCount > 0) {
            source.sendFeedback(() -> Text.literal("💰 Tax collection complete! ")
                .append(Text.literal("Collected taxes from " + playersCount + " player(s).")
                .formatted(Formatting.GREEN)), true);
        } else {
            source.sendFeedback(() -> Text.literal("💰 No players with plots were found online to tax."), true);
        }
        
        return playersCount;
    }
} 