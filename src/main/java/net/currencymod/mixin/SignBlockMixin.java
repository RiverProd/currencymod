package net.currencymod.mixin;

import net.minecraft.block.AbstractBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Note: Shop sign protection is now handled by the ShopSignHandler and ShopInteractionHandler classes.
 * This mixin has been simplified to avoid Bootstrap errors due to method signature changes in Minecraft 1.21.1.
 * 
 * The functionality previously provided by this mixin:
 * 1. Removing shops from the manager when signs are broken
 * 2. Preventing non-owners from breaking shop signs
 * 
 * Is now handled in the mod's other components.
 */
@Mixin(AbstractBlock.class)
public class SignBlockMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/SignBlock");
    
    // Functionality moved to ShopInteractionHandler and ShopSignHandler
}