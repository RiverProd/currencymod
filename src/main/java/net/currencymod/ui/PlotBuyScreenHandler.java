package net.currencymod.ui;

import net.currencymod.CurrencyMod;
import net.currencymod.plots.ChunkKey;
import net.currencymod.plots.PlotManager;
import net.currencymod.plots.PlotType;
import net.currencymod.plots.WorldPlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Player chest UI — select which PlotType to purchase one or more chunks as.
 *
 * Works for both single-plot and multi-plot (bulk) purchases.
 * The type list is the intersection of enabled types across all selected plots.
 * Each type item shows the total count, total discounted cost, and total daily tax.
 *
 * Layout (27 slots, 3 rows of 9):
 *   Row 0 (0-8):   all filler
 *   Row 1 (9-17):  slot 9 = filler, slots 10-16 = type options (max 7), slot 17 = filler
 *   Row 2 (18-26): all filler except slot 22 = Back button
 */
public class PlotBuyScreenHandler extends GenericContainerScreenHandler {

    private static final int SLOT_BACK       = 22;
    private static final int FIRST_TYPE_SLOT = 10;
    private static final int MAX_TYPES       = 7;

    private final Set<ChunkKey> plotKeys;
    private final PlotType[]    orderedTypes;
    private final Set<PlotType> commonTypes;
    private final double        playerBalance;
    private final int           plotCount;

