package net.currencymod.jobs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.currencymod.CurrencyMod;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Configuration for jobs.
 */
public class JobConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/JobConfig");
    private static final String CONFIG_FILE = "currency_mod/job_templates.json";
    private static final Type TEMPLATE_LIST_TYPE = new TypeToken<List<JobTemplate>>() {}.getType();
    
    private final List<JobTemplate> templates;
    private final Random random = new Random();
    
    /**
     * Creates a new job configuration.
     */
    public JobConfig() {
        this.templates = loadTemplates();
        
        // If no templates were loaded, add defaults
        if (templates.isEmpty()) {
            templates.addAll(getDefaultTemplates());
            saveTemplates();
        }
    }
    
    /**
     * Loads job templates from disk.
     *
     * @return The loaded templates
     */
    private List<JobTemplate> loadTemplates() {
        try {
            // Get the server instance from CurrencyMod
            MinecraftServer server = CurrencyMod.getServer();
            if (server == null) {
                LOGGER.error("Cannot get server from CurrencyMod, using default templates");
                return getDefaultTemplates();
            }
            
            // Get the config file path
            Path runDirPath = server.getRunDirectory();
            File configFile = runDirPath.resolve(CONFIG_FILE).toFile();
            
            // If the file doesn't exist, create default templates
            if (!configFile.exists()) {
                List<JobTemplate> defaultTemplates = getDefaultTemplates();
                saveTemplates(defaultTemplates);
                return defaultTemplates;
            }
            
            // Read the file
            try (FileReader reader = new FileReader(configFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                List<JobTemplate> loadedTemplates = gson.fromJson(reader, TEMPLATE_LIST_TYPE);
                
                // Validate templates
                if (loadedTemplates == null || loadedTemplates.isEmpty()) {
                    LOGGER.warn("No job templates found in config, using defaults");
                    return getDefaultTemplates();
                }
                
                // Filter out invalid templates
                List<JobTemplate> validTemplates = new ArrayList<>();
                for (JobTemplate template : loadedTemplates) {
                    if (isValidTemplate(template)) {
                        validTemplates.add(template);
                    } else {
                        LOGGER.warn("Invalid job template found: {}", template.itemId);
                    }
                }
                
                // If no valid templates, use defaults
                if (validTemplates.isEmpty()) {
                    LOGGER.warn("No valid job templates found in config, using defaults");
                    return getDefaultTemplates();
                }
                
                LOGGER.info("Loaded {} job templates from config", validTemplates.size());
                return validTemplates;
            }
        } catch (Exception e) {
            LOGGER.error("Error loading job templates: {}", e.getMessage());
            return getDefaultTemplates();
        }
    }
    
    /**
     * Saves job templates to disk.
     */
    private void saveTemplates() {
        saveTemplates(templates);
    }
    
    /**
     * Saves the provided job templates to disk.
     * 
     * @param templatesToSave The templates to save
     */
    private void saveTemplates(List<JobTemplate> templatesToSave) {
        try {
            // Get the server instance from CurrencyMod
            MinecraftServer server = CurrencyMod.getServer();
            if (server == null) {
                LOGGER.error("Cannot get server from CurrencyMod, cannot save templates");
                return;
            }
            
            // Get the config file path
            Path runDirPath = server.getRunDirectory();
            File configFile = runDirPath.resolve(CONFIG_FILE).toFile();
            
            // Create parent directory if it doesn't exist
            File parentDir = configFile.getParentFile();
            if (!parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    LOGGER.error("Failed to create parent directory for job templates config");
                    return;
                }
            }
            
            // Write the file
            try (FileWriter writer = new FileWriter(configFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(templatesToSave, writer);
                LOGGER.info("Saved {} job templates to config", templatesToSave.size());
            }
        } catch (IOException e) {
            LOGGER.error("Error saving job templates: {}", e.getMessage());
        }
    }
    
    /**
     * Checks if a template is valid.
     *
     * @param template The template to check
     * @return True if the template is valid, false otherwise
     */
    private boolean isValidTemplate(JobTemplate template) {
        // Check if the item exists
        try {
            // Parse the item ID, handling both formats: "minecraft:diamond" and "diamond"
            Identifier itemId;
            if (template.itemId.contains(":")) {
                // Already has namespace, use directly
                itemId = Identifier.of(template.itemId);
            } else {
                // Add minecraft namespace
                itemId = Identifier.of("minecraft", template.itemId);
            }
            
            // Check if the item exists
            Item item = Registries.ITEM.get(itemId);
            if (item == Items.AIR) {
                LOGGER.warn("Invalid job template: Item ID '{}' does not exist", template.itemId);
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Invalid job template: Invalid item ID '{}'", template.itemId, e);
            return false;
        }
        
        // Check if the base quantity and reward are valid
        if (template.baseQuantity <= 0 || template.baseReward <= 0) {
            LOGGER.warn("Invalid job template: Base quantity and reward must be positive");
            return false;
        }
        
        return true;
    }
    
    /**
     * Gets the job templates.
     *
     * @return The job templates
     */
    public List<JobTemplate> getTemplates() {
        return templates;
    }
    
    /**
     * Gets a random job template.
     *
     * @return A random job template
     */
    public JobTemplate getRandomTemplate() {
        if (templates.isEmpty()) {
            return getDefaultTemplates().get(0);
        }
        
        return templates.get(random.nextInt(templates.size()));
    }
    
    /**
     * Gets a random job template filtered by difficulty.
     * Difficulty levels unlock every 5 player levels:
     * - Difficulty 1: Levels 0-4
     * - Difficulty 2: Levels 5-9
     * - Difficulty 3: Levels 10-14
     * - Difficulty 4: Levels 15-19
     * - Difficulty 5: Levels 20-24
     * - Difficulty 6: Levels 25-30
     *
     * @param playerLevel The player's job level
     * @return A random job template appropriate for the player's level
     */
    public JobTemplate getRandomTemplateForLevel(int playerLevel) {
        // Calculate which difficulty levels the player can access
        int maxDifficulty = Math.min(6, (playerLevel / 5) + 1);
        
        // Filter templates by accessible difficulty levels
        List<JobTemplate> accessibleTemplates = new ArrayList<>();
        for (JobTemplate template : templates) {
            if (template.difficulty <= maxDifficulty) {
                accessibleTemplates.add(template);
            }
        }
        
        // If no accessible templates found, fall back to difficulty 1
        if (accessibleTemplates.isEmpty()) {
            for (JobTemplate template : templates) {
                if (template.difficulty == 1) {
                    accessibleTemplates.add(template);
                }
            }
        }
        
        // If still empty, return first default template
        if (accessibleTemplates.isEmpty()) {
            List<JobTemplate> defaults = getDefaultTemplates();
            return defaults.isEmpty() ? null : defaults.get(0);
        }
        
        return accessibleTemplates.get(random.nextInt(accessibleTemplates.size()));
    }
    
    /**
     * Gets the default job templates.
     *
     * @return The default job templates
     */
    public static List<JobTemplate> getDefaultTemplates() {
        List<JobTemplate> defaultTemplates = new ArrayList<>();
        
        // Difficulty 1 (Levels 0-4): Common resources - Basic items for new players
        defaultTemplates.add(new JobTemplate("minecraft:cobblestone", 64, 8, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:dirt", 64, 6, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:gravel", 32, 6, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:sand", 32, 6, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:oak_log", 32, 12, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:spruce_log", 32, 12, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:birch_log", 32, 12, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:jungle_log", 32, 12, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:acacia_log", 32, 12, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:dark_oak_log", 32, 12, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:wheat", 32, 10, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:carrot", 32, 10, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:potato", 32, 10, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:beetroot", 32, 10, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:rotten_flesh", 32, 12, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:bone", 32, 12, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:string", 16, 10, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:kelp", 32, 8, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:seagrass", 32, 6, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:stone", 64, 8, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:granite", 64, 8, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:diorite", 64, 8, 25, 15, 1));
        defaultTemplates.add(new JobTemplate("minecraft:andesite", 64, 8, 25, 15, 1));
        
        // Difficulty 2 (Levels 5-9): Easy resources - Slightly better items
        defaultTemplates.add(new JobTemplate("minecraft:pumpkin", 16, 10, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:melon", 16, 10, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:apple", 16, 12, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:gunpowder", 16, 14, 25, 20, 2));
        defaultTemplates.add(new JobTemplate("minecraft:spider_eye", 16, 14, 25, 20, 2));
        defaultTemplates.add(new JobTemplate("minecraft:cod", 32, 12, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:salmon", 32, 14, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:deepslate", 64, 10, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:cobbled_deepslate", 64, 10, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:netherrack", 64, 8, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:cherry_log", 32, 12, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:mangrove_log", 32, 12, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:sweet_berries", 32, 12, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:cocoa_beans", 32, 14, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:bamboo", 64, 8, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:sugar_cane", 32, 10, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:cactus", 32, 8, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:mushroom", 16, 12, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:brown_mushroom", 16, 12, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:red_mushroom", 16, 12, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:feather", 32, 10, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:egg", 32, 8, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:ink_sac", 16, 12, 25, 15, 2));
        defaultTemplates.add(new JobTemplate("minecraft:dried_kelp", 32, 10, 25, 15, 2));
        
        // Difficulty 3 (Levels 10-14): Medium resources - Uncommon items
        defaultTemplates.add(new JobTemplate("minecraft:slime_ball", 16, 16, 25, 20, 3));
        defaultTemplates.add(new JobTemplate("minecraft:leather", 16, 16, 25, 20, 3));
        defaultTemplates.add(new JobTemplate("minecraft:coal", 32, 14, 25, 20, 3));
        defaultTemplates.add(new JobTemplate("minecraft:iron_ingot", 16, 18, 25, 20, 3));
        defaultTemplates.add(new JobTemplate("minecraft:redstone", 16, 16, 25, 20, 3));
        defaultTemplates.add(new JobTemplate("minecraft:lapis_lazuli", 16, 16, 25, 20, 3));
        defaultTemplates.add(new JobTemplate("minecraft:quartz", 16, 16, 25, 20, 3));
        defaultTemplates.add(new JobTemplate("minecraft:tropical_fish", 16, 16, 25, 20, 3));
        defaultTemplates.add(new JobTemplate("minecraft:pufferfish", 16, 18, 25, 20, 3));
        defaultTemplates.add(new JobTemplate("minecraft:blackstone", 32, 12, 25, 15, 3));
        defaultTemplates.add(new JobTemplate("minecraft:basalt", 32, 10, 25, 15, 3));
        defaultTemplates.add(new JobTemplate("minecraft:crimson_stem", 32, 14, 25, 15, 3));
        defaultTemplates.add(new JobTemplate("minecraft:warped_stem", 32, 14, 25, 15, 3));
        defaultTemplates.add(new JobTemplate("minecraft:glow_berries", 16, 18, 25, 20, 3));
        defaultTemplates.add(new JobTemplate("minecraft:nether_wart", 16, 16, 25, 20, 3));
        defaultTemplates.add(new JobTemplate("minecraft:glow_ink_sac", 8, 20, 25, 20, 3));
        defaultTemplates.add(new JobTemplate("minecraft:rabbit_hide", 16, 14, 25, 15, 3));
        defaultTemplates.add(new JobTemplate("minecraft:copper_ingot", 16, 16, 25, 20, 3));
        defaultTemplates.add(new JobTemplate("minecraft:raw_iron", 16, 14, 25, 15, 3));
        defaultTemplates.add(new JobTemplate("minecraft:raw_copper", 16, 14, 25, 15, 3));
        defaultTemplates.add(new JobTemplate("minecraft:amethyst_shard", 16, 18, 25, 20, 3));
        defaultTemplates.add(new JobTemplate("minecraft:stripped_oak_log", 32, 14, 25, 15, 3));
        defaultTemplates.add(new JobTemplate("minecraft:stripped_spruce_log", 32, 14, 25, 15, 3));
        
        // Difficulty 4 (Levels 15-19): Hard resources - Rare items
        defaultTemplates.add(new JobTemplate("minecraft:gold_ingot", 8, 24, 25, 25, 4));
        defaultTemplates.add(new JobTemplate("minecraft:diamond", 4, 40, 25, 25, 4));
        defaultTemplates.add(new JobTemplate("minecraft:emerald", 4, 40, 25, 25, 4));
        defaultTemplates.add(new JobTemplate("minecraft:ender_pearl", 8, 24, 25, 25, 4));
        defaultTemplates.add(new JobTemplate("minecraft:blaze_rod", 8, 24, 25, 25, 4));
        defaultTemplates.add(new JobTemplate("minecraft:ghast_tear", 4, 32, 25, 25, 4));
        defaultTemplates.add(new JobTemplate("minecraft:nautilus_shell", 8, 28, 25, 25, 4));
        defaultTemplates.add(new JobTemplate("minecraft:prismarine_shard", 16, 20, 25, 20, 4));
        defaultTemplates.add(new JobTemplate("minecraft:prismarine_crystals", 8, 32, 25, 25, 4));
        defaultTemplates.add(new JobTemplate("minecraft:sea_lantern", 4, 36, 25, 25, 4));
        defaultTemplates.add(new JobTemplate("minecraft:sponge", 4, 30, 25, 25, 4));
        defaultTemplates.add(new JobTemplate("minecraft:end_stone", 32, 14, 25, 20, 4));
        defaultTemplates.add(new JobTemplate("minecraft:rabbit_foot", 8, 22, 25, 20, 4));
        defaultTemplates.add(new JobTemplate("minecraft:phantom_membrane", 8, 26, 25, 25, 4));
        defaultTemplates.add(new JobTemplate("minecraft:scute", 4, 30, 25, 25, 4));
        defaultTemplates.add(new JobTemplate("minecraft:honeycomb", 8, 24, 25, 25, 4));
        defaultTemplates.add(new JobTemplate("minecraft:raw_gold", 8, 22, 25, 20, 4));
        defaultTemplates.add(new JobTemplate("minecraft:glowstone_dust", 16, 18, 25, 20, 4));
        defaultTemplates.add(new JobTemplate("minecraft:nether_wart_block", 4, 28, 25, 25, 4));
        
        // Difficulty 5 (Levels 20-24): Very hard resources - Very rare items
        defaultTemplates.add(new JobTemplate("minecraft:echo_shard", 4, 38, 25, 30, 5));
        defaultTemplates.add(new JobTemplate("minecraft:netherite_ingot", 2, 80, 25, 30, 5));
        defaultTemplates.add(new JobTemplate("minecraft:ancient_debris", 2, 70, 25, 30, 5));
        defaultTemplates.add(new JobTemplate("minecraft:shulker_shell", 4, 60, 25, 30, 5));
        defaultTemplates.add(new JobTemplate("minecraft:elytra", 1, 100, 25, 35, 5));
        defaultTemplates.add(new JobTemplate("minecraft:beacon", 1, 120, 25, 35, 5));
        defaultTemplates.add(new JobTemplate("minecraft:nether_star", 1, 150, 25, 40, 5));
        defaultTemplates.add(new JobTemplate("minecraft:totem_of_undying", 1, 90, 25, 35, 5));
        defaultTemplates.add(new JobTemplate("minecraft:heart_of_the_sea", 1, 80, 25, 30, 5));
        defaultTemplates.add(new JobTemplate("minecraft:conduit", 1, 100, 25, 35, 5));
        defaultTemplates.add(new JobTemplate("minecraft:trident", 1, 70, 25, 30, 5));
        defaultTemplates.add(new JobTemplate("minecraft:enchanted_golden_apple", 1, 110, 25, 35, 5));
        defaultTemplates.add(new JobTemplate("minecraft:music_disc_otherside", 1, 50, 25, 30, 5));
        defaultTemplates.add(new JobTemplate("minecraft:music_disc_pigstep", 1, 55, 25, 30, 5));
        
        // Difficulty 6 (Levels 25-30): Extreme resources - Ultra rare legendary items
        defaultTemplates.add(new JobTemplate("minecraft:dragon_egg", 1, 200, 25, 50, 6));
        defaultTemplates.add(new JobTemplate("minecraft:dragon_breath", 4, 65, 25, 30, 6));
        
        return defaultTemplates;
    }
    
    /**
     * A class representing a job template.
     */
    public static class JobTemplate {
        public String itemId;
        public int baseQuantity;
        public int baseReward;
        public int quantityVariation;
        public int rewardVariation;
        public int difficulty; // 1-6, determines which player levels can access this job
        
        /**
         * Creates a new job template.
         *
         * @param itemId           The item ID
         * @param baseQuantity     The base quantity
         * @param baseReward       The base reward
         * @param quantityVariation The quantity variation (percentage)
         * @param rewardVariation   The reward variation (percentage)
         * @param difficulty       The difficulty level (1-6)
         */
        public JobTemplate(String itemId, int baseQuantity, int baseReward, int quantityVariation, int rewardVariation, int difficulty) {
            this.itemId = itemId;
            this.baseQuantity = baseQuantity;
            this.baseReward = baseReward;
            this.quantityVariation = quantityVariation;
            this.rewardVariation = rewardVariation;
            this.difficulty = difficulty;
        }
        
        /**
         * Creates a new job template.
         */
        public JobTemplate() {
            // Default constructor for Gson
        }
    }
} 