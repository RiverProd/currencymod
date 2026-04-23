package net.currencymod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.currencymod.plots.ChunkKey;
import net.currencymod.plots.PlotManager;
import net.currencymod.plots.PlotType;
import net.currencymod.plots.WorldPlot;
import net.currencymod.ui.ConfirmScreenHandler;
import net.currencymod.ui.PlotInfoScreenHandler;
import net.currencymod.ui.PlotTypeSelectScreenHandler;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /plots command — reworked for GUI-based plot interaction.
 *
 * Player commands (no permission required):
 *   /plots          → opens PlotInfoScreenHandler (dashboard)
 *
 * Admin commands (permission level 2):
 *   /plots create   → registers current chunk as buyable (all types enabled), opens type-select UI
 *   /plots edit     → opens type-select UI for current chunk
 *   /plots remove   → unregisters current chunk (blocked if owned)
 *
 * Op escape hatches (permission level 2, hidden from tab-complete for non-ops):
 *   /plots buy <type> [quantity]
 *   /plots sell <type>
 *   /plots confirm
 *   /plots cancel
 */
public class PlotsCommand {

    private static final SuggestionProvider<ServerCommandSource> PLOT_TYPE_SUGGESTIONS = (ctx, builder) ->
            CommandSource.suggestMatching(
                    Arrays.stream(PlotType.values())
                            .map(PlotType::getDisplayName)
                            .collect(Collectors.toList()),
                    builder);

    private static final SuggestionProvider<ServerCommandSource> ONLINE_PLAYER_SUGGESTIONS = (ctx, builder) ->
            CommandSource.suggestMatching(
                    ctx.getSource().getServer().getPlayerManager().getPlayerList()
                            .stream()
                            .map(p -> p.getName().getString())
                            .collect(Collectors.toList()),
                    builder);

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                 CommandRegistryAccess registryAccess,
                                 CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            literal("plots")
                // ---- Base command: player dashboard ----
                .executes(PlotsCommand::openDashboard)

                // ---- Admin: create ----
                .then(literal("create")
                    .requires(src -> src.hasPermissionLevel(2))
                    .executes(PlotsCommand::createPlot))

                // ---- Admin: edit ----
                .then(literal("edit")
                    .requires(src -> src.hasPermissionLevel(2))
                    .executes(PlotsCommand::editPlot))

                // ---- Admin: remove ----
                .then(literal("remove")
                    .requires(src -> src.hasPermissionLevel(2))
                    .executes(PlotsCommand::removePlot))

                // ---- Admin: grant ----
                .then(literal("grant")
                    .requires(src -> src.hasPermissionLevel(2))
                    .then(argument("player", StringArgumentType.word())
                        .suggests(ONLINE_PLAYER_SUGGESTIONS)
                        .then(argument("type", StringArgumentType.word())
                            .suggests(PLOT_TYPE_SUGGESTIONS)
                            .executes(ctx -> grantPlot(ctx,
                                    StringArgumentType.getString(ctx, "player"),
                                    StringArgumentType.getString(ctx, "type"))))))

                // ---- Admin: delete ----
                .then(literal("delete")
                    .requires(src -> src.hasPermissionLevel(2))
                    .executes(PlotsCommand::deletePlot))

                // ---- Op escape hatch: buy ----
                .then(literal("buy")
                    .requires(src -> src.hasPermissionLevel(2))
                    .executes(PlotsCommand::showBuyHelp)
                    .then(argument("type", StringArgumentType.word())
                        .suggests(PLOT_TYPE_SUGGESTIONS)
                        .executes(ctx -> legacyBuy(ctx, StringArgumentType.getString(ctx, "type"), 1))
                        .then(argument("quantity", IntegerArgumentType.integer(1))
                            .executes(ctx -> legacyBuy(ctx,
                                    StringArgumentType.getString(ctx, "type"),
                                    IntegerArgumentType.getInteger(ctx, "quantity"))))))

                // ---- Op escape hatch: sell ----
                .then(literal("sell")
                    .requires(src -> src.hasPermissionLevel(2))
                    .executes(PlotsCommand::showSellHelp)
                    .then(argument("type", StringArgumentType.word())
                        .suggests(PLOT_TYPE_SUGGESTIONS)
                        .executes(ctx -> legacySell(ctx, StringArgumentType.getString(ctx, "type")))))

