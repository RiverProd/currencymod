package net.currencymod.mixin;

import net.minecraft.block.ChestBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Note: Shop chest protection is now handled by the ShopProtectionHandler and ShopInteractionHandler classes.
 * This mixin has been simplified to avoid Bootstrap errors due to method signature changes in Minecraft 1.21.1.
 * 
 * The functionality previously provided by this mixin:
 * 1. Preventing non-owners from accessing shop chests
 * 2. Preventing non-owners from breaking shop chests
 * 
 * Is now handled in the mod's other components.
 */
@Mixin(ChestBlock.class)
public class ChestBlockMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/ChestBlock");
    
    // Functionality moved to ShopProtectionHandler and ShopInteractionHandler
} 