    private PlotBuyScreenHandler(int syncId, PlayerInventory playerInv, SimpleInventory gui,
                                   Set<ChunkKey> plotKeys, PlotType[] orderedTypes,
                                   Set<PlotType> commonTypes, double playerBalance) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInv, gui, 3);
        this.plotKeys      = plotKeys;
        this.orderedTypes  = orderedTypes;
        this.commonTypes   = commonTypes;
        this.playerBalance = playerBalance;
        this.plotCount     = plotKeys.size();
    }

    /**
     * Opens the buy-type selection screen.
     *
     * @param player   The buying player
     * @param plotKeys All chunk keys to purchase (1 = single, N = bulk with discount)
     */
    public static void open(ServerPlayerEntity player, Set<ChunkKey> plotKeys) {
        PlotManager pm = PlotManager.getInstance();
        double balance  = CurrencyMod.getEconomyManager().getBalance(player.getUuid());
        PlotType[] types = PlotType.values();

        // Compute the intersection of enabled types across all selected plots
        Set<PlotType> common = EnumSet.allOf(PlotType.class);
        for (ChunkKey key : plotKeys) {
            WorldPlot wp = pm.getWorldPlot(key);
            if (wp == null || !wp.isAvailable()) {
                common.clear();
                break;
            }
            common.retainAll(wp.getEnabledTypes());
        }

        int count = plotKeys.size();
        SimpleInventory gui = new SimpleInventory(27);
        ItemStack filler = ConfirmScreenHandler.makeFiller();
        for (int i = 0; i < 27; i++) gui.setStack(i, filler.copy());

        for (int i = 0; i < Math.min(types.length, MAX_TYPES); i++) {
            gui.setStack(FIRST_TYPE_SLOT + i,
                    makeTypeItem(types[i], common.contains(types[i]), balance, count, pm));
        }

        gui.setStack(SLOT_BACK, ConfirmScreenHandler.makeItem(
                Items.RED_CONCRETE,
                "§c§lBack",
                List.of("§7Return to your plot info.")));

        String title = count == 1 ? "Buy Plot — Select Type"
                                  : "Buy " + count + " Plots — Select Type";

        // Capture a stable copy of plotKeys for the handler lambda
        Set<ChunkKey> keysCopy = new LinkedHashSet<>(plotKeys);

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inv, p) -> new PlotBuyScreenHandler(
                        syncId, inv, gui, keysCopy, types, common, balance),
                Text.literal(title)));
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return;

        if (slotIndex == SLOT_BACK) {
            sp.closeHandledScreen();
            PlotInfoScreenHandler.open(sp);
            return;
        }

        if (slotIndex >= FIRST_TYPE_SLOT && slotIndex < FIRST_TYPE_SLOT + MAX_TYPES) {
            int typeIndex = slotIndex - FIRST_TYPE_SLOT;
            if (typeIndex >= orderedTypes.length) return;

            PlotType type = orderedTypes[typeIndex];
            if (!commonTypes.contains(type)) return;

            int totalCost = PlotManager.getInstance()
                    .calculateBulkPurchaseCost(type.getPurchasePrice(), plotCount);
            if (playerBalance < totalCost) return;

            int totalTax   = plotCount * type.getDailyTax();
            int refundEach = (int)(type.getPurchasePrice() * 0.2);

            List<String> confirmLore = new ArrayList<>();
            confirmLore.add("§7Buying: §a" + plotCount + "x " + type.getDisplayName() + " plot"
                    + (plotCount > 1 ? "s" : ""));
            if (plotCount > 1) {
                confirmLore.add("§7Total cost: §6$" + totalCost + " §8(bulk discount applied)");
            } else {
                confirmLore.add("§7Cost: §6$" + totalCost);
            }
            confirmLore.add("§7Daily tax: §6$" + totalTax + "/day");
            confirmLore.add("§8Sell refund per plot: §7$" + refundEach + " (20%)");

            // Capture for lambda
            Set<ChunkKey> keys = new LinkedHashSet<>(plotKeys);

            sp.closeHandledScreen();
            ConfirmScreenHandler.open(
                    sp,
                    plotCount == 1 ? "Confirm Plot Purchase" : "Confirm " + plotCount + " Plot Purchases",
                    Items.LIME_DYE,
                    "§a§l" + type.getDisplayName() + (plotCount > 1 ? " ×" + plotCount : ""),
                    confirmLore,
                    () -> PlotManager.getInstance().buyMultiWorldPlots(sp, keys, type),
                    () -> PlotBuyScreenHandler.open(sp, keys));
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }

    // -------------------------------------------------------------------------
    // Item builder
    // -------------------------------------------------------------------------

    private static ItemStack makeTypeItem(PlotType type, boolean inCommon,
                                           double balance, int count, PlotManager pm) {
        int basePrice  = type.getPurchasePrice();
        int totalCost  = pm.calculateBulkPurchaseCost(basePrice, count);
        int totalTax   = count * type.getDailyTax();
        boolean canAfford = balance >= totalCost;

        if (!inCommon) {
            return ConfirmScreenHandler.makeItem(
                    Items.GRAY_DYE,
                    "§8" + type.getDisplayName(),
                    List.of(
                            "§8Not shared by all selected plots.",
                            "§7Base price: §8$" + basePrice + " each",
                            "§7Daily Tax: §8$" + type.getDailyTax() + "/day"
                    ));
        }

        if (!canAfford) {
            return ConfirmScreenHandler.makeItem(
                    Items.RED_DYE,
                    "§c" + type.getDisplayName(),
                    buildAffordLore(count, basePrice, totalCost, totalTax, (int) balance, pm));
        }

        return ConfirmScreenHandler.makeItem(
                Items.LIME_DYE,
                "§a§l" + type.getDisplayName(),
                buildBuyLore(count, basePrice, totalCost, totalTax, pm));
    }

    private static List<String> buildBuyLore(int count, int basePrice,
                                              int totalCost, int totalTax, PlotManager pm) {
        List<String> lore = new ArrayList<>();
        if (count == 1) {
            lore.add("§7Price: §6$" + basePrice);
        } else {
            lore.add("§7Plots: §f" + count + "x");
            lore.add("§7Base price: §7$" + basePrice + " each");
            lore.add("§7Total (discounted): §6$" + totalCost);
        }
        lore.add("§7Daily tax: §6$" + totalTax + "/day");
        if (count == 1) {
            lore.add("§7Sell refund: §7$" + (int)(basePrice * 0.2) + " (20%)");
        }
        lore.add("");
        lore.add("§eClick to purchase" + (count > 1 ? " all " + count + " plots" : "") + ".");
        return lore;
    }

    private static List<String> buildAffordLore(int count, int basePrice,
                                                  int totalCost, int totalTax,
                                                  int balance, PlotManager pm) {
        List<String> lore = new ArrayList<>();
        lore.add("§c✗ Insufficient funds");
        if (count > 1) {
            lore.add("§7Plots: §f" + count + "x");
            lore.add("§7Total needed: §6$" + totalCost);
        } else {
            lore.add("§7Price: §6$" + basePrice);
        }
        lore.add("§7Your balance: §c$" + balance);
        lore.add("§7Daily tax would be: §6$" + totalTax + "/day");
        return lore;
    }
}
