package net.currencymod.ui;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A reusable 3-row (27-slot) confirmation GUI.
 * Shows a centred info item, confirm (lime concrete) and cancel (red concrete) button.
 */
public class ConfirmScreenHandler extends GenericContainerScreenHandler {

    private static final int SLOT_INFO    = 13;
    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_CANCEL  = 15;

    private final Runnable onConfirm;
    private final Runnable onCancel;

    private ConfirmScreenHandler(int syncId, PlayerInventory playerInv,
                                  SimpleInventory gui,
                                  Runnable onConfirm, Runnable onCancel) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInv, gui, 3);
        this.onConfirm = onConfirm;
        this.onCancel  = onCancel;
    }

    /**
     * Opens a confirmation screen for the given player.
     *
     * @param player     The player
     * @param title      Title shown on the chest
     * @param infoItem   The item displayed in the centre info slot
     * @param infoName   Display name for the info item (may use § colour codes)
     * @param infoLore   Lore lines (may use § colour codes)
     * @param onConfirm  Called when the player clicks Confirm
     * @param onCancel   Called when the player clicks Cancel / Back
     */
    public static void open(ServerPlayerEntity player,
                            String title,
                            Item infoItem,
                            String infoName,
                            List<String> infoLore,
                            Runnable onConfirm,
                            Runnable onCancel) {
        SimpleInventory gui = new SimpleInventory(27);
        ItemStack filler = makeFiller();
        for (int i = 0; i < 27; i++) gui.setStack(i, filler.copy());

        gui.setStack(SLOT_INFO,    makeItem(infoItem, infoName, infoLore));
        gui.setStack(SLOT_CONFIRM, makeItem(Items.LIME_CONCRETE,   "§a§lConfirm", List.of("§7Click to confirm.")));
        gui.setStack(SLOT_CANCEL,  makeItem(Items.RED_CONCRETE,    "§c§lCancel",  List.of("§7Click to go back.")));

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, inv, p) -> new ConfirmScreenHandler(syncId, inv, gui, onConfirm, onCancel),
            Text.literal(title)
        ));
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return;
        if (slotIndex == SLOT_CONFIRM) { sp.closeHandledScreen(); onConfirm.run(); return; }
        if (slotIndex == SLOT_CANCEL)  { sp.closeHandledScreen(); onCancel.run();  return; }
        // Block all other interactions
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }

    // -------------------------------------------------------------------------
    // Shared item-building helpers (package-visible so other handlers can reuse)
    // -------------------------------------------------------------------------

    static ItemStack makeItem(Item item, String name, List<String> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal(name).styled(s -> s.withItalic(false)));
        if (!lore.isEmpty()) {
            List<Text> loreTexts = lore.stream()
                .map(line -> (Text) Text.literal(line).styled(s -> s.withItalic(false)))
                .collect(Collectors.toList());
            stack.set(DataComponentTypes.LORE, new LoreComponent(loreTexts));
        }
        return stack;
    }

    static ItemStack makeFiller() {
        ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        pane.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" ").styled(s -> s.withItalic(false)));
        return pane;
    }
}
