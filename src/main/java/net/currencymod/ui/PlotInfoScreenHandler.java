package net.currencymod.ui;

import net.currencymod.CurrencyMod;
import net.currencymod.plots.ChunkKey;
import net.currencymod.plots.PlotManager;
import net.currencymod.plots.PlotType;
import net.currencymod.plots.WorldPlot;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Player chest UI — plot ownership dashboard.
 *
 * Layout (27 slots, 3 rows of 9):
 *   Row 0: slot 4 = player plot summary
 *          slot 7 = Buy Multiple Plots toggle
 *          slot 8 = Plot Vision toggle
 *          rest   = filler
 *   Row 1: slots 9-12 = owned-type items (one per PlotType with count > 0); rest filler
 *   Row 2: slot 22 = current chunk info  ← CLICKABLE (buy if available, sell if owned)
 *          slot 25 = multi mode: Select/Deselect  |  single mode + own plot: Sell button
 *          rest    = filler
 *
 * Multi-buy mode behaviour:
 *   - Toggling ON also enables Plot Vision if it is currently off.
 *   - Toggling OFF erases all pending selections.
 *   - Select/Deselect (slot 25) adds/removes the current chunk from the selection (max 16).
 *   - Clicking the Available Plot item (slot 22) opens PlotBuyScreenHandler for all
 *     selected plots when ≥1 are selected; otherwise sends a hint message.
 *   - In single mode, clicking slot 22 while standing in an available plot opens
 *     PlotBuyScreenHandler for just that one plot.
 */
public class PlotInfoScreenHandler extends GenericContainerScreenHandler {

    private static final int SLOT_SUMMARY    = 4;
    private static final int SLOT_MULTIBUY   = 7;
    private static final int SLOT_VISION     = 8;
    private static final int FIRST_TYPE_SLOT = 9;   // 9, 10, 11, 12
    private static final int SLOT_CHUNK_INFO = 22;
    private static final int SLOT_ACTION     = 25;

    private final SimpleInventory gui;
    private final WorldPlot       worldPlot;   // chunk player was in when screen opened; may be null
    private final ChunkKey        currentKey;  // pre-derived for convenience
    private final UUID            playerUuid;

