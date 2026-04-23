package net.currencymod.mixin;

import net.currencymod.shop.SignAccessor;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin for SignBlockEntity to add functionality for colorizing shop signs
 * In Minecraft 1.21, the API has changed and we don't have easy access to modify sign text colors
 * This mixin just provides a method for compatibility, but doesn't actually modify the sign appearance
 */
@Mixin(SignBlockEntity.class)
public abstract class SignBlockEntityMixin implements SignAccessor {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/SignBlockEntityMixin");
    
    /**
     * Method to "colorize" a shop sign - in Minecraft 1.21 this just logs the intent
     * In future versions, we could add more direct modification when the API stabilizes
     * 
     * @param color The color that would be applied (not actually applied due to API limitations)
     */
    @Override
    public void colorizeShopSign(Formatting color) {
        try {
            SignBlockEntity sign = (SignBlockEntity)(Object)this;
            
            // Just log the intent since we can't easily modify sign text colors in 1.21
            LOGGER.info("Shop sign at {} would be colored as {} (API limitations prevent actual coloring)", 
                sign.getPos(), color == Formatting.GREEN ? "Buy (Green)" : "Sell (Blue)");
            
        } catch (Exception e) {
            LOGGER.error("Error in shop sign colorization: {}", e.getMessage());
        }
    }
} 