                // ---- Op escape hatch: confirm / cancel ----
                .then(literal("confirm")
                    .requires(src -> src.hasPermissionLevel(2))
                    .executes(PlotsCommand::confirmPurchase))

                .then(literal("cancel")
                    .requires(src -> src.hasPermissionLevel(2))
                    .executes(PlotsCommand::cancelPurchase))
        );
    }

    // =========================================================================
    // Base — player dashboard
    // =========================================================================

    private static int openDashboard(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }
        PlotInfoScreenHandler.open(player);
        return 1;
    }

    // =========================================================================
    // Admin — create
    // =========================================================================

    private static int createPlot(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }

        ChunkKey key = ChunkKey.fromPlayer(player);
        PlotManager pm = PlotManager.getInstance();

        if (pm.getWorldPlot(key) != null) {
            source.sendError(Text.literal("This chunk is already registered. Use ")
                    .append(Text.literal("/plots edit").formatted(Formatting.YELLOW))
                    .append(Text.literal(" to modify it.")));
            return 0;
        }

        // Register with all configured types enabled
        Set<PlotType> allTypes = EnumSet.allOf(PlotType.class);
        pm.registerPlot(key, allTypes);

        source.sendFeedback(() -> Text.literal("Chunk registered as a buyable plot. Opening type configuration...")
                .formatted(Formatting.GREEN), false);

        // Open type-select UI so admin can fine-tune
        PlotTypeSelectScreenHandler.open(player, key, allTypes);
        return 1;
    }

    // =========================================================================
    // Admin — edit
    // =========================================================================

    private static int editPlot(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }

        ChunkKey key = ChunkKey.fromPlayer(player);
        WorldPlot worldPlot = PlotManager.getInstance().getWorldPlot(key);

        if (worldPlot == null) {
            source.sendError(Text.literal("No registered plot in this chunk. Use ")
                    .append(Text.literal("/plots create").formatted(Formatting.YELLOW))
                    .append(Text.literal(" first.")));
            return 0;
        }

        PlotTypeSelectScreenHandler.open(player, key, worldPlot.getEnabledTypes());
        return 1;
    }

    // =========================================================================
    // Admin — remove
    // =========================================================================

    private static int removePlot(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }

        ChunkKey key = ChunkKey.fromPlayer(player);
        PlotManager pm = PlotManager.getInstance();
        WorldPlot worldPlot = pm.getWorldPlot(key);

        if (worldPlot == null) {
            source.sendError(Text.literal("No registered plot in this chunk."));
            return 0;
        }

        if (worldPlot.isOwned()) {
            source.sendError(Text.literal("This plot is owned by a player. It cannot be removed while owned."));
            return 0;
        }

        pm.unregisterPlot(key);
        source.sendFeedback(() -> Text.literal("Plot removed from the registry.")
                .formatted(Formatting.YELLOW), false);
        return 1;
    }

    // =========================================================================
    // Admin — grant
    // =========================================================================

    private static int grantPlot(CommandContext<ServerCommandSource> ctx,
                                   String playerName, String typeName) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity admin)) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }

        // Resolve target (online players only)
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(playerName);
        if (target == null) {
            source.sendError(Text.literal("Player \"" + playerName + "\" is not online."));
            return 0;
        }

        // Resolve plot type
        PlotType plotType = PlotType.fromString(typeName);
        if (plotType == null) {
            source.sendError(Text.literal("Unknown plot type: " + typeName));
            return 0;
        }

        ChunkKey key = ChunkKey.fromPlayer(admin);
        PlotManager pm = PlotManager.getInstance();
        WorldPlot worldPlot = pm.getWorldPlot(key);

        if (worldPlot == null) {
            source.sendError(Text.literal("No registered plot in this chunk. Use ")
                    .append(Text.literal("/plots create").formatted(Formatting.YELLOW))
                    .append(Text.literal(" to register it first.")));
            return 0;
        }

        if (worldPlot.isOwned()) {
            source.sendError(Text.literal("This plot is already owned. Use ")
                    .append(Text.literal("/plots delete").formatted(Formatting.YELLOW))
                    .append(Text.literal(" to clear it first.")));
            return 0;
        }

        boolean success = pm.grantPlot(target, worldPlot, plotType);
        if (success) {
            source.sendFeedback(() -> Text.literal("Granted a ")
                    .append(Text.literal(plotType.getDisplayName()).formatted(Formatting.GREEN))
                    .append(Text.literal(" plot to "))
                    .append(Text.literal(target.getName().getString()).formatted(Formatting.AQUA))
                    .append(Text.literal(".")), false);
            target.sendMessage(Text.literal("An admin has granted you a ")
                    .append(Text.literal(plotType.getDisplayName()).formatted(Formatting.GREEN, Formatting.BOLD))
                    .append(Text.literal(" plot. Daily tax: "))
                    .append(Text.literal("$" + plotType.getDailyTax() + "/day").formatted(Formatting.YELLOW)));
        } else {
            source.sendError(Text.literal("Failed to grant plot — it may have become owned during the operation."));
        }
        return success ? 1 : 0;
    }

    // =========================================================================
    // Admin — delete
    // =========================================================================

    private static int deletePlot(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity admin)) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }

        ChunkKey key = ChunkKey.fromPlayer(admin);
        PlotManager pm = PlotManager.getInstance();
        WorldPlot worldPlot = pm.getWorldPlot(key);

        if (worldPlot == null) {
            source.sendError(Text.literal("No registered plot in this chunk."));
            return 0;
        }

        // Build confirmation info
        String ownerLine = worldPlot.isOwned()
                ? "§c⚠ Owned by a player — ownership record will be removed."
                : "§7Unowned.";

        List<String> lore = new java.util.ArrayList<>();
        lore.add("§7Chunk: §f" + key.chunkX + ", " + key.chunkZ);
        lore.add("§7Dimension: §f" + key.dimension);
        lore.add(ownerLine);
        lore.add("");
        lore.add("§cThis permanently removes all data");
        lore.add("§cfor this chunk.");

        ConfirmScreenHandler.open(
                admin,
                "Delete Plot — Are you sure?",
                Items.BARRIER,
                "§c§lDelete This Plot",
                lore,
                () -> {
                    pm.deletePlot(key);
                    admin.sendMessage(Text.literal("Plot at chunk ")
                            .append(Text.literal(key.chunkX + ", " + key.chunkZ).formatted(Formatting.YELLOW))
                            .append(Text.literal(" has been permanently deleted.").formatted(Formatting.RED)));
                },
                () -> admin.sendMessage(Text.literal("Plot deletion cancelled.").formatted(Formatting.GRAY)));
        return 1;
    }

    // =========================================================================
    // Op escape hatches — legacy command-based buy/sell
    // =========================================================================

    private static int legacyBuy(CommandContext<ServerCommandSource> ctx,
                                   String typeName, int quantity) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }
        PlotType plotType = PlotType.fromString(typeName);
        if (plotType == null) {
            source.sendError(Text.literal("Unknown plot type: " + typeName));
            return 0;
        }
        return PlotManager.getInstance().buyPlots(player, plotType, quantity) ? 1 : 0;
    }

    private static int legacySell(CommandContext<ServerCommandSource> ctx,
                                    String typeName) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }
        PlotType plotType = PlotType.fromString(typeName);
        if (plotType == null) {
            source.sendError(Text.literal("Unknown plot type: " + typeName));
            return 0;
        }
        return PlotManager.getInstance().sellPlot(player, plotType) ? 1 : 0;
    }

    private static int showBuyHelp(CommandContext<ServerCommandSource> ctx) {
        StringBuilder sb = new StringBuilder("Available plot types:\n");
        for (PlotType type : PlotType.values()) {
            sb.append("  • ").append(type.getDisplayName())
              .append(" — $").append(type.getPurchasePrice())
              .append(" (Tax: $").append(type.getDailyTax()).append("/day)\n");
        }
        ctx.getSource().sendFeedback(() -> Text.literal(sb.toString().trim()).formatted(Formatting.GOLD), false);
        return 1;
    }

    private static int showSellHelp(CommandContext<ServerCommandSource> ctx) {
        StringBuilder sb = new StringBuilder("Sell a plot (20% refund):\n");
        for (PlotType type : PlotType.values()) {
            int refund = (int)(type.getPurchasePrice() * 0.2);
            sb.append("  • ").append(type.getDisplayName())
              .append(" — Refund: $").append(refund).append("\n");
        }
        ctx.getSource().sendFeedback(() -> Text.literal(sb.toString().trim()).formatted(Formatting.GOLD), false);
        return 1;
    }

    private static int confirmPurchase(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }
        return PlotManager.getInstance().confirmPurchase(player) ? 1 : 0;
    }

    private static int cancelPurchase(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }
        return PlotManager.getInstance().cancelPurchase(player) ? 1 : 0;
    }
}