    private PlotInfoScreenHandler(int syncId, PlayerInventory playerInv, SimpleInventory gui,
                                    WorldPlot worldPlot, ChunkKey currentKey, UUID playerUuid) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInv, gui, 3);
        this.gui        = gui;
        this.worldPlot  = worldPlot;
        this.currentKey = currentKey;
        this.playerUuid = playerUuid;
    }

    /** Opens the dashboard for the given player, deriving chunk context from their position. */
    public static void open(ServerPlayerEntity player) {
        UUID       playerUuid = player.getUuid();
        PlotManager pm        = PlotManager.getInstance();
        ChunkKey   currentKey = ChunkKey.fromPlayer(player);
        WorldPlot  worldPlot  = pm.getWorldPlot(currentKey);
        double     balance    = CurrencyMod.getEconomyManager().getBalance(playerUuid);

        boolean multiMode     = pm.isMultiBuyMode(playerUuid);
        boolean visionOn      = pm.hasPlotVision(playerUuid);
        int     selCount      = pm.getSelectionCount(playerUuid);
        boolean chunkSelected = multiMode && pm.getSelectedPlots(playerUuid).contains(currentKey);

        Map<PlotType, Integer> plotCounts = pm.getPlayerPlotCounts(playerUuid);
        int totalPlots = plotCounts.values().stream().mapToInt(Integer::intValue).sum();
        int dailyTax   = pm.getPlayerDailyTax(playerUuid);

        SimpleInventory gui = new SimpleInventory(27);
        ItemStack filler = ConfirmScreenHandler.makeFiller();
        for (int i = 0; i < 27; i++) gui.setStack(i, filler.copy());

        // Slot 4 — player summary
        gui.setStack(SLOT_SUMMARY, makeSummaryItem(plotCounts, totalPlots, dailyTax));

        // Slot 7 — multi-buy toggle
        gui.setStack(SLOT_MULTIBUY, makeMultiBuyItem(multiMode, selCount));

        // Slot 8 — plot vision toggle
        gui.setStack(SLOT_VISION, makeVisionItem(visionOn));

        // Slots 9-12 — owned type items
        int typeSlot = FIRST_TYPE_SLOT;
        for (PlotType type : PlotType.values()) {
            if (typeSlot > 12) break;
            int count = plotCounts.getOrDefault(type, 0);
            if (count > 0) { gui.setStack(typeSlot++, makeOwnedTypeItem(type, count)); }
        }

        // Slot 22 — current chunk info (clickable)
        gui.setStack(SLOT_CHUNK_INFO,
                makeChunkInfoItem(worldPlot, playerUuid, player, multiMode, chunkSelected, selCount));

        // Slot 25 — context action
        gui.setStack(SLOT_ACTION,
                makeActionItem(worldPlot, playerUuid, currentKey, multiMode, chunkSelected, selCount));

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inv, p) -> new PlotInfoScreenHandler(
                        syncId, inv, gui, worldPlot, currentKey, playerUuid),
                Text.literal("Your Plots")));
    }

    // =========================================================================
    // Slot click handling
    // =========================================================================

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return;

        switch (slotIndex) {
            case SLOT_MULTIBUY   -> handleMultiBuyToggle(sp);
            case SLOT_VISION     -> handleVisionToggle(sp);
            case SLOT_CHUNK_INFO -> handleChunkInfoClick(sp);
            case SLOT_ACTION     -> handleActionClick(sp);
        }
        // All other slots: no-op
    }

    private void handleMultiBuyToggle(ServerPlayerEntity sp) {
        PlotManager pm = PlotManager.getInstance();
        boolean nowOn = pm.toggleMultiBuy(sp.getUuid());

        // Auto-enable Plot Vision when entering multi-buy mode
        if (nowOn && !pm.hasPlotVision(sp.getUuid())) {
            pm.togglePlotVision(sp.getUuid());
            gui.setStack(SLOT_VISION, makeVisionItem(true));
            sp.sendMessage(Text.literal("Plot Vision auto-enabled for multi-buy.")
                    .formatted(Formatting.GRAY));
        }

        int selCount = pm.getSelectionCount(sp.getUuid());
        gui.setStack(SLOT_MULTIBUY, makeMultiBuyItem(nowOn, selCount));
        // Selection has been cleared or started — refresh dependent slots
        refreshSelectionSlots(sp, nowOn, selCount);

        sp.sendMessage(nowOn
                ? Text.literal("Multi-buy mode enabled. Select plots and click the available plot info to purchase.")
                        .formatted(Formatting.AQUA)
                : Text.literal("Multi-buy mode disabled. Selections cleared.")
                        .formatted(Formatting.GRAY));
    }

    private void handleVisionToggle(ServerPlayerEntity sp) {
        boolean nowEnabled = PlotManager.getInstance().togglePlotVision(sp.getUuid());
        gui.setStack(SLOT_VISION, makeVisionItem(nowEnabled));
        sp.sendMessage(nowEnabled
                ? Text.literal("Plot Vision ").append(
                        Text.literal("enabled").formatted(Formatting.GREEN, Formatting.BOLD))
                        .append(Text.literal(". Available plots nearby will glow.").formatted(Formatting.GRAY))
                : Text.literal("Plot Vision ").append(
                        Text.literal("disabled").formatted(Formatting.RED, Formatting.BOLD))
                        .append(Text.literal(".").formatted(Formatting.GRAY)));
    }

    private void handleChunkInfoClick(ServerPlayerEntity sp) {
        PlotManager pm     = PlotManager.getInstance();
        UUID        uuid   = sp.getUuid();
        boolean     multi  = pm.isMultiBuyMode(uuid);

        if (worldPlot != null && !worldPlot.isOwned() && worldPlot.isAvailable()) {
            if (multi) {
                Set<ChunkKey> sel = pm.getSelectedPlots(uuid);
                if (sel.isEmpty()) {
                    sp.sendMessage(Text.literal(
                            "No plots selected. Use the Select button to add plots first.")
                            .formatted(Formatting.RED));
                } else {
                    sp.closeHandledScreen();
                    PlotBuyScreenHandler.open(sp, new LinkedHashSet<>(sel));
                }
            } else {
                // Single mode — buy just this plot
                sp.closeHandledScreen();
                Set<ChunkKey> single = new LinkedHashSet<>();
                single.add(currentKey);
                PlotBuyScreenHandler.open(sp, single);
            }
            return;
        }

        if (worldPlot != null && worldPlot.isOwned() && uuid.equals(worldPlot.getOwnerUuid())) {
            handleSell(sp);
        }
    }

    private void handleActionClick(ServerPlayerEntity sp) {
        PlotManager pm    = PlotManager.getInstance();
        UUID        uuid  = sp.getUuid();
        boolean     multi = pm.isMultiBuyMode(uuid);

        if (multi) {
            // Select / Deselect the current chunk
            if (worldPlot == null || !worldPlot.isAvailable()) {
                sp.sendMessage(Text.literal("You are not standing in an available plot.")
                        .formatted(Formatting.RED));
                return;
            }
            int result = pm.togglePlotSelection(uuid, currentKey);
            if (result == -1) {
                sp.sendMessage(Text.literal("You can only select up to 16 plots at once.")
                        .formatted(Formatting.RED));
                return;
            }
            boolean selected  = pm.getSelectedPlots(uuid).contains(currentKey);
            int     selCount  = pm.getSelectionCount(uuid);

            // Refresh chunk info (shows/hides Selected lore) and select button (updates count)
            refreshSelectionSlots(sp, true, selCount);

            sp.sendMessage(selected
                    ? Text.literal("Plot added to selection. §a" + selCount + "/16 §7selected.")
                            .formatted(Formatting.GREEN)
                    : Text.literal("Plot removed from selection. §e" + selCount + "/16 §7selected.")
                            .formatted(Formatting.YELLOW));
        } else {
            // Single mode — slot 25 is the Sell button when the player owns this plot
            handleSell(sp);
        }
    }

    private void handleSell(ServerPlayerEntity sp) {
        if (worldPlot == null || !worldPlot.isOwned() || !sp.getUuid().equals(worldPlot.getOwnerUuid()))
            return;

        PlotType type    = getOwnedPlotType(worldPlot);
        int      price   = type != null ? type.getPurchasePrice() : 0;
        int      refund  = (int)(price * 0.2);
        String   typeName = type != null ? type.getDisplayName() : "Unknown";

        sp.closeHandledScreen();
        ConfirmScreenHandler.open(
                sp,
                "Confirm Plot Sale",
                Items.BARRIER,
                "§c§lSell " + typeName + " Plot",
                List.of(
                        "§7Sell your §c" + typeName + "§7 plot?",
                        "§7Refund: §6$" + refund + " §7(20% of §6$" + price + "§7)",
                        "",
                        "§8This cannot be undone."
                ),
                () -> PlotManager.getInstance().sellWorldPlot(sp, worldPlot),
                () -> PlotInfoScreenHandler.open(sp));
    }

    /**
     * Refreshes the chunk-info item (slot 22) and action item (slot 25) after a selection change.
     */
    private void refreshSelectionSlots(ServerPlayerEntity sp, boolean multiMode, int selCount) {
        PlotManager pm        = PlotManager.getInstance();
        boolean     selected  = multiMode && pm.getSelectedPlots(sp.getUuid()).contains(currentKey);
        double      balance   = CurrencyMod.getEconomyManager().getBalance(sp.getUuid());

        gui.setStack(SLOT_CHUNK_INFO,
                makeChunkInfoItem(worldPlot, sp.getUuid(), sp, multiMode, selected, selCount));
        gui.setStack(SLOT_ACTION,
                makeActionItem(worldPlot, sp.getUuid(), currentKey, multiMode, selected, selCount));
    }

    private PlotType getOwnedPlotType(WorldPlot wp) {
        if (wp.getPlotId() == null || wp.getOwnerUuid() == null) return null;
        return PlotManager.getInstance().getPlayerPlots(wp.getOwnerUuid()).stream()
                .filter(p -> p.getId().equals(wp.getPlotId()))
                .map(p -> p.getType())
                .findFirst().orElse(null);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }

    // =========================================================================
    // Item builders
    // =========================================================================

    private static ItemStack makeMultiBuyItem(boolean enabled, int selCount) {
        ItemStack stack = ConfirmScreenHandler.makeItem(
                Items.ENDER_PEARL,
                enabled ? "§b§lBuy Multiple Plots" : "§7Buy Multiple Plots",
                enabled
                        ? List.of(
                                "§bCurrently: §a§lON",
                                "§7Selected: §b" + selCount + "/16",
                                "§7Stand in available plots and use",
                                "§7the Select button to build your list.",
                                "§7Click the plot info below to purchase.",
                                "",
                                "§7Click to disable (clears selection).")
                        : List.of(
                                "§8Currently: OFF",
                                "§7Enable to select multiple plots",
                                "§7and buy them all with a bulk discount.",
                                "§7Click to enable.")
        );
        if (enabled) {
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    private static ItemStack makeVisionItem(boolean enabled) {
        ItemStack stack = ConfirmScreenHandler.makeItem(
                Items.ENDER_EYE,
                enabled ? "§d§lPlot Vision" : "§7Plot Vision",
                enabled
                        ? List.of("§dCurrently: §a§lON", "§7Highlights available plots nearby.", "§7Click to disable.")
                        : List.of("§8Currently: OFF", "§7Shows available plots near you", "§7with a particle effect.", "§7Click to enable.")
        );
        if (enabled) stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    private static ItemStack makeSummaryItem(Map<PlotType, Integer> plotCounts, int total, int tax) {
        List<String> lore = new ArrayList<>();
        if (total == 0) {
            lore.add("§7You don't own any plots yet.");
            lore.add("");
            lore.add("§7Stand in a registered chunk and");
            lore.add("§7click the plot info below to buy.");
        } else {
            lore.add("§7Total owned: §f" + total);
            for (PlotType t : PlotType.values()) {
                int c = plotCounts.getOrDefault(t, 0);
                if (c > 0) lore.add("§7• " + typeColour(t) + t.getDisplayName() + "§7: §f" + c + "x");
            }
            lore.add("");
            lore.add("§7Daily tax: §6$" + tax + "/day");
        }
        return ConfirmScreenHandler.makeItem(Items.PAPER, "§e§lYour Plot Summary", lore);
    }

    private static ItemStack makeOwnedTypeItem(PlotType type, int count) {
        return ConfirmScreenHandler.makeItem(
                typeIcon(type),
                typeColour(type) + "§l" + type.getDisplayName() + " Plot",
                List.of(
                        "§7Owned: §f" + count + "x",
                        "§7Daily Tax: §6$" + (count * type.getDailyTax()) + "/day",
                        "§7Price: §f$" + type.getPurchasePrice() + " each"
                ));
    }

    private static ItemStack makeChunkInfoItem(WorldPlot wp, UUID playerUuid,
                                                ServerPlayerEntity player,
                                                boolean multiMode, boolean chunkSelected,
                                                int selCount) {
        if (wp == null) {
            ChunkKey key = ChunkKey.fromPlayer(player);
            return ConfirmScreenHandler.makeItem(Items.COMPASS, "§7Current Chunk",
                    List.of("§8Chunk: §7" + key.chunkX + ", " + key.chunkZ,
                            "§8Not a registered plot."));
        }

        ChunkKey loc = wp.getLocation();

        if (!wp.isOwned()) {
            List<String> lore = new ArrayList<>();
            lore.add("§8Chunk: §7" + loc.chunkX + ", " + loc.chunkZ);
            lore.add("§aAvailable for purchase.");
            lore.add("§7Types:");
            for (PlotType t : wp.getEnabledTypes())
                lore.add("  §7• " + typeColour(t) + t.getDisplayName() + " §7($" + t.getPurchasePrice() + ")");

            if (multiMode) {
                lore.add("");
                if (chunkSelected) {
                    lore.add("§a✓ Selected §7(" + selCount + "/16)");
                } else {
                    lore.add("§8Not selected  §7(" + selCount + "/16)");
                }
                if (selCount > 0) {
                    lore.add("§eClick to open buy menu for " + selCount + " selected plot"
                            + (selCount != 1 ? "s" : "") + ".");
                } else {
                    lore.add("§7Click to buy just this plot.");
                }
            } else {
                lore.add("");
                lore.add("§eClick to purchase this plot.");
            }
            return ConfirmScreenHandler.makeItem(Items.MAP, "§a§lAvailable Plot", lore);
        }

        if (playerUuid.equals(wp.getOwnerUuid())) {
            PlotType type = PlotManager.getInstance().getPlayerPlots(playerUuid).stream()
                    .filter(p -> p.getId().equals(wp.getPlotId())).map(p -> p.getType())
                    .findFirst().orElse(null);
            int refund = type != null ? (int)(type.getPurchasePrice() * 0.2) : 0;
            return ConfirmScreenHandler.makeItem(Items.FILLED_MAP, "§e§lYour Plot",
                    List.of("§8Chunk: §7" + loc.chunkX + ", " + loc.chunkZ,
                            "§7Type: " + typeColour(type) + (type != null ? type.getDisplayName() : "?"),
                            "§7Sell refund: §6$" + refund + " §7(20%)",
                            "",
                            "§eClick to sell this plot."));
        }

        return ConfirmScreenHandler.makeItem(Items.BARRIER, "§c§lOwned Plot",
                List.of("§8Chunk: §7" + loc.chunkX + ", " + loc.chunkZ,
                        "§7Owner: §c" + resolvePlayerName(wp.getOwnerUuid(), player),
                        "§8Cannot purchase."));
    }

    private static ItemStack makeActionItem(WorldPlot wp, UUID playerUuid, ChunkKey currentKey,
                                             boolean multiMode, boolean chunkSelected, int selCount) {
        if (multiMode) {
            if (wp != null && wp.isAvailable()) {
                if (chunkSelected) {
                    return ConfirmScreenHandler.makeItem(Items.YELLOW_CONCRETE,
                            "§e§lDeselect This Plot",
                            List.of("§7Click to remove from selection.",
                                    "§7Selected: §e" + selCount + "/16"));
                } else {
                    return ConfirmScreenHandler.makeItem(
                            selCount >= 16 ? Items.RED_CONCRETE : Items.CYAN_CONCRETE,
                            selCount >= 16 ? "§c§lSelection Full" : "§b§lSelect This Plot",
                            selCount >= 16
                                    ? List.of("§cMax 16 plots per purchase.",
                                              "§7Deselect a plot to free a slot.")
                                    : List.of("§7Click to add to selection.",
                                              "§7Selected: §b" + selCount + "/16"));
                }
            }
            // Not standing in available plot
            return ConfirmScreenHandler.makeItem(Items.GRAY_CONCRETE,
                    "§7Select Plot",
                    List.of("§8Stand in an available plot to select it.",
                            "§7Selected: §7" + selCount + "/16"));
        }

        // Single mode — sell button when player owns this plot
        if (wp != null && wp.isOwned() && playerUuid.equals(wp.getOwnerUuid())) {
            PlotType type  = PlotManager.getInstance().getPlayerPlots(playerUuid).stream()
                    .filter(p -> p.getId().equals(wp.getPlotId())).map(p -> p.getType())
                    .findFirst().orElse(null);
            int refund = type != null ? (int)(type.getPurchasePrice() * 0.2) : 0;
            return ConfirmScreenHandler.makeItem(Items.YELLOW_CONCRETE, "§e§lSell This Plot",
                    List.of("§7You own this plot.",
                            "§7Refund: §6$" + refund + " §7(20%)"));
        }

        return ConfirmScreenHandler.makeFiller();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String typeColour(PlotType type) {
        if (type == null) return "§7";
        return switch (type) {
            case PERSONAL   -> "§b";
            case FARM       -> "§a";
            case BUSINESS   -> "§5";
            case INDUSTRIAL -> "§c";
            case COMMUNITY  -> "§e";
        };
    }

    private static Item typeIcon(PlotType type) {
        return switch (type) {
            case PERSONAL   -> Items.GRASS_BLOCK;
            case FARM       -> Items.HAY_BLOCK;
            case BUSINESS   -> Items.EMERALD_BLOCK;
            case INDUSTRIAL -> Items.IRON_BLOCK;
            case COMMUNITY  -> Items.LANTERN;
        };
    }

    private static String resolvePlayerName(UUID uuid, ServerPlayerEntity requester) {
        var online = requester.getServer().getPlayerManager().getPlayer(uuid);
        if (online != null) return online.getName().getString();
        return requester.getServer().getUserCache()
                .getByUuid(uuid)
                .map(p -> p.getName())
                .orElse(uuid.toString().substring(0, 8) + "...");
    }
}
