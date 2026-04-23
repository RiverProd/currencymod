package net.currencymod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.currencymod.config.GuiPreferenceManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class CurrencyGuiCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            literal("currencygui")
                .executes(ctx -> showStatus(ctx.getSource()))
                .then(literal("on").executes(ctx -> setGui(ctx.getSource(), true)))
                .then(literal("off").executes(ctx -> setGui(ctx.getSource(), false)))
        );
    }

    private static int showStatus(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be run by a player."));
            return 0;
        }
        boolean enabled = GuiPreferenceManager.getInstance().isGuiEnabled(player.getUuid());
        source.sendFeedback(() -> Text.literal("Jobs GUI is currently ")
            .formatted(Formatting.GRAY)
            .append(Text.literal(enabled ? "ON" : "OFF")
                .formatted(enabled ? Formatting.GREEN : Formatting.RED))
            .append(Text.literal(". Use ").formatted(Formatting.GRAY))
            .append(Text.literal("/currencygui on").formatted(Formatting.YELLOW))
            .append(Text.literal(" or ").formatted(Formatting.GRAY))
            .append(Text.literal("/currencygui off").formatted(Formatting.YELLOW))
            .append(Text.literal(" to change.").formatted(Formatting.GRAY)), false);
        return 1;
    }

    private static int setGui(ServerCommandSource source, boolean enable) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be run by a player."));
            return 0;
        }
        GuiPreferenceManager.getInstance().setGuiEnabled(player.getUuid(), enable);
        source.sendFeedback(() -> Text.literal("Jobs GUI ")
            .formatted(Formatting.GRAY)
            .append(Text.literal(enable ? "enabled" : "disabled")
                .formatted(enable ? Formatting.GREEN : Formatting.RED))
            .append(Text.literal(enable
                ? ". Use /jobs to open the interface."
                : ". Use /jobs list to use the text-based system.")
                .formatted(Formatting.GRAY)), false);
        return 1;
    }
}
