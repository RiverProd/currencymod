package net.currencymod.shop;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.SignBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for shop sign break events
 * This replaces the functionality previously in the SignBlockMixin
 */
public class ShopSignBreakHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/ShopSignBreak");
    
    /**
     * Register the shop sign break handler
     */
    public static void register() {
        // Register for block break events to handle breaking shop signs
        PlayerBlockBreakEvents.BEFORE.register(ShopSignBreakHandler::onBlockBreak);
        LOGGER.info("Registered shop sign break handler");
    }
    
    /**
     * Handle block break events for shop signs
     */
    private static boolean onBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        // Only process on server side
        if (world.isClient() || !(world instanceof ServerWorld)) {
            return true; // Allow the break on client
        }
        
        // Check if the block is a sign
        if (!(state.getBlock() instanceof SignBlock) && !(state.getBlock() instanceof WallSignBlock)) {
            return true; // Not a sign, allow the break
        }
        
        // Get shop data from the shop manager
        ShopManager shopManager = ((ShopAccess) world).getShopManager();
        if (shopManager == null) {
            return true; // No shop manager, allow the break
        }
        
        String worldId = ShopManager.getWorldId(world);
        ShopData shopData = shopManager.getShop(worldId, pos);
        
        // If this is not a shop sign, allow default behavior
        if (shopData == null) {
            return true; // Not a shop, allow the break
        }
        
        LOGGER.info("Shop sign break at {} by {}", pos, player.getName().getString());
        
        // Always allow the break now that we're removing the protection feature
        
        // Remove the shop from the manager
        boolean removed = removeShop(shopManager, worldId, pos);
        if (removed) {
            // Also remove from shop detector tracking
            ShopDetector.untrackShopSign(worldId, pos);
            LOGGER.info("Removed shop at {} due to sign being broken", pos);
            
            // Notify the player that a shop was removed
            String shopType = shopData.isBuyShop() ? "Buy" : "Sell";
            if (shopData.isAdminShop()) {
                shopType = "Admin " + shopType;
            }
            
            player.sendMessage(
                net.minecraft.text.Text.literal("🛑 ")
                    .formatted(net.minecraft.util.Formatting.RED)
                    .append(net.minecraft.text.Text.literal("Shop Removed")
                        .formatted(net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.BOLD))
                    .append(net.minecraft.text.Text.literal(" - " + shopType + " shop has been deleted.")
                        .formatted(net.minecraft.util.Formatting.YELLOW)),
                false
            );
        } else {
            LOGGER.warn("Failed to remove shop at {} despite sign being broken", pos);
        }
        
        return true; // Always allow the break
    }
    
    /**
     * Helper method to remove a shop and verify the removal
     */
    private static boolean removeShop(ShopManager shopManager, String worldId, BlockPos pos) {
        // Remove the shop
        shopManager.removeShop(worldId, pos);
        
        // Verify the shop was actually removed
        return shopManager.getShop(worldId, pos) == null;
    }
} 