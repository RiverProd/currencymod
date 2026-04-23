package net.currencymod.shop;

import net.minecraft.util.Formatting;

/**
 * Interface for accessing methods added to SignBlockEntity via mixin
 */
public interface SignAccessor {
    /**
     * Method to colorize the first line of a shop sign
     * @param color The color to apply to the first line
     */
    void colorizeShopSign(Formatting color);
} 