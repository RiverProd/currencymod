package net.currencymod.plots;

import net.currencymod.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the different types of plots that can be purchased.
 */
public enum PlotType {
    PERSONAL("Personal"),
    FARM("Farm"),
    BUSINESS("Business"),
    INDUSTRIAL("Industrial"),
    COMMUNITY("Community");
    
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/PlotType");
    private final String defaultDisplayName;
    
    /**
     * Constructor for PlotType
     * 
     * @param defaultDisplayName The default display name if config is not available
     */
    PlotType(String defaultDisplayName) {
        this.defaultDisplayName = defaultDisplayName;
    }
    
    /**
     * Get the display name for this plot type from config, or fallback to default
     * 
     * @return The display name
     */
    public String getDisplayName() {
        ModConfig.PlotTypeConfig config = getConfig();
        return config != null ? config.getDisplayName() : defaultDisplayName;
    }
    
    /**
     * Get the purchase price for this plot type from config, or fallback to default
     * 
     * @return The purchase price
     */
    public int getPurchasePrice() {
        ModConfig.PlotTypeConfig config = getConfig();
        int defaultPrice = getDefaultPrice();
        return config != null ? config.getPurchasePrice() : defaultPrice;
    }
    
    /**
     * Get the daily tax amount for this plot type from config, or fallback to default
     * 
     * @return The daily tax
     */
    public int getDailyTax() {
        ModConfig.PlotTypeConfig config = getConfig();
        int defaultTax = getDefaultTax();
        return config != null ? config.getDailyTax() : defaultTax;
    }
    
    /**
     * Get the config for this plot type
     * 
     * @return The PlotTypeConfig or null if not found
     */
    private ModConfig.PlotTypeConfig getConfig() {
        return ModConfig.getInstance().getPlotConfig().getPlotTypeConfig(name());
    }
    
    /**
     * Get the default price for a plot type (used as fallback if config fails)
     */
    private int getDefaultPrice() {
        switch (this) {
            case PERSONAL: return 1000;
            case FARM: return 2000;
            case BUSINESS: return 3000;
            case INDUSTRIAL: return 3000;
            case COMMUNITY: return 500;
            default: return 1000;
        }
    }
    
    /**
     * Get the default tax for a plot type (used as fallback if config fails)
     */
    private int getDefaultTax() {
        switch (this) {
            case PERSONAL: return 1;
            case FARM: return 2;
            case BUSINESS: return 3;
            case INDUSTRIAL: return 3;
            case COMMUNITY: return -10;
            default: return 1;
        }
    }
    
    /**
     * Parse a string into a PlotType, case-insensitive
     * 
     * @param name The name of the plot type
     * @return The PlotType or null if not found
     */
    public static PlotType fromString(String name) {
        for (PlotType type : values()) {
            if (type.name().equalsIgnoreCase(name) || type.getDisplayName().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
} 