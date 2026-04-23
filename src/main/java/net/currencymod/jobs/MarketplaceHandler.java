package net.currencymod.jobs;

import net.currencymod.CurrencyMod;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles marketplace interaction in a simplified way using block interaction.
 */
public class MarketplaceHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/MarketplaceHandler");
    
    // The position of the marketplace chest, if any
    private static BlockPos marketplaceChestPos = null;
    
    /**
     * Register the marketplace handler.
     */
    public static void register() {
        // Instead of packet handling or item NBT, we use block interaction
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient() || hand != Hand.MAIN_HAND) {
                return ActionResult.PASS;
            }
            
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);
            
            // Check if the block is the marketplace chest
            if (state.getBlock() instanceof ChestBlock && pos.equals(marketplaceChestPos)) {
                // Open the marketplace GUI instead of the chest
                MarketplaceManager.getInstance().openMarketplace((ServerPlayerEntity) player);
                return ActionResult.SUCCESS;
            }
            
            return ActionResult.PASS;
        });
        
        LOGGER.info("Registered marketplace handler using block interaction events");
    }
    
    /**
     * Set the marketplace chest position.
     * This is used to identify the chest that should open the marketplace.
     *
     * @param pos The position of the marketplace chest
     */
    public static void setMarketplaceChestPos(BlockPos pos) {
        marketplaceChestPos = pos;
        LOGGER.info("Set marketplace chest position to {}", pos);
    }
} 