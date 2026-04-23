package net.currencymod.jobs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.currencymod.CurrencyMod;
import net.currencymod.util.JobAdapter;
import net.currencymod.util.UUIDAdapter;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.currencymod.util.FileUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.currencymod.economy.EconomyManager;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Manages jobs and player job levels.
 */
public class JobManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/JobManager");
    private static final int BASE_JOBS_PER_PLAYER = 5;
    private static final String JOBS_FILE = "currency_mod/jobs.json";
    private static final String LEVELS_FILE = "currency_mod/job_levels.json";
    private static final String STREAKS_FILE = "currency_mod/job_streaks.json";
    private static final String BOOSTERS_FILE = "currency_mod/job_boosters.json";
    private static final String SKIPS_FILE = "currency_mod/job_skips.json";
    private static final Type PLAYER_JOBS_TYPE = new TypeToken<Map<UUID, List<Job>>>() {}.getType();
    private static final Type PLAYER_LEVELS_TYPE = new TypeToken<Map<UUID, PlayerJobLevel>>() {}.getType();
    private static final Type PLAYER_STREAKS_TYPE = new TypeToken<Map<UUID, PlayerJobStreak>>() {}.getType();
    
    // Constants for booster drop chances
    private static final double PREMIUM_BOOSTER_CHANCE = 0.01;  // 1% chance
    private static final double BASIC_BOOSTER_CHANCE = 0.05;    // 5% chance
    private static final double JOB_SKIP_CHANCE = 0.10;         // 10% chance
    private static final int SKIPS_PER_LEVEL = 3;               // 3 skips per level up
    
    private final JobConfig jobConfig;
    private final Map<UUID, List<Job>> playerJobs;
    private final Map<UUID, PlayerJobLevel> playerLevels;
    private final Map<UUID, PlayerJobStreak> playerStreaks;
    private final Map<UUID, PlayerBoosterData> playerBoosters;
    private final Map<UUID, PlayerJobSkipData> playerSkips;
    private final Gson gson;
    private final Map<UUID, Map<Item, Integer>> playerJobItems = new HashMap<>();
    
    private static JobManager instance;
    
    /**
     * Gets the singleton instance of the job manager.
     *
     * @return The job manager
     */
    public static JobManager getInstance() {
        if (instance == null) {
            instance = new JobManager();
        }
        return instance;
    }
    
    /**
     * Creates a new job manager.
     */
    private JobManager() {
        this.jobConfig = new JobConfig();
        this.playerJobs = new HashMap<>();
        this.playerLevels = new HashMap<>();
        this.playerStreaks = new HashMap<>();
        this.playerBoosters = new HashMap<>();
        this.playerSkips = new HashMap<>();
        
        // Create gson with custom type adapters
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(UUID.class, new UUIDAdapter())
            .registerTypeAdapter(Job.class, new JobAdapter())
            .create();
        
        load();
    }
    
    /**
     * Loads jobs, levels, and streaks data from files.
     */
    public void load() {
        MinecraftServer server = CurrencyMod.getServer();
        if (server == null) {
            LOGGER.warn("Cannot load job data: server is null");
            return;
        }
        
        // Load jobs
        File jobsFile = FileUtil.getServerFile(server, JOBS_FILE);
        if (jobsFile.exists()) {
            try (FileReader reader = new FileReader(jobsFile)) {
                Map<UUID, List<Job>> loadedJobs = gson.fromJson(reader, PLAYER_JOBS_TYPE);
                if (loadedJobs != null) {
                    playerJobs.clear();
                    playerJobs.putAll(loadedJobs);
                    LOGGER.info("Loaded jobs for {} players", playerJobs.size());
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load jobs data: {}", e.getMessage());
            }
        }
        
        // Load levels
        File levelsFile = FileUtil.getServerFile(server, LEVELS_FILE);
        if (levelsFile.exists()) {
            try (FileReader reader = new FileReader(levelsFile)) {
                Map<UUID, PlayerJobLevel> loadedLevels = gson.fromJson(reader, PLAYER_LEVELS_TYPE);
                if (loadedLevels != null) {
                    playerLevels.clear();
                    playerLevels.putAll(loadedLevels);
                    LOGGER.info("Loaded job levels for {} players", playerLevels.size());
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load job levels data: {}", e.getMessage());
            }
        }
        
        // Load streaks
        File streaksFile = FileUtil.getServerFile(server, STREAKS_FILE);
        if (streaksFile.exists()) {
            try (FileReader reader = new FileReader(streaksFile)) {
                Map<UUID, PlayerJobStreak> loadedStreaks = gson.fromJson(reader, PLAYER_STREAKS_TYPE);
                if (loadedStreaks != null) {
                    playerStreaks.clear();
                    playerStreaks.putAll(loadedStreaks);
                    LOGGER.info("Loaded job streaks for {} players", playerStreaks.size());
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load job streaks data: {}", e.getMessage());
            }
        }
        
        // Load boosters
        File boostersFile = FileUtil.getServerFile(server, BOOSTERS_FILE);
        if (boostersFile.exists()) {
            try (FileReader reader = new FileReader(boostersFile)) {
                JsonObject boostersJson = JsonParser.parseReader(reader).getAsJsonObject();
                
                for (String playerUuidStr : boostersJson.keySet()) {
                    try {
                        UUID playerUuid = UUID.fromString(playerUuidStr);
                        PlayerBoosterData playerBoosterData = new PlayerBoosterData(playerUuid);
                        JsonObject playerData = boostersJson.getAsJsonObject(playerUuidStr);
                        
                        // Load owned boosters
                        if (playerData.has("owned")) {
                            JsonObject owned = playerData.getAsJsonObject("owned");
                            for (BoosterType type : BoosterType.values()) {
                                String typeName = type.name();
                                if (owned.has(typeName)) {
                                    int count = owned.get(typeName).getAsInt();
                                    for (int i = 0; i < count; i++) {
                                        playerBoosterData.addBooster(type);
                                    }
                                }
                            }
                        }
                        
                        // Load active booster if one exists
                        if (playerData.has("active")) {
                            JsonObject activeData = playerData.getAsJsonObject("active");
                            String typeName = activeData.get("type").getAsString();
                            long activationTime = activeData.get("activationTime").getAsLong();
                            long expirationTime = activeData.get("expirationTime").getAsLong();
                            
                            BoosterType type = BoosterType.valueOf(typeName);
                            // Only load if the booster hasn't expired yet
                            if (expirationTime > System.currentTimeMillis()) {
                                playerBoosterData.setActiveBooster(new Booster(type, activationTime, expirationTime));
                            }
                        }
                        
                        playerBoosters.put(playerUuid, playerBoosterData);
                    } catch (Exception e) {
                        LOGGER.error("Error loading boosters for player {}: {}", playerUuidStr, e.getMessage());
                    }
                }
                LOGGER.info("Loaded job boosters for {} players", playerBoosters.size());
            } catch (IOException e) {
                LOGGER.error("Failed to load job boosters data: {}", e.getMessage());
            }
        }
        
        // Load skips
        File skipsFile = FileUtil.getServerFile(server, SKIPS_FILE);
        if (skipsFile.exists()) {
            try (FileReader reader = new FileReader(skipsFile)) {
                JsonObject skipsJson = JsonParser.parseReader(reader).getAsJsonObject();
                
                for (String playerUuidStr : skipsJson.keySet()) {
                    try {
                        UUID playerUuid = UUID.fromString(playerUuidStr);
                        JsonObject playerData = skipsJson.getAsJsonObject(playerUuidStr);
                        
                        int skipCount = playerData.has("skipCount") ? playerData.get("skipCount").getAsInt() : 0;
                        int lastRewardedLevel = playerData.has("lastRewardedLevel") ? playerData.get("lastRewardedLevel").getAsInt() : -1;
                        
                        playerSkips.put(playerUuid, new PlayerJobSkipData(playerUuid, skipCount, lastRewardedLevel));
                    } catch (Exception e) {
                        LOGGER.error("Error loading skips for player {}: {}", playerUuidStr, e.getMessage());
                    }
                }
                LOGGER.info("Loaded job skips for {} players", playerSkips.size());
            } catch (IOException e) {
                LOGGER.error("Failed to load job skips data: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Saves jobs, levels, and streaks data to files.
     */
    public void save() {
        MinecraftServer server = CurrencyMod.getServer();
        if (server == null) {
            LOGGER.warn("Cannot save job data: server is null");
            return;
        }
        
        // Save jobs
        File jobsFile = FileUtil.getServerFile(server, JOBS_FILE);
        try (FileWriter writer = new FileWriter(jobsFile)) {
            gson.toJson(playerJobs, writer);
            LOGGER.debug("Saved jobs for {} players", playerJobs.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save jobs data: {}", e.getMessage());
        }
        
        // Save levels
        File levelsFile = FileUtil.getServerFile(server, LEVELS_FILE);
        try (FileWriter writer = new FileWriter(levelsFile)) {
            gson.toJson(playerLevels, writer);
            LOGGER.debug("Saved job levels for {} players", playerLevels.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save job levels data: {}", e.getMessage());
        }
        
        // Save streaks
        File streaksFile = FileUtil.getServerFile(server, STREAKS_FILE);
        try (FileWriter writer = new FileWriter(streaksFile)) {
            gson.toJson(playerStreaks, writer);
            LOGGER.debug("Saved job streaks for {} players", playerStreaks.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save job streaks data: {}", e.getMessage());
        }
        
        // Save boosters
        File boostersFile = FileUtil.getServerFile(server, BOOSTERS_FILE);
        try (FileWriter writer = new FileWriter(boostersFile)) {
            JsonObject rootObject = new JsonObject();
            
            for (Map.Entry<UUID, PlayerBoosterData> entry : playerBoosters.entrySet()) {
                UUID playerUuid = entry.getKey();
                PlayerBoosterData boosterData = entry.getValue();
                
                // Skip players with no boosters
                if (boosterData.getTotalBoosterCount() == 0 && !boosterData.hasActiveBooster()) {
                    continue;
                }
                
                JsonObject playerObject = new JsonObject();
                
                // Save owned boosters
                JsonObject ownedObject = new JsonObject();
                for (Map.Entry<BoosterType, Integer> boosterEntry : boosterData.getOwnedBoosters().entrySet()) {
                    ownedObject.addProperty(boosterEntry.getKey().name(), boosterEntry.getValue());
                }
                playerObject.add("owned", ownedObject);
                
                // Save active booster if one exists
                Booster activeBooster = boosterData.getActiveBooster();
                if (activeBooster != null && activeBooster.isActive()) {
                    JsonObject activeObject = new JsonObject();
                    activeObject.addProperty("type", activeBooster.getType().name());
                    activeObject.addProperty("activationTime", activeBooster.getActivationTime());
                    activeObject.addProperty("expirationTime", activeBooster.getExpirationTime());
                    playerObject.add("active", activeObject);
                }
                
                rootObject.add(playerUuid.toString(), playerObject);
            }
            
            gson.toJson(rootObject, writer);
            LOGGER.info("Saved job boosters for {} players", playerBoosters.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save job boosters data: {}", e.getMessage());
        }
        
        // Save skips
        File skipsFile = FileUtil.getServerFile(server, SKIPS_FILE);
        try (FileWriter writer = new FileWriter(skipsFile)) {
            JsonObject rootObject = new JsonObject();
            
            for (Map.Entry<UUID, PlayerJobSkipData> entry : playerSkips.entrySet()) {
                UUID playerUuid = entry.getKey();
                PlayerJobSkipData skipData = entry.getValue();
                
                // Skip players with no skips and never rewarded
                if (skipData.getSkipCount() == 0 && skipData.getLastRewardedLevel() == -1) {
                    continue;
                }
                
                JsonObject playerObject = new JsonObject();
                playerObject.addProperty("skipCount", skipData.getSkipCount());
                playerObject.addProperty("lastRewardedLevel", skipData.getLastRewardedLevel());
                
                rootObject.add(playerUuid.toString(), playerObject);
            }
            
            gson.toJson(rootObject, writer);
            LOGGER.debug("Saved job skips for {} players", playerSkips.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save job skips data: {}", e.getMessage());
        }
    }
    
    /**
     * Gets a player's job level object. Creates a new one if it doesn't exist.
     * 
     * @param playerUuid The player's UUID
     * @return The player's job level object
     */
    public PlayerJobLevel getPlayerJobLevel(UUID playerUuid) {
        if (!playerLevels.containsKey(playerUuid)) {
            playerLevels.put(playerUuid, new PlayerJobLevel(playerUuid));
        }
        return playerLevels.get(playerUuid);
    }
    
    /**
     * Gets a player's job level object.
     * 
     * @param player The player
     * @return The player's job level object
     */
    public PlayerJobLevel getPlayerJobLevel(ServerPlayerEntity player) {
        return getPlayerJobLevel(player.getUuid());
    }
    
    /**
     * Gets jobs for a player, generating new ones if they don't have enough.
     *
     * @param player The player
     * @return The jobs
     */
    public List<Job> getPlayerJobs(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        
        // Get player's job level to determine how many jobs they should have
        PlayerJobLevel jobLevel = getPlayerJobLevel(playerUuid);
        int jobsAllowed = jobLevel.getJobsAllowed();
        
        // Create jobs if the player doesn't have any
        int playerLevel = jobLevel.getLevel();
        if (!playerJobs.containsKey(playerUuid)) {
            List<Job> jobs = new ArrayList<>();
            for (int i = 0; i < jobsAllowed; i++) {
                jobs.add(generateUniqueJob(jobs, playerLevel));
            }
            playerJobs.put(playerUuid, jobs);
            save();
            return playerJobs.get(playerUuid);
        }
        
        // Check if we need to add more jobs because the player leveled up
        List<Job> existingJobs = playerJobs.get(playerUuid);
        if (existingJobs.size() < jobsAllowed) {
            // Add more jobs up to the allowed amount
            int jobsToAdd = jobsAllowed - existingJobs.size();
            LOGGER.info("Adding {} more jobs for player {} (level {})", 
                      jobsToAdd, playerUuid, playerLevel);
            
            for (int i = 0; i < jobsToAdd; i++) {
                existingJobs.add(generateUniqueJob(existingJobs, playerLevel));
            }
            save();
        }
        
        return playerJobs.get(playerUuid);
    }
    
    /**
     * Gets the player's active job, or null if none is active.
     *
     * @param player The player
     * @return The active job, or null if none
     */
    public Job getActiveJob(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        List<Job> jobs = playerJobs.getOrDefault(playerUuid, new ArrayList<>());
        return jobs.stream().filter(Job::isActive).findFirst().orElse(null);
    }
    
    /**
     * Activates a job for a player by its ID.
     *
     * @param player The player
     * @param jobId  The ID of the job to activate
     * @return True if successful, false otherwise
     */
    public boolean activateJob(ServerPlayerEntity player, String jobId) {
        UUID playerUuid = player.getUuid();
        
        // Check if player has jobs
        if (!playerJobs.containsKey(playerUuid)) {
            LOGGER.debug("Player {} has no jobs to activate", playerUuid);
            return false;
        }
        
        List<Job> jobs = playerJobs.get(playerUuid);
        
        // Check if player already has an active job
        Job currentActiveJob = getActiveJob(player);
        if (currentActiveJob != null) {
            LOGGER.debug("Player {} already has active job {}", playerUuid, currentActiveJob.getId());
            player.sendMessage(Text.literal("You already have an active job. Abandon it first.").formatted(Formatting.RED));
            return false;
        }
        
        // Find the job with matching ID prefix (we're using the first part of the UUID)
        Optional<Job> jobOpt = jobs.stream()
                .filter(job -> job.getId().toString().startsWith(jobId))
                .findFirst();
        
        // If not found with prefix, try exact match (backwards compatibility)
        if (jobOpt.isEmpty()) {
            LOGGER.debug("Job with prefix {} not found, trying exact match", jobId);
            try {
                // Handle case where full UUID was passed
                if (jobId.length() >= 36) {
                    UUID fullId = UUID.fromString(jobId);
                    jobOpt = jobs.stream()
                        .filter(job -> job.getId().equals(fullId))
                        .findFirst();
                }
            } catch (IllegalArgumentException e) {
                // Not a valid UUID, just continue with normal flow
            }
        }
        
        if (jobOpt.isEmpty()) {
            LOGGER.debug("Player {} job ID {} not found", playerUuid, jobId);
            player.sendMessage(Text.literal("Job not found. Use /jobs list to see available jobs.").formatted(Formatting.RED));
            return false;
        }
        
        // Activate the job
        Job job = jobOpt.get();
        job.setActive(true);
        
        // Save the changes immediately to persist this active job state
        save();
        
        LOGGER.info("Player {} activated job {}", playerUuid, job.getId());

        // We've removed the feedback messages here because they are now handled in the JobCommand class
        // This prevents duplicate activation messages
        
        return true;
    }
    
    /**
     * Gets a player's job streak, creating one if it doesn't exist.
     *
     * @param playerUuid The player's UUID
     * @return The player's job streak
     */
    public PlayerJobStreak getPlayerJobStreak(UUID playerUuid) {
        return playerStreaks.computeIfAbsent(playerUuid, uuid -> new PlayerJobStreak(uuid));
    }
    
    /**
     * Gets a player's job streak, creating one if it doesn't exist.
     *
     * @param player The player
     * @return The player's job streak
     */
    public PlayerJobStreak getPlayerJobStreak(ServerPlayerEntity player) {
        return getPlayerJobStreak(player.getUuid());
    }
    
    /**
     * Abandons the player's active job.
     *
     * @param player The player
     * @return True if successful, false otherwise
     */
    public boolean abandonJob(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        
        // Check if player has jobs
        if (!playerJobs.containsKey(playerUuid)) {
            return false;
        }
        
        // Find the active job
        List<Job> jobs = playerJobs.get(playerUuid);
        Optional<Job> activeJobOpt = jobs.stream()
                .filter(Job::isActive)
                .findFirst();
        
        if (activeJobOpt.isEmpty()) {
            player.sendMessage(Text.literal("You don't have an active job to abandon.").formatted(Formatting.RED));
            return false;
        }
        
        // Deactivate the job
        Job activeJob = activeJobOpt.get();
        activeJob.setActive(false);
        
        // Check if this is a mega job - if so, no abandonment fee
        boolean isMegaJob = activeJob.isMegaJob();
        
        // Get player's job level and streak
        PlayerJobLevel jobLevel = getPlayerJobLevel(player);
        PlayerJobStreak jobStreak = getPlayerJobStreak(player);
        
        // Calculate adjusted reward with level and streak bonuses
        int originalReward = activeJob.getReward();
        double totalMultiplier = getTotalMultiplier(jobLevel, jobStreak);
        int adjustedReward = (int) Math.ceil(originalReward * totalMultiplier);
        
        // For mega jobs, no penalty is charged
        int playerLevel = jobLevel.getLevel();
        if (isMegaJob) {
            // Generate a new job to replace this one
            jobs.remove(activeJob);
            jobs.add(generateUniqueJob(jobs, playerLevel));
            
            // Save the changes
            save();
            
            // Notify the player - no penalty for mega jobs
            player.sendMessage(Text.literal("MEGA job abandoned with no penalty.")
                    .formatted(Formatting.GREEN)
                    .append(Text.literal(" A new job has been generated.")
                    .formatted(Formatting.WHITE)));
            
            return true;
        }
        
        // For regular jobs, calculate abandonment penalty (75% of adjusted job reward)
        int penalty = Math.max(1, (int)(adjustedReward * 0.75));
        
        // Apply the penalty
        double currentBalance = CurrencyMod.getEconomyManager().getBalance(playerUuid);
        
        // Check if player has enough money to pay the penalty
        if (currentBalance < penalty) {
            player.sendMessage(Text.literal("You don't have enough money to abandon this job. Abandonment penalty: ")
                    .append(Text.literal("$" + penalty).formatted(Formatting.RED))
                    .append(Text.literal(". Your balance: "))
                    .append(Text.literal("$" + (int)currentBalance).formatted(Formatting.GOLD))
                    .append(Text.literal(".")));
            
            // Reactivate the job since abandonment failed
            activeJob.setActive(true);
            return false;
        }
        
        // Deduct the penalty
        CurrencyMod.getEconomyManager().removeBalance(playerUuid, penalty);
        
        // Generate a new job to replace this one
        jobs.remove(activeJob);
        jobs.add(generateUniqueJob(jobs, playerLevel));
        
        // Save the changes
        save();
        
        // Notify the player
        player.sendMessage(Text.literal("Job abandoned. You were charged a penalty of ")
                .append(Text.literal("$" + penalty).formatted(Formatting.RED))
                .append(Text.literal(". A new job has been generated.")));
        
        return true;
    }
    
    /**
     * Checks if a player can complete their active job.
     *
     * @param player The player
     * @return True if the player can complete the job, false otherwise
     */
    public boolean canCompleteJob(ServerPlayerEntity player) {
        Job activeJob = getActiveJob(player);
        
        // Check if player has an active job
        if (activeJob == null) {
            return false;
        }
        
        // Check if player has enough items
        Item requiredItem = activeJob.getItem();
        int requiredQuantity = activeJob.getQuantity();
        
        int playerItemCount = getItemCount(player, requiredItem);
        
        return playerItemCount >= requiredQuantity;
    }
    
    /**
     * Counts how many of a specific item a player has in their inventory.
     *
     * @param player The player
     * @param item   The item to count
     * @return The count
     */
    public int getItemCount(ServerPlayerEntity player, Item item) {
        int count = 0;
        
        // Check main inventory
        for (ItemStack stack : player.getInventory().main) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        
        // Check offhand slot
        ItemStack offhandStack = player.getOffHandStack();
        if (!offhandStack.isEmpty() && offhandStack.getItem() == item) {
            count += offhandStack.getCount();
        }
        
        return count;
    }
    
    /**
     * Completes the active job for a player if they have collected enough items.
     *
     * @param player The player
     * @return true if the job was completed, false otherwise
     */
    public boolean completeJob(ServerPlayerEntity player) {
        LOGGER.info("Player {} attempting to complete job", player.getName().getString());
        
        UUID playerUuid = player.getUuid();
        Job job = getActiveJob(player);
        
        // Check if there's an active job
        if (job == null) {
            LOGGER.info("No active job found for player {}", playerUuid);
            return false;
        }
        
        // Get the item for this job
        Item item = job.getItem();
        
        // Get the adjusted quantity if player has an active booster
        PlayerBoosterData boosterData = getPlayerBoosterData(player);
        int adjustedQuantity = getAdjustedQuantity(job.getQuantity(), boosterData);
        
        // Check if the player has enough items
        int playerItems = getItemCount(player, item);
        LOGGER.info("Player has {} items, needs {} for job completion", playerItems, adjustedQuantity);
        if (playerItems < adjustedQuantity) {
            return false;
        }
        
        // Remove the collected items from the player's inventory
        removeItems(player, item, adjustedQuantity);
        
        // Mark the job as completed
        job.setActive(false);
        
        // Clear the collected items
        playerJobItems.remove(playerUuid);
        
        // Update player level
        PlayerJobLevel jobLevel = getPlayerJobLevel(playerUuid);
        boolean leveledUp = jobLevel.completeJob();
        
        // If player leveled up, award boosters and skips based on their new level
        if (leveledUp) {
            int newLevel = jobLevel.getLevel();
            awardLevelUpBoosters(player, newLevel);
            
            // Award 3 skips per level up
            PlayerJobSkipData skipData = getPlayerSkipData(player);
            skipData.addSkips(SKIPS_PER_LEVEL);
            skipData.setLastRewardedLevel(newLevel);
            
            player.sendMessage(Text.literal("🎁 Level Up Reward! ").formatted(Formatting.GOLD, Formatting.BOLD)
                .append(Text.literal("You received ").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal(SKIPS_PER_LEVEL + " Job Skip" + (SKIPS_PER_LEVEL == 1 ? "" : "s")).formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal(" for reaching level ").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal(String.valueOf(newLevel)).formatted(Formatting.YELLOW))
                .append(Text.literal("!").formatted(Formatting.LIGHT_PURPLE)));
            
            LOGGER.info("Awarded {} job skips to player {} for reaching level {}", 
                SKIPS_PER_LEVEL, player.getName().getString(), newLevel);
        }
        
        // Calculate the reward with bonuses
        PlayerJobStreak jobStreak = getPlayerJobStreak(playerUuid);
        double totalMultiplier = getTotalMultiplier(jobLevel, jobStreak, boosterData);
        int baseReward = job.getReward();
        int finalReward = (int) Math.ceil(baseReward * totalMultiplier);
        LOGGER.info("Rewarding {} coins to player {} (base: {}, multiplier: {})", 
                  finalReward, player.getName().getString(), baseReward, totalMultiplier);
        
        // Give the reward to the player
        EconomyManager economyManager = CurrencyMod.getEconomyManager();
        economyManager.addBalance(playerUuid, finalReward);
        
        // Update the job streak
        boolean streakAdvanced = false;
        if (CurrencyMod.getServer() != null) {
            streakAdvanced = jobStreak.recordJobCompletion(CurrencyMod.getServer());
        }
        
        // Award random boosters (1% premium, 5% basic)
        awardRandomBoosters(player);
        
        // Award random job skips (10% chance)
        awardRandomSkips(player);
        
        // Generate a replacement job
        List<Job> playerJobsList = playerJobs.getOrDefault(playerUuid, new ArrayList<>());
        playerJobsList.remove(job);
        int playerLevel = jobLevel.getLevel();
        Job newJob = generateUniqueJob(playerJobsList, playerLevel);
        playerJobsList.add(newJob);
        playerJobs.put(playerUuid, playerJobsList);
        
        // Save the updated data
        save();
        
        // Play a sound effect
        if (leveledUp) {
            player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
        }
        
        return true;
    }
    
    /**
     * Removes items from a player's inventory.
     *
     * @param player   The player
     * @param item     The item to remove
     * @param quantity The quantity to remove
     */
    private void removeItems(ServerPlayerEntity player, Item item, int quantity) {
        int remaining = quantity;
        
        // First try to remove from main inventory
        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            
            if (!stack.isEmpty() && stack.getItem() == item) {
                int toRemove = Math.min(stack.getCount(), remaining);
                stack.decrement(toRemove);
                remaining -= toRemove;
            }
        }
        
        // Update the inventory
        player.getInventory().markDirty();
    }
    
    /**
     * Generates a unique job that doesn't overlap with existing jobs.
     * Jobs are filtered by difficulty based on the player's level.
     *
     * @param existingJobs The player's existing jobs
     * @param playerLevel The player's job level (for difficulty filtering)
     * @return A new unique job
     */
    private Job generateUniqueJob(List<Job> existingJobs, int playerLevel) {
        Set<String> existingItemIds = new HashSet<>();
        
        // Collect existing item IDs to avoid duplicates
        for (Job job : existingJobs) {
            existingItemIds.add(job.getItemId());
        }
        
        // Generate up to 10 attempts to find a unique job with appropriate difficulty
        for (int attempt = 0; attempt < 10; attempt++) {
            JobConfig.JobTemplate template = jobConfig.getRandomTemplateForLevel(playerLevel);
            if (template != null && !existingItemIds.contains(template.itemId)) {
                return Job.fromTemplate(template);
            }
        }
        
        // If all attempts failed, just create any random job with appropriate difficulty
        JobConfig.JobTemplate template = jobConfig.getRandomTemplateForLevel(playerLevel);
        if (template != null) {
            return Job.fromTemplate(template);
        }
        
        // Fallback to any template if difficulty filtering fails
        return Job.fromTemplate(jobConfig.getRandomTemplate());
    }
    
    /**
     * Clears all jobs data. Use with caution!
     */
    public void clearJobs() {
        playerJobs.clear();
        save();
        LOGGER.info("All jobs data has been cleared");
    }
    
    /**
     * Get the count of active jobs across all players
     * 
     * @return The number of active jobs
     */
    public int getActiveJobsCount() {
        int count = 0;
        
        for (List<Job> jobs : playerJobs.values()) {
            for (Job job : jobs) {
                if (job.isActive()) {
                    count++;
                }
            }
        }
        
        LOGGER.debug("Count of active jobs across all players: {}", count);
        return count;
    }
    
    /**
     * Updates a player's jobs after they level up.
     * This should be called when a player gains a level.
     *
     * @param player The player who leveled up
     * @return The number of new jobs added
     */
    public int updateJobsAfterLevelUp(ServerPlayerEntity player) {
        PlayerJobLevel jobLevel = getPlayerJobLevel(player);
        int jobsAllowed = jobLevel.getJobsAllowed();
        
        // Get the player's current jobs
        List<Job> existingJobs = getPlayerJobs(player);
        int currentJobCount = existingJobs.size();
        
        // If they already have enough jobs, nothing to do
        if (currentJobCount >= jobsAllowed) {
            return 0;
        }
        
        // Add more jobs
        int jobsToAdd = jobsAllowed - currentJobCount;
        int playerLevel = jobLevel.getLevel();
        for (int i = 0; i < jobsToAdd; i++) {
            existingJobs.add(generateUniqueJob(existingJobs, playerLevel));
        }
        
        // Save changes
        save();
        
        LOGGER.info("Player {} leveled up to {} - added {} new jobs", 
                   player.getUuid(), jobLevel.getLevel(), jobsToAdd);
        
        return jobsToAdd;
    }
    
    /**
     * Get the total multiplier for a player's job rewards based on level and streak
     * @param jobLevel The player's job level
     * @param jobStreak The player's job streak
     * @param boosterData The player's booster data
     * @return The total multiplier to apply to rewards
     */
    public static double getTotalMultiplier(PlayerJobLevel jobLevel, PlayerJobStreak jobStreak, PlayerBoosterData boosterData) {
        double levelMultiplier = jobLevel.getRewardMultiplier();
        double streakMultiplier = jobStreak.getStreakBonus();
        double boosterMultiplier = boosterData != null ? boosterData.getBoosterRewardMultiplier() : 1.0;
        
        // Convert multipliers to bonus percentages
        int levelBonusPercent = (int)((levelMultiplier * 100) - 100);
        int streakBonusPercent = (int)((streakMultiplier * 100) - 100);
        int boosterBonusPercent = (int)((boosterMultiplier * 100) - 100);
        
        // Add percentages together
        int totalBonusPercent = levelBonusPercent + streakBonusPercent + boosterBonusPercent;
        
        // Convert back to multiplier (e.g., 15% becomes 1.15)
        return 1.0 + (totalBonusPercent / 100.0);
    }
    
    // Overload for backward compatibility
    public static double getTotalMultiplier(PlayerJobLevel jobLevel, PlayerJobStreak jobStreak) {
        return getTotalMultiplier(jobLevel, jobStreak, null);
    }
    
    /**
     * Get the individual bonus percentages for display purposes
     * @param jobLevel The player's job level
     * @param jobStreak The player's job streak
     * @param boosterData The player's booster data
     * @return An array containing [levelBonusPercent, streakBonusPercent, boosterBonusPercent, totalBonusPercent]
     */
    public static int[] getBonusPercentages(PlayerJobLevel jobLevel, PlayerJobStreak jobStreak, PlayerBoosterData boosterData) {
        double levelMultiplier = jobLevel.getRewardMultiplier();
        double streakMultiplier = jobStreak.getStreakBonus();
        double boosterMultiplier = boosterData != null ? boosterData.getBoosterRewardMultiplier() : 1.0;
        
        // Convert multipliers to bonus percentages
        int levelBonusPercent = (int)((levelMultiplier * 100) - 100);
        int streakBonusPercent = (int)((streakMultiplier * 100) - 100);
        int boosterBonusPercent = (int)((boosterMultiplier * 100) - 100);
        
        // Add percentages together
        int totalBonusPercent = levelBonusPercent + streakBonusPercent + boosterBonusPercent;
        
        return new int[] { levelBonusPercent, streakBonusPercent, boosterBonusPercent, totalBonusPercent };
    }
    
    // Overload for backward compatibility
    public static int[] getBonusPercentages(PlayerJobLevel jobLevel, PlayerJobStreak jobStreak) {
        int[] result = getBonusPercentages(jobLevel, jobStreak, null);
        // Return only the first three values for compatibility (level, streak, total)
        return new int[] { result[0], result[1], result[3] };
    }
    
    /**
     * Calculate the adjusted quantity for a job based on active boosters
     * @param originalQuantity The original quantity required
     * @param boosterData The player's booster data
     * @return The adjusted quantity
     */
    public static int getAdjustedQuantity(int originalQuantity, PlayerBoosterData boosterData) {
        if (boosterData == null) {
            return originalQuantity;
        }
        
        // If there is a booster with quantity reduction, apply it
        double quantityMultiplier = boosterData.getBoosterQuantityMultiplier();
        if (quantityMultiplier >= 1.0) {
            return originalQuantity; // No reduction
        }
        
        // Apply reduction and ensure we have at least 1 item required
        return Math.max(1, (int)Math.ceil(originalQuantity * quantityMultiplier));
    }
    
    /**
     * Gets a player's job booster data, creating one if it doesn't exist.
     *
     * @param playerUuid The player's UUID
     * @return The player's booster data
     */
    public PlayerBoosterData getPlayerBoosterData(UUID playerUuid) {
        return playerBoosters.computeIfAbsent(playerUuid, uuid -> new PlayerBoosterData(uuid));
    }
    
    /**
     * Gets a player's job booster data, creating one if it doesn't exist.
     *
     * @param player The player
     * @return The player's booster data
     */
    public PlayerBoosterData getPlayerBoosterData(ServerPlayerEntity player) {
        return getPlayerBoosterData(player.getUuid());
    }
    
    /**
     * Award boosters to a player based on random chance when completing a job
     * 
     * @param player The player who completed the job
     */
    private void awardRandomBoosters(ServerPlayerEntity player) {
        Random random = new Random();
        PlayerBoosterData boosterData = getPlayerBoosterData(player);
        
        // Check for premium booster (1% chance)
        if (random.nextDouble() < PREMIUM_BOOSTER_CHANCE) {
            boosterData.addBooster(BoosterType.PREMIUM);
            player.sendMessage(Text.literal("🎁 You found a ").formatted(Formatting.LIGHT_PURPLE)
                .append(Text.literal("Premium Booster").formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal("! Use it with ").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal("/jobs boosters use premium").formatted(Formatting.YELLOW)));
        }
        
        // Check for basic booster (5% chance)
        else if (random.nextDouble() < BASIC_BOOSTER_CHANCE) {
            boosterData.addBooster(BoosterType.BASIC);
            player.sendMessage(Text.literal("🎁 You found a ").formatted(Formatting.LIGHT_PURPLE)
                .append(Text.literal("Basic Booster").formatted(Formatting.AQUA, Formatting.BOLD))
                .append(Text.literal("! Use it with ").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal("/jobs boosters use basic").formatted(Formatting.YELLOW)));
        }
    }
    
    /**
     * Gets the number of items a player has collected for their active job.
     *
     * @param playerUuid The player's UUID
     * @param item The item to check
     * @return The number of items collected
     */
    public int getCollectedItems(UUID playerUuid, Item item) {
        Map<Item, Integer> items = playerJobItems.getOrDefault(playerUuid, new HashMap<>());
        return items.getOrDefault(item, 0);
    }
    
    /**
     * Gets the number of items a player has collected for their active job.
     *
     * @param player The player
     * @param item The item to check
     * @return The number of items collected
     */
    public int getCollectedItems(ServerPlayerEntity player, Item item) {
        return getCollectedItems(player.getUuid(), item);
    }
    
    /**
     * Records collected items for a player's active job.
     *
     * @param player The player
     * @param item The item collected
     * @param count The number of items collected
     */
    public void recordCollectedItems(ServerPlayerEntity player, Item item, int count) {
        UUID playerUuid = player.getUuid();
        Map<Item, Integer> items = playerJobItems.computeIfAbsent(playerUuid, uuid -> new HashMap<>());
        
        int current = items.getOrDefault(item, 0);
        items.put(item, current + count);
        
        // Check if this item is needed for their active job
        Job job = getActiveJob(player);
        
        if (job != null && job.getItem() == item) {
            // Get the adjusted quantity if player has an active booster
            PlayerBoosterData boosterData = getPlayerBoosterData(player);
            int adjustedQuantity = getAdjustedQuantity(job.getQuantity(), boosterData);
            
            int collectedTotal = items.get(item);
            
            // Notify them if they've collected enough
            if (collectedTotal >= adjustedQuantity && collectedTotal - count < adjustedQuantity) {
                player.sendMessage(Text.literal("You have collected enough items to complete your job! Type ")
                    .append(Text.literal("/jobs complete").formatted(Formatting.GREEN, Formatting.BOLD))
                    .append(" to turn in your job and get your reward."), false);
                
                // Updated playSound call to match the correct method signature
                player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
            }
        }
    }
    
    /**
     * Awards boosters to a player who has leveled up based on their current level.
     * - Premium booster on levels 5, 10, 15, 20, 25, and 30
     * - Basic booster on all other level ups
     *
     * @param player The player who leveled up
     * @param level The player's current level after leveling up
     */
    public void awardLevelUpBoosters(ServerPlayerEntity player, int level) {
        PlayerBoosterData boosterData = getPlayerBoosterData(player);
        
        // Special levels get Premium boosters
        if (level == 5 || level == 10 || level == 15 || level == 20 || level == 25 || level == 30) {
            boosterData.addBooster(BoosterType.PREMIUM);
            player.sendMessage(Text.literal("🎁 Level Up Reward! ").formatted(Formatting.GOLD, Formatting.BOLD)
                .append(Text.literal("You received a ").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal("Premium Booster").formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal(" for reaching level ").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal(String.valueOf(level)).formatted(Formatting.YELLOW))
                .append(Text.literal("!").formatted(Formatting.LIGHT_PURPLE)));
                
            LOGGER.info("Awarded Premium Booster to player {} for reaching level {}", 
                player.getName().getString(), level);
        } 
        // All other level ups get Basic boosters
        else {
            boosterData.addBooster(BoosterType.BASIC);
            player.sendMessage(Text.literal("🎁 Level Up Reward! ").formatted(Formatting.GOLD, Formatting.BOLD)
                .append(Text.literal("You received a ").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal("Basic Booster").formatted(Formatting.AQUA, Formatting.BOLD))
                .append(Text.literal(" for reaching level ").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal(String.valueOf(level)).formatted(Formatting.YELLOW))
                .append(Text.literal("!").formatted(Formatting.LIGHT_PURPLE)));
                
            LOGGER.info("Awarded Basic Booster to player {} for reaching level {}", 
                player.getName().getString(), level);
        }
        
        // Save the updated booster data
        save();
    }

    /**
     * Gets a map of all player job levels.
     * @return Map of player UUIDs to their job levels
     */
    public Map<UUID, PlayerJobLevel> getAllPlayerJobLevels() {
        // Return a defensive copy
        return new HashMap<>(playerLevels);
    }
    
    /**
     * Gets a player's job skip data, creating one if it doesn't exist.
     *
     * @param playerUuid The player's UUID
     * @return The player's skip data
     */
    public PlayerJobSkipData getPlayerSkipData(UUID playerUuid) {
        return playerSkips.computeIfAbsent(playerUuid, uuid -> new PlayerJobSkipData(uuid));
    }
    
    /**
     * Gets a player's job skip data, creating one if it doesn't exist.
     *
     * @param player The player
     * @return The player's skip data
     */
    public PlayerJobSkipData getPlayerSkipData(ServerPlayerEntity player) {
        return getPlayerSkipData(player.getUuid());
    }
    
    /**
     * Awards job skips to a player based on random chance when completing a job.
     * 
     * @param player The player who completed the job
     */
    private void awardRandomSkips(ServerPlayerEntity player) {
        Random random = new Random();
        PlayerJobSkipData skipData = getPlayerSkipData(player);
        
        // Check for job skip (10% chance)
        if (random.nextDouble() < JOB_SKIP_CHANCE) {
            skipData.addSkips(1);
            player.sendMessage(Text.literal("🎁 You found a ").formatted(Formatting.LIGHT_PURPLE)
                .append(Text.literal("Job Skip").formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal("! Use it with ").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal("/jobs skip").formatted(Formatting.YELLOW))
                .append(Text.literal(" to skip your current job.").formatted(Formatting.LIGHT_PURPLE)));
            save();
        }
    }
    
    /**
     * Checks and awards retrospective job skips for a player based on their level.
     * This should be called when a player logs in.
     * 
     * @param player The player to check
     */
    public void checkAndAwardRetrospectiveSkips(ServerPlayerEntity player) {
        PlayerJobLevel jobLevel = getPlayerJobLevel(player);
        PlayerJobSkipData skipData = getPlayerSkipData(player);
        
        int currentLevel = jobLevel.getLevel();
        int lastRewardedLevel = skipData.getLastRewardedLevel();
        
        // If player has never been rewarded, or their level is higher than last rewarded level
        if (lastRewardedLevel < currentLevel) {
            // Award skips for each level from (lastRewardedLevel + 1) to currentLevel
            int levelsToReward = currentLevel - lastRewardedLevel;
            int skipsToAward = levelsToReward * SKIPS_PER_LEVEL;
            
            if (skipsToAward > 0) {
                skipData.addSkips(skipsToAward);
                skipData.setLastRewardedLevel(currentLevel);
                
                player.sendMessage(Text.literal("🎁 Level Rewards! ").formatted(Formatting.GOLD, Formatting.BOLD)
                    .append(Text.literal("You received ").formatted(Formatting.LIGHT_PURPLE))
                    .append(Text.literal(skipsToAward + " Job Skip" + (skipsToAward == 1 ? "" : "s")).formatted(Formatting.GOLD, Formatting.BOLD))
                    .append(Text.literal(" for reaching level ").formatted(Formatting.LIGHT_PURPLE))
                    .append(Text.literal(String.valueOf(currentLevel)).formatted(Formatting.YELLOW))
                    .append(Text.literal("!").formatted(Formatting.LIGHT_PURPLE)));
                
                LOGGER.info("Awarded {} job skips to player {} for level {}", 
                    skipsToAward, player.getName().getString(), currentLevel);
                
                save();
            }
        }
    }
    
    /**
     * Uses a job skip to complete the current active job without penalty.
     * 
     * @param player The player using the skip
     * @return True if the skip was used successfully, false otherwise
     */
    public boolean useJobSkip(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        
        // Check if player has an active job
        Job activeJob = getActiveJob(player);
        if (activeJob == null) {
            player.sendMessage(Text.literal("You don't have an active job to skip.").formatted(Formatting.RED));
            return false;
        }
        
        // Check if player has skips
        PlayerJobSkipData skipData = getPlayerSkipData(player);
        if (skipData.getSkipCount() <= 0) {
            player.sendMessage(Text.literal("You don't have any job skips available.").formatted(Formatting.RED));
            return false;
        }
        
        // Use the skip
        if (!skipData.useSkip()) {
            player.sendMessage(Text.literal("Failed to use job skip.").formatted(Formatting.RED));
            return false;
        }
        
        // Mark the job as completed (inactive)
        activeJob.setActive(false);
        
        // Generate a replacement job
        List<Job> playerJobsList = playerJobs.getOrDefault(playerUuid, new ArrayList<>());
        PlayerJobLevel jobLevel = getPlayerJobLevel(playerUuid);
        int playerLevel = jobLevel.getLevel();
        
        playerJobsList.remove(activeJob);
        Job newJob = generateUniqueJob(playerJobsList, playerLevel);
        playerJobsList.add(newJob);
        playerJobs.put(playerUuid, playerJobsList);
        
        // Save the changes
        save();
        
        // Notify the player
        player.sendMessage(Text.literal("✓ ").formatted(Formatting.GREEN)
            .append(Text.literal("Job skipped! ").formatted(Formatting.GREEN, Formatting.BOLD))
            .append(Text.literal("A new job has been generated.").formatted(Formatting.WHITE)));
        
        player.sendMessage(Text.literal("Job Skips Remaining: ").formatted(Formatting.GRAY)
            .append(Text.literal(String.valueOf(skipData.getSkipCount())).formatted(Formatting.GOLD)));
        
        LOGGER.info("Player {} used a job skip", player.getName().getString());
        
        return true;
    }
} 