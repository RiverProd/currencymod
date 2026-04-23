package net.currencymod.ui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple screen handler for the marketplace that uses a chest-like UI.
 * Players can click on items to purchase them.
 */
public class MarketplaceScreenHandler extends ScreenHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/MarketplaceScreenHandler");
    private final Inventory inventory;
    
    /**
     * Create a new marketplace screen handler.
     *
     * @param syncId The synchronization ID
     * @param playerInventory The player's inventory
     * @param inventory The marketplace inventory
     */
    public MarketplaceScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        // Use the generic container screen handler type for a chest-like UI
        super(ScreenHandlerType.GENERIC_9X3, syncId);
        this.inventory = inventory;
        
        // Add the marketplace slots (3 rows of 9 slots)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new MarketplaceSlot(inventory, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }
        
        // Add the player inventory slots (3 rows of 9 slots)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        
        // Add the player hotbar slots (1 row of 9 slots)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }
    
    /**
     * Create a marketplace screen handler with an empty inventory.
     *
     * @param syncId The synchronization ID
     * @param playerInventory The player's inventory
     */
    public MarketplaceScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(27));
    }
    
    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }
    
    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        // Prevent shift-clicking in the marketplace
        return ItemStack.EMPTY;
    }
    
    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        // No need to drop items as they are virtual representations
    }
    
    /**
     * Override this method to handle button clicks (used to process purchases).
     * 
     * @param player The player
     * @param id The button ID (slot index)
     * @return True if the click was handled, false otherwise
     */
    public boolean onButtonClick(PlayerEntity player, int id) {
        // Default implementation does nothing
        return false;
    }
    
    @Override
    public void onSlotClick(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player) {
        // If the slot is in the marketplace area (first 27 slots)
        if (slotIndex >= 0 && slotIndex < 27) {
            // Use our custom button click handler to process the purchase
            // This will be overridden by the MarketplaceManager
            if (onButtonClick(player, slotIndex)) {
                return; // Skip normal slot click if button click was handled
            }
        }
        
        // Allow normal interaction with player inventory
        super.onSlotClick(slotIndex, button, actionType, player);
    }
    
    /**
     * A custom slot for marketplace items that disables interaction.
     * Items can only be "purchased" through the MarketplaceHandler click system.
     */
    public class MarketplaceSlot extends Slot {
        public MarketplaceSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }
        
        @Override
        public boolean canTakeItems(PlayerEntity player) {
            return false; // Prevent normal item taking
        }
        
        @Override
        public boolean canInsert(ItemStack stack) {
            return false; // Prevent inserting items
        }
    }
} 