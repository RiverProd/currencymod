package net.currencymod.ui;

import net.currencymod.plots.ChunkKey;
import net.currencymod.plots.PlotManager;
import net.currencymod.plots.PlotType;
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
import net.minecraft.util.Formatting;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Admin chest UI — toggle which PlotTypes are enabled for a registered chunk.
 *
 * Layout (27 slots, 3 rows of 9):
 *   Row 0 (0-8):   all filler
 *   Row 1 (9-17):  slot 9 = filler, slots 10-16 = type toggles (max 7), slot 17 = filler
 *   Row 2 (18-26): all filler except slot 22 = Save button
 *
 * Clicking a type slot toggles its state locally.
 * Clicking Save persists to PlotManager and closes the screen.
 */
public class PlotTypeSelectScreenHandler extends GenericContainerScreenHandler {

    private static final int SLOT_SAVE = 22;
    // Type slots: 10, 11, 12, 13, 14, 15, 16  (7 max)
    private static final int FIRST_TYPE_SLOT = 10;
    private static final int MAX_TYPES = 7;

    private final SimpleInventory gui;
    private final ChunkKey chunkKey;
    private final Set<PlotType> localEnabledTypes;
    private final PlotType[] orderedTypes;
    private final ServerPlayerEntity admin;

    private PlotTypeSelectScreenHandler(int syncId, PlayerInventory playerInv, SimpleInventory gui,
                                         ChunkKey chunkKey, Set<PlotType> currentEnabledTypes,
                                         ServerPlayerEntity admin) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInv, gui, 3);
        this.gui = gui;
        this.chunkKey = chunkKey;
        this.localEnabledTypes = EnumSet.copyOf(
                currentEnabledTypes.isEmpty() ? EnumSet.noneOf(PlotType.class) : currentEnabledTypes);
        this.orderedTypes = PlotType.values();
        this.admin = admin;
    }

    /**
     * Opens the plot-type configuration screen for the admin.
     *
     * @param admin           The admin player
     * @param chunkKey        The chunk being configured
     * @param currentEnabled  The types currently enabled for that chunk
     */
    public static void open(ServerPlayerEntity admin, ChunkKey chunkKey, Set<PlotType> currentEnabled) {
        SimpleInventory gui = new SimpleInventory(27);
        ItemStack filler = ConfirmScreenHandler.makeFiller();
        for (int i = 0; i < 27; i++) gui.setStack(i, filler.copy());

        // Populate type slots
        PlotType[] types = PlotType.values();
        for (int i = 0; i < Math.min(types.length, MAX_TYPES); i++) {
            gui.setStack(FIRST_TYPE_SLOT + i, makeTypeItem(types[i], currentEnabled.contains(types[i])));
        }

        // Save button
        gui.setStack(SLOT_SAVE, ConfirmScreenHandler.makeItem(
                Items.LIME_CONCRETE,
                "§a§lSave",
                List.of("§7Click to save changes.")));

        admin.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inv, p) -> new PlotTypeSelectScreenHandler(
                        syncId, inv, gui, chunkKey, currentEnabled, admin),
                Text.literal("Configure Plot Types")));
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return;

        if (slotIndex == SLOT_SAVE) {
            sp.closeHandledScreen();
            PlotManager.getInstance().updateEnabledTypes(chunkKey, localEnabledTypes);
            sp.sendMessage(Text.literal("Plot types saved for this chunk.").formatted(Formatting.GREEN));
            return;
        }

        // Check if a type slot was clicked
        if (slotIndex >= FIRST_TYPE_SLOT && slotIndex < FIRST_TYPE_SLOT + MAX_TYPES) {
            int typeIndex = slotIndex - FIRST_TYPE_SLOT;
            if (typeIndex < orderedTypes.length) {
                PlotType type = orderedTypes[typeIndex];
                // Toggle
                if (localEnabledTypes.contains(type)) {
                    localEnabledTypes.remove(type);
                } else {
                    localEnabledTypes.add(type);
                }
                // Refresh the slot item immediately
                gui.setStack(slotIndex, makeTypeItem(type, localEnabledTypes.contains(type)));
            }
        }
        // All other slots: no-op (block interaction)
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }

    // -------------------------------------------------------------------------
    // Item builders
    // -------------------------------------------------------------------------

    private static ItemStack makeTypeItem(PlotType type, boolean enabled) {
        if (enabled) {
            return ConfirmScreenHandler.makeItem(
                    Items.LIME_DYE,
                    "§a§l" + type.getDisplayName(),
                    List.of(
                            "§7Price: §6$" + type.getPurchasePrice(),
                            "§7Daily Tax: §6$" + type.getDailyTax() + "/day",
                            "§a● Enabled",
                            "§7Click to disable."
                    ));
        } else {
            return ConfirmScreenHandler.makeItem(
                    Items.GRAY_DYE,
                    "§7" + type.getDisplayName(),
                    List.of(
                            "§7Price: §6$" + type.getPurchasePrice(),
                            "§7Daily Tax: §6$" + type.getDailyTax() + "/day",
                            "§8○ Disabled",
                            "§7Click to enable."
                    ));
        }
    }
}
