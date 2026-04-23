package net.currencymod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.currencymod.CurrencyMod;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles configuration settings for the mod that can be changed without code modification
 */
public class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/Config");
    private static final String CONFIG_DIR = "currency_mod/config";
    private static final String CONFIG_FILE = "config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static ModConfig instance;
    
    // Configuration categories
    private PlotConfig plotConfig;
    
    // Job multipliers (default to 1.0)
    @SerializedName("job-quantity-multiplier")
    private double jobQuantityMultiplier = 1.0;
    
    @SerializedName("job-payout-multiplier")
    private double jobPayoutMultiplier = 1.0;
    
    // Web sync configuration
    @SerializedName("web-sync-enabled")
    private boolean webSyncEnabled = false;
    
    @SerializedName("web-sync-api-url")
    private String webSyncApiUrl = "";
    
    @SerializedName("web-sync-api-key")
    private String webSyncApiKey = "";
    
    @SerializedName("web-sync-interval")
    private int webSyncInterval = 60; // Default: 60 seconds
    
    /**
     * Creates a new ModConfig with default values
     */
    private ModConfig() {
        // Initialize with default values
        this.plotConfig = new PlotConfig();
    }
    
    /**
     * Get the singleton instance of ModConfig
     * @return The ModConfig instance
     */
    public static ModConfig getInstance() {
        if (instance == null) {
            instance = new ModConfig();
        }
        return instance;
    }
    
    /**
     * Get the config directory path
     * @return The path to the config directory
     */
    public static String getConfigDir() {
        return CONFIG_DIR;
    }
    
    /**
     * Get the config file name
     * @return The name of the config file
     */
    public static String getConfigFile() {
        return CONFIG_FILE;
    }
    
    /**
     * Load config from file or create default config if file doesn't exist
     * @param server The server instance for file operations
     */
    public void load(MinecraftServer server) {
        LOGGER.info("Attempting to load configuration...");
        LOGGER.info("Server run directory: {}", server.getRunDirectory());
        
        // Create proper paths by resolving against server run directory
        Path runDir = server.getRunDirectory();
        Path configDirPath = runDir.resolve(CONFIG_DIR);
        Path configFilePath = configDirPath.resolve(CONFIG_FILE);
        
        File configDir = configDirPath.toFile();
        File configFile = configFilePath.toFile();
        
        LOGGER.info("Config directory path: {}", configDir.getAbsolutePath());
        LOGGER.info("Config file path: {}", configFile.getAbsolutePath());
        
        // Create directory if it doesn't exist
        if (!configDir.exists()) {
            LOGGER.info("Config directory does not exist, attempting to create it");
            boolean created = configDir.mkdirs();
            if (!created) {
                LOGGER.error("Failed to create config directory: {}", configDir.getAbsolutePath());
                return;
            } else {
                LOGGER.info("Successfully created config directory");
            }
        } else {
            LOGGER.info("Config directory already exists");
        }
        
        // If config file exists, load it
        if (configFile.exists()) {
            LOGGER.info("Config file exists, loading configuration");
            try (FileReader reader = new FileReader(configFile)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    // Update our instance with loaded values
                    if (loaded.plotConfig != null) {
                        this.plotConfig = loaded.plotConfig;
                        LOGGER.info("Successfully loaded plot configuration with {} plot types", 
                                    loaded.plotConfig.getPlotTypes().size());
                    }
                    
                    // Load job multipliers (use defaults if not present for backward compatibility)
                    // Gson sets missing double fields to 0.0, so we check if it's not 0.0
                    if (loaded.jobQuantityMultiplier != 0.0) {
                        this.jobQuantityMultiplier = loaded.jobQuantityMultiplier;
                    }
                    if (loaded.jobPayoutMultiplier != 0.0) {
                        this.jobPayoutMultiplier = loaded.jobPayoutMultiplier;
                    }
                    
                    // Load web sync configuration
                    this.webSyncEnabled = loaded.webSyncEnabled;
                    if (loaded.webSyncApiUrl != null) {
                        this.webSyncApiUrl = loaded.webSyncApiUrl;
                    }
                    if (loaded.webSyncApiKey != null) {
                        this.webSyncApiKey = loaded.webSyncApiKey;
                    }
                    if (loaded.webSyncInterval > 0) {
                        this.webSyncInterval = loaded.webSyncInterval;
                    }
                    
                    LOGGER.info("Loaded configuration from {}", configFile.getAbsolutePath());
                    LOGGER.info("Job multipliers - Quantity: {}, Payout: {}", 
                                this.jobQuantityMultiplier, this.jobPayoutMultiplier);
                    LOGGER.info("Web sync - Enabled: {}, Interval: {}s", 
                                this.webSyncEnabled, this.webSyncInterval);
                } else {
                    LOGGER.warn("Loaded config was null, using default values");
                }
            } catch (Exception e) {
                LOGGER.error("Error loading config file: {}", e.getMessage(), e);
                // Continue with default values
            }
        } else {
            LOGGER.info("Config file does not exist, creating default configuration");
            // Save default config
            save(server);
        }
    }
    
    /**
     * Save the config to file
     * @param server The server instance for file operations
     */
    public void save(MinecraftServer server) {
        LOGGER.info("Attempting to save configuration...");
        
        // Create proper paths by resolving against server run directory
        Path runDir = server.getRunDirectory();
        Path configDirPath = runDir.resolve(CONFIG_DIR);
        Path configFilePath = configDirPath.resolve(CONFIG_FILE);
        
        File configDir = configDirPath.toFile();
        File configFile = configFilePath.toFile();
        
        LOGGER.info("Config directory path: {}", configDir.getAbsolutePath());
        LOGGER.info("Config file path: {}", configFile.getAbsolutePath());
        
        // Create directory if it doesn't exist
        if (!configDir.exists()) {
            LOGGER.info("Config directory does not exist, attempting to create it");
            boolean created = configDir.mkdirs();
            if (!created) {
                LOGGER.error("Failed to create config directory: {}", configDir.getAbsolutePath());
                return;
            } else {
                LOGGER.info("Successfully created config directory");
            }
        }
        
        try {
            LOGGER.info("Writing configuration to file");
            FileWriter writer = new FileWriter(configFile);
            GSON.toJson(this, writer);
            writer.close();
            LOGGER.info("Successfully saved configuration to {}", configFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Error saving config file: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get the plot configuration
     * @return The plot configuration
     */
    public PlotConfig getPlotConfig() {
        return plotConfig;
    }
    
    /**
     * Get the job quantity multiplier
     * @return The job quantity multiplier
     */
    public double getJobQuantityMultiplier() {
        return jobQuantityMultiplier;
    }
    
    /**
     * Get the job payout multiplier
     * @return The job payout multiplier
     */
    public double getJobPayoutMultiplier() {
        return jobPayoutMultiplier;
    }
    
    /**
     * Check if web sync is enabled
     * @return True if web sync is enabled
     */
    public boolean isWebSyncEnabled() {
        return webSyncEnabled;
    }
    
    /**
     * Get the web sync API URL
     * @return The API URL for web sync
     */
    public String getWebSyncApiUrl() {
        return webSyncApiUrl;
    }
    
    /**
     * Get the web sync API key
     * @return The API key for web sync
     */
    public String getWebSyncApiKey() {
        return webSyncApiKey;
    }
    
    /**
     * Get the web sync interval in seconds
     * @return The sync interval in seconds
     */
    public int getWebSyncInterval() {
        return webSyncInterval;
    }
    
    /**
     * Configuration class for plot-related settings
     */
    public static class PlotConfig {
        private Map<String, PlotTypeConfig> plotTypes;
        
        public PlotConfig() {
            // Default plot type configurations
            this.plotTypes = new HashMap<>();
            this.plotTypes.put("PERSONAL", new PlotTypeConfig("Personal", 1000, 1));
            this.plotTypes.put("FARM", new PlotTypeConfig("Farm", 2000, 2));
            this.plotTypes.put("BUSINESS", new PlotTypeConfig("Business", 3000, 3));
            this.plotTypes.put("INDUSTRIAL", new PlotTypeConfig("Industrial", 3000, 3));
        }
        
        /**
         * Get the configuration for a specific plot type
         * @param typeName The name of the plot type (e.g., "PERSONAL")
         * @return The configuration for that plot type, or null if not found
         */
        public PlotTypeConfig getPlotTypeConfig(String typeName) {
            return plotTypes.get(typeName);
        }
        
        /**
         * Get all plot type configurations
         * @return Map of all plot type configurations
         */
        public Map<String, PlotTypeConfig> getPlotTypes() {
            return plotTypes;
        }
    }
    
    /**
     * Configuration class for a specific plot type
     */
    public static class PlotTypeConfig {
        private String displayName;
        private int purchasePrice;
        private int dailyTax;
        
        /**
         * Constructor with all fields
         * @param displayName The display name of the plot type
         * @param purchasePrice The purchase price of the plot type
         * @param dailyTax The daily tax of the plot type
         */
        public PlotTypeConfig(String displayName, int purchasePrice, int dailyTax) {
            this.displayName = displayName;
            this.purchasePrice = purchasePrice;
            this.dailyTax = dailyTax;
        }
        
        /**
         * Default constructor for Gson deserialization
         */
        public PlotTypeConfig() {
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public int getPurchasePrice() {
            return purchasePrice;
        }
        
        public int getDailyTax() {
            return dailyTax;
        }
    }
} 