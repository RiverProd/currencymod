package net.currencymod.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.currencymod.CurrencyMod;
import net.currencymod.economy.EconomyManager;
import net.currencymod.auction.AuctionManager;
import net.currencymod.jobs.JobManager;
import net.currencymod.jobs.MarketplaceManager;
import net.currencymod.plots.PlotManager;
import net.currencymod.services.ServiceManager;
import net.currencymod.shop.ShopAccess;
import net.currencymod.shop.ShopManager;
import net.currencymod.shop.ShopTransactionManager;
import net.currencymod.util.FileUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Centralized data management system for the Currency Mod.
 * This class handles:
 * 1. Scheduled regular auto-saves
 * 2. Backup creation
 * 3. Centralized data loading
 * 4. Data validation
 * 5. Error recovery
 */
public class DataManager {
    // Singleton instance
    private static DataManager instance;
    
    // Data directories and settings
    private static final String DATA_DIR = "currency_mod";
    private static final String BACKUP_DIR = DATA_DIR + "/backups";
    private static final String LOG_FILE = DATA_DIR + "/data-operations.log";
    private static final long AUTO_SAVE_INTERVAL = 60 * 60 * 1000; // 60 minutes in milliseconds
    private static final int MAX_BACKUPS = 3; // Maximum number of backups to keep
    
    // Internal state tracking
    private final Timer autoSaveTimer = new Timer("CurrencyMod-AutoSave", true);
    private MinecraftServer server;
    private final AtomicBoolean isSaving = new AtomicBoolean(false);
    private long lastSaveTime = 0;
    private boolean shutdownInProgress = false;
    
    // Dedicated logger for data operations
    private final Logger dataLogger = LoggerFactory.getLogger("CurrencyMod-DataManager");
    private boolean loggerInitialized = false;
    
    private DataManager() {
        // Constructor is private for singleton pattern
    }
    
    /**
     * Get the singleton instance of the DataManager
     */
    public static DataManager getInstance() {
        if (instance == null) {
            instance = new DataManager();
        }
        return instance;
    }
    
    /**
     * Initialize the data manager with a server instance
     */
    public void initialize(MinecraftServer server) {
        if (server == null) {
            CurrencyMod.LOGGER.error("Cannot initialize DataManager: server is null");
            return;
        }
        
        this.server = server;
        
        // Initialize logger
        initializeLogger();
        
        // Ensure data directories exist
        ensureDirectoriesExist();
        
        // Load all data
        loadAllData();
        
        // Schedule regular auto-saves
        scheduleAutoSave();
        
        CurrencyMod.LOGGER.info("DataManager initialized with auto-save interval of {} minutes", 
            AUTO_SAVE_INTERVAL / (60 * 1000));
    }
    
    /**
     * Initialize the data logger
     */
    private void initializeLogger() {
        if (loggerInitialized) {
            return;
        }
        
        try {
            // For SLF4J, we don't need to configure file handlers manually
            // We only need to make sure the directory exists for logback to write to
            Path runDirPath = server.getRunDirectory();
            File dataDir = runDirPath.resolve(DATA_DIR).toFile();
            FileUtil.ensureDirectoryExists(dataDir);
            
            loggerInitialized = true;
            dataLogger.info("Data logger initialized at {}", new Date());
        } catch (Exception e) {
            CurrencyMod.LOGGER.error("Failed to set up data operations logger", e);
        }
    }
    
    /**
     * Ensure all required directories exist
     */
    private void ensureDirectoriesExist() {
        if (server == null) {
            dataLogger.warn("Cannot create directories: server is null");
            return;
        }
        
        try {
            // Get the server's run directory as Path
            Path runDirPath = server.getRunDirectory();
            
            // Create main data directory
            File dataDir = runDirPath.resolve(DATA_DIR).toFile();
            FileUtil.ensureDirectoryExists(dataDir);
            
            // Create backup directory
            File backupDir = runDirPath.resolve(BACKUP_DIR).toFile();
            FileUtil.ensureDirectoryExists(backupDir);
            
            // We only need to create this one additional directory, the rest are not used
            File backupDataDir = new File(backupDir, "data");
            FileUtil.ensureDirectoryExists(backupDataDir);
        } catch (Exception e) {
            dataLogger.error("Error creating required directories: {}", e.getMessage());
        }
    }
    
    /**
     * Schedule regular auto-saves
     */
    private void scheduleAutoSave() {
        autoSaveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    if (!shutdownInProgress) {
                        saveAllData("auto-save");
                    }
                } catch (Exception e) {
                    CurrencyMod.LOGGER.error("Error during auto-save", e);
                    dataLogger.error("Auto-save error: {}", e.getMessage());
                }
            }
        }, AUTO_SAVE_INTERVAL, AUTO_SAVE_INTERVAL);
        
        CurrencyMod.LOGGER.info("Auto-save scheduled every {} minutes", AUTO_SAVE_INTERVAL / (60 * 1000));
        dataLogger.info("Auto-save scheduled every {} minutes", AUTO_SAVE_INTERVAL / (60 * 1000));
    }
    
    /**
     * Load all data from disk
     */
    public void loadAllData() {
        if (server == null) {
            CurrencyMod.LOGGER.error("Cannot load data: server is null");
            return;
        }
        
        try {
            // Load various data types
            // Try to handle errors for individual data types gracefully
            boolean criticalError = false;
            
            try {
                // Economy data
                EconomyManager economyManager = CurrencyMod.getEconomyManager();
                if (economyManager != null) {
                    economyManager.loadData(server);
                    CurrencyMod.LOGGER.info("Loaded economy data");
                }
            } catch (Exception e) {
                CurrencyMod.LOGGER.error("Failed to load economy data", e);
                criticalError = true;
            }
            
            try {
                // Load shop data for all worlds
                for (ServerWorld world : server.getWorlds()) {
                    ShopManager shopManager = ((ShopAccess) world).getShopManager();
                    if (shopManager != null) {
                        shopManager.loadData(server);
                        CurrencyMod.LOGGER.info("Loaded shop data for world: {}", world.getRegistryKey().getValue());
                    }
                }
            } catch (Exception e) {
                CurrencyMod.LOGGER.error("Failed to load shop data", e);
                criticalError = shouldAttemptRecovery(e);
            }
            
            try {
                // Shop transactions
                ShopTransactionManager.getInstance().loadData(server);
                CurrencyMod.LOGGER.info("Loaded shop transaction data");
            } catch (Exception e) {
                CurrencyMod.LOGGER.error("Failed to load shop transaction data", e);
            }
            
            try {
                // Auction data
                AuctionManager.getInstance().loadPendingItems(server);
                CurrencyMod.LOGGER.info("Loaded auction data");
            } catch (Exception e) {
                CurrencyMod.LOGGER.error("Failed to load auction data", e);
            }
            
            try {
                // Job data
                JobManager.getInstance().load();
                CurrencyMod.LOGGER.info("Loaded job data");
            } catch (Exception e) {
                CurrencyMod.LOGGER.error("Failed to load job data", e);
            }
            
            try {
                // Plot data
                PlotManager.getInstance().loadData(server);
                CurrencyMod.LOGGER.info("Loaded plot data");
            } catch (Exception e) {
                CurrencyMod.LOGGER.error("Failed to load plot data", e);
            }
            
            try {
                // Marketplace data
                MarketplaceManager.getInstance().load(server);
                CurrencyMod.LOGGER.info("Loaded marketplace data");
            } catch (Exception e) {
                CurrencyMod.LOGGER.error("Failed to load marketplace data", e);
            }
            
            try {
                // Service data
                ServiceManager.getInstance().loadData(server);
                CurrencyMod.LOGGER.info("Loaded service data");
            } catch (Exception e) {
                CurrencyMod.LOGGER.error("Failed to load service data", e);
            }
            
            // If a critical error occurred, attempt recovery
            if (criticalError) {
                attemptRecoveryFromBackup();
            }
            
            CurrencyMod.LOGGER.info("All data loaded successfully");
            lastSaveTime = System.currentTimeMillis();
        } catch (Exception e) {
            CurrencyMod.LOGGER.error("Error loading data", e);
            dataLogger.error("Error loading data: {}", e.getMessage());
        }
    }
    
    /**
     * Save all data to disk
     */
    public void saveAllData(String reason) {
        if (server == null) {
            CurrencyMod.LOGGER.error("Cannot save data: server is null");
            return;
        }
        
        // Prevent concurrent saves
        if (!isSaving.compareAndSet(false, true)) {
            CurrencyMod.LOGGER.warn("Save already in progress, skipping new save request for reason: {}", reason);
            return;
        }
        
        try {
            CurrencyMod.LOGGER.info("Saving all data (reason: {})", reason);
            dataLogger.info("Saving all data (reason: {})", reason);
            
            // Track which worlds we've saved shops for in this batch
            Set<String> savedWorldIds = new HashSet<>();
            
            try {
                // Economy data
                EconomyManager economyManager = CurrencyMod.getEconomyManager();
                if (economyManager != null) {
                    economyManager.saveData(server);
                    CurrencyMod.LOGGER.info("Saved economy data");
                }
            } catch (Exception e) {
                CurrencyMod.LOGGER.error("Failed to save economy data", e);
            }
            
            try {
                // Save shop data for all worlds
                for (ServerWorld world : server.getWorlds()) {
                    String worldId = ShopManager.getWorldId(world);
                    savedWorldIds.add(worldId);
                    
                    ShopManager shopManager = ((ShopAccess) world).getShopManager();
                    if (shopManager != null) {
                        shopManager.saveData(server);
                        CurrencyMod.LOGGER.info("Saved shop data for world: {}", worldId);
                    }
                }
            } catch (Exception e) {
                CurrencyMod.LOGGER.error("Failed to save shop data", e);
            }
            
            // Store the world IDs we've saved in this batch
            if (shutdownInProgress) {
                CurrencyMod.LOGGER.info("Marked {} worlds as saved during shutdown", savedWorldIds.size());
            }
            
            try {
                // Shop transactions
                ShopTransactionManager.getInstance().saveData(server);
                CurrencyMod.LOGGER.info("Saved shop transaction data");
            } catch (Exception e) {
                CurrencyMod.LOGGER.error("Failed to save shop transaction data", e);
            }
            
            try {
                // Auction data
                AuctionManager.getInstance().savePendingItems(server);
                CurrencyMod.LOGGER.info("Saved auction data");
            } catch (Exception e) {
                CurrencyMod.LOGGER.error("Failed to save auction data", e);
            }
            
            try {
                // Job data
                JobManager.getInstance().save();
                CurrencyMod.LOGGER.info("Saved job data");
            } catch (Exception e) {
                CurrencyMod.LOGGER.error("Failed to save job data", e);
            }
            
            try {
                // Plot data
                PlotManager.getInstance().saveData(server);
                CurrencyMod.LOGGER.info("Saved plot data");
            } catch (Exception e) {
                CurrencyMod.LOGGER.error("Failed to save plot data", e);
            }
            
            try {
                // Marketplace data
                MarketplaceManager.getInstance().save(server);
                CurrencyMod.LOGGER.info("Saved marketplace data");
            } catch (Exception e) {
                CurrencyMod.LOGGER.error("Failed to save marketplace data", e);
            }
            
            try {
                // Service data
                ServiceManager.getInstance().saveData();
                CurrencyMod.LOGGER.info("Saved service data");
            } catch (Exception e) {
                CurrencyMod.LOGGER.error("Failed to save service data", e);
            }
            
            lastSaveTime = System.currentTimeMillis();
            CurrencyMod.LOGGER.info("All data saved successfully");
            dataLogger.info("All data saved successfully");
        } catch (Exception e) {
            CurrencyMod.LOGGER.error("Error saving data", e);
            dataLogger.error("Error saving data: {}", e.getMessage());
        } finally {
            isSaving.set(false);
        }
    }
    
    /**
     * Create a backup of all data
     */
    public void createBackup() {
        if (server == null) {
            dataLogger.warn("Cannot create backup: server is null");
            return;
        }
        
        try {
            // Get the data directory and backup root as Path
            Path runDirPath = server.getRunDirectory();
            File dataDir = runDirPath.resolve(DATA_DIR).toFile();
            File backupRoot = runDirPath.resolve(BACKUP_DIR).toFile();
            
            // Make sure directories exist
            if (!FileUtil.ensureDirectoryExists(dataDir) || !FileUtil.ensureDirectoryExists(backupRoot)) {
                dataLogger.warn("Required directories don't exist and couldn't be created");
                return;
            }
            
            // Generate timestamp for this backup
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File backupDir = new File(backupRoot, timestamp);
            
            if (!FileUtil.ensureDirectoryExists(backupDir)) {
                dataLogger.warn("Could not create backup directory: {}", backupDir.getAbsolutePath());
                return;
            }
            
            // Copy all data to backup directory
            copyDirectoryForBackup(dataDir, backupDir);
            
            dataLogger.info("Successfully created backup at {}", timestamp);
            
            // Clean up old backups if there are too many
            cleanupOldBackups();
        } catch (Exception e) {
            dataLogger.error("Error creating backup: {}", e.getMessage());
        }
    }
    
    /**
     * Recursively copy a directory for backup purposes
     */
    private void copyDirectoryForBackup(File sourceDir, File destDir) throws IOException {
        // Skip the backups directory itself
        if (sourceDir.getAbsolutePath().contains(BACKUP_DIR)) {
            return;
        }
        
        // Skip log files
        if (sourceDir.getAbsolutePath().endsWith(".log")) {
            return;
        }
        
        // If source is a directory, create destination directory and copy contents
        if (sourceDir.isDirectory()) {
            // Skip certain directories
            String dirName = sourceDir.getName();
            if (dirName.equals("backups") || dirName.equals("logs") || dirName.equals("crash-reports")) {
                return;
            }
            
            // Create destination directory
            FileUtil.ensureDirectoryExists(destDir);
            
            // List all the files/directories
            String[] items = sourceDir.list();
            if (items != null) {
                for (String item : items) {
                    File srcFile = new File(sourceDir, item);
                    File destFile = new File(destDir, item);
                    
                    copyDirectoryForBackup(srcFile, destFile);
                }
            }
        } 
        // If source is a file, copy it
        else if (sourceDir.isFile() && (sourceDir.getName().endsWith(".json") || 
                                        sourceDir.getName().endsWith(".dat"))) {
            Files.copy(sourceDir.toPath(), destDir.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
    /**
     * Delete old backups to prevent disk space issues
     */
    private void cleanupOldBackups() {
        try {
            // Get backup root directory
            Path runDirPath = server.getRunDirectory();
            File backupRoot = runDirPath.resolve(BACKUP_DIR).toFile();
            
            if (!backupRoot.exists() || !backupRoot.isDirectory()) {
                return;
            }
            
            // List all backup directories
            File[] backups = backupRoot.listFiles();
            if (backups == null || backups.length <= MAX_BACKUPS) {
                return;
            }
            
            // Sort by last modified (oldest first)
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
            
            // Delete oldest backups until we're at the limit
            int toDelete = backups.length - MAX_BACKUPS;
            for (int i = 0; i < toDelete; i++) {
                deleteDirectory(backups[i]);
                dataLogger.info("Deleted old backup: " + backups[i].getName());
            }
        } catch (Exception e) {
            dataLogger.warn("Error cleaning up old backups: " + e.getMessage());
        }
    }
    
    /**
     * Delete a directory and all its contents recursively
     */
    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    deleteDirectory(new File(dir, child));
                }
            }
        }
        dir.delete();
    }
    
    /**
     * Check if a data error is severe enough to warrant recovery
     */
    private boolean shouldAttemptRecovery(Exception e) {
        if (e == null) {
            return false;
        }
        
        // Consider certain error types as reasons to recover
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return message.contains("parse") || 
               message.contains("corrupt") || 
               message.contains("invalid") ||
               e instanceof java.io.FileNotFoundException;
    }
    
    /**
     * Attempt to recover data from the most recent backup
     */
    private void attemptRecoveryFromBackup() {
        dataLogger.error("Attempting to recover from most recent backup...");
        
        try {
            // Get backup root directory
            Path runDirPath = server.getRunDirectory();
            File backupRoot = runDirPath.resolve(BACKUP_DIR).toFile();
            
            if (!backupRoot.exists() || !backupRoot.isDirectory()) {
                dataLogger.error("No backup directory found at " + backupRoot.getAbsolutePath());
                return;
            }
            
            // List all backup directories
            File[] backups = backupRoot.listFiles();
            if (backups == null || backups.length == 0) {
                dataLogger.error("No backups found in " + backupRoot.getAbsolutePath());
                return;
            }
            
            // Sort by last modified (newest first)
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed());
            
            // Get most recent backup
            File mostRecentBackup = backups[0];
            dataLogger.info("Attempting recovery from backup: " + mostRecentBackup.getName());
            
            // Get data directory
            File dataDir = runDirPath.resolve(DATA_DIR).toFile();
            
            // Perform recovery
            restoreFromBackupDirectory(mostRecentBackup, dataDir);
            
            dataLogger.info("Recovery successful! Data restored from backup: " + mostRecentBackup.getName());
        } catch (Exception e) {
            dataLogger.error("Recovery attempt failed: " + e.getMessage());
        }
    }
    
    /**
     * Recursively restore files from a backup directory
     */
    private void restoreFromBackupDirectory(File backupDir, File targetDir) throws IOException {
        if (!backupDir.exists() || !backupDir.isDirectory()) {
            return;
        }
        
        // Create the target directory if it doesn't exist
        FileUtil.ensureDirectoryExists(targetDir);
        
        // Copy all files
        File[] files = backupDir.listFiles();
        if (files != null) {
            for (File file : files) {
                File targetFile = new File(targetDir, file.getName());
                
                if (file.isDirectory()) {
                    // Recursively restore subdirectory
                    restoreFromBackupDirectory(file, targetFile);
                } else if (file.isFile()) {
                    // Copy the file
                    Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
    
    /**
     * Check if the data manager is in shutdown mode
     * @return true if shutdown is in progress
     */
    public boolean isShuttingDown() {
        return shutdownInProgress;
    }

    /**
     * Handle shutdown - ensure all data is saved
     */
    public void onShutdown() {
        // If already shutting down, don't run this method again
        if (shutdownInProgress) {
            CurrencyMod.LOGGER.warn("DataManager.onShutdown() called more than once, ignoring");
            return;
        }
        
        shutdownInProgress = true;
        
        // Cancel auto-save timer
        autoSaveTimer.cancel();
        
        // Perform one final save
        if (server != null) {
            // Explicitly save each data manager - to ensure none are missed
            try {
                CurrencyMod.LOGGER.info("Performing final save of all data on server shutdown");
                
                // First ensure our core components are saved
                CurrencyMod.getEconomyManager().saveData(server);
                CurrencyMod.LOGGER.info("Saved economy data on shutdown");
                
                // Save shop transactions
                ShopTransactionManager.getInstance().saveData(server);
                CurrencyMod.LOGGER.info("Saved shop transaction data on shutdown");
                
                // Save auction pending items 
                AuctionManager.getInstance().savePendingItems(server);
                CurrencyMod.LOGGER.info("Saved auction pending items on shutdown");
                
                // Save job data
                JobManager.getInstance().save();
                CurrencyMod.LOGGER.info("Saved job data on shutdown");
                
                // Save plot data
                PlotManager.getInstance().saveData(server);
                CurrencyMod.LOGGER.info("Saved plot data on shutdown");
                
                // Save marketplace data
                MarketplaceManager.getInstance().save(server);
                CurrencyMod.LOGGER.info("Saved marketplace data on shutdown");
                
                // Save service data
                ServiceManager.getInstance().saveData();
                CurrencyMod.LOGGER.info("Saved service data on shutdown");
                
                // Now call the general saveAllData method to ensure all shops are saved too
                saveAllData("server-shutdown");
            } catch (Exception e) {
                CurrencyMod.LOGGER.error("Error during shutdown save", e);
            }
            
            // Make sure plot manager is shut down properly
            try {
                PlotManager.getInstance().shutdown(server);
            } catch (Exception e) {
                CurrencyMod.LOGGER.error("Error shutting down PlotManager", e);
            }
        }
        
        CurrencyMod.LOGGER.info("DataManager shutdown completed");
        if (loggerInitialized) {
            dataLogger.info("DataManager shutdown completed at " + new Date());
        }
    }
    
    /**
     * Get the current status of the data system
     */
    public String getStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Data Manager Status:\n");
        status.append("  Auto-save interval: ").append(AUTO_SAVE_INTERVAL / (60 * 1000)).append(" minutes\n");
        status.append("  Last save: ").append(lastSaveTime > 0 ? new Date(lastSaveTime) : "Never").append("\n");
        status.append("  Currently saving: ").append(isSaving.get()).append("\n");
        
        // Count backups
        Path runDirPath = server.getRunDirectory();
        File backupRoot = runDirPath.resolve(BACKUP_DIR).toFile();
        File[] backups = backupRoot.listFiles(file -> file.isDirectory());
        int backupCount = backups != null ? backups.length : 0;
        status.append("  Backups available: ").append(backupCount);
        
        return status.toString();
    }
    
    /**
     * Get time since last save in seconds
     */
    public int getSecondsSinceLastSave() {
        return (int) ((System.currentTimeMillis() - lastSaveTime) / 1000);
    }
    
    /**
     * Force an immediate save of all data
     */
    public void forceSave(String reason) {
        saveAllData("manual-" + reason);
    }
    
    /**
     * Restore data from a specific backup
     * 
     * @param backupName The name of the backup to restore (e.g., "backup_2023-01-01_12-00-00")
     * @return true if successful, false otherwise
     */
    public boolean restoreFromBackup(String backupName) {
        if (server == null) {
            dataLogger.warn("Cannot restore backup: server is null");
            return false;
        }
        
        if (backupName == null || backupName.trim().isEmpty()) {
            dataLogger.warn("Invalid backup name: " + backupName);
            return false;
        }
        
        try {
            // Get backup directory
            Path runDirPath = server.getRunDirectory();
            File backupDir = runDirPath.resolve(BACKUP_DIR + "/" + backupName).toFile();
            
            if (!backupDir.exists() || !backupDir.isDirectory()) {
                dataLogger.warn("Backup does not exist: " + backupName);
                return false;
            }
            
            // Force a save before overwriting
            saveAllData("Pre-restore backup");
            
            // Create a backup of the current data
            createBackup();
            
            // Get data directory
            File dataDir = runDirPath.resolve(DATA_DIR).toFile();
            
            // Perform restore
            restoreFromBackupDirectory(backupDir, dataDir);
            
            dataLogger.info("Successfully restored from backup: " + backupName);
            
            return true;
        } catch (Exception e) {
            dataLogger.error("Error restoring from backup: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get a list of available backups
     * 
     * @return Array of backup names (directory names)
     */
    public String[] getAvailableBackups() {
        try {
            // Get backup root directory
            Path runDirPath = server.getRunDirectory();
            File backupRoot = runDirPath.resolve(BACKUP_DIR).toFile();
            
            if (!backupRoot.exists() || !backupRoot.isDirectory()) {
                return new String[0];
            }
            
            // List all backup directories
            File[] backups = backupRoot.listFiles();
            if (backups == null || backups.length == 0) {
                return new String[0];
            }
            
            // Sort by last modified (newest first)
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed());
            
            // Extract names
            String[] backupNames = new String[backups.length];
            for (int i = 0; i < backups.length; i++) {
                backupNames[i] = backups[i].getName();
            }
            
            return backupNames;
        } catch (Exception e) {
            dataLogger.warn("Error getting available backups: {}", e.getMessage());
            return new String[0];
        }
    }
} 