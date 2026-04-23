package net.currencymod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.currencymod.data.DataManager;
import net.currencymod.util.FileUtil;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Command for testing data storage functionality
 * This is intended for diagnostic purposes only
 */
public class TestDataCommand {
    
    /**
     * Register the command
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            literal("testdata")
                .requires(source -> source.hasPermissionLevel(4)) // Operator only
                .executes(TestDataCommand::execute)
                .then(literal("checkpaths")
                    .executes(TestDataCommand::checkPaths)
                )
                .then(literal("filetest")
                    .executes(TestDataCommand::fileTest)
                )
                .then(literal("checkpermissions")
                    .executes(TestDataCommand::checkPermissions)
                )
        );
    }
    
    /**
     * Execute the base command - shows available subcommands
     */
    private static int execute(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        source.sendFeedback(() -> Text.literal("🔍 Data Storage Testing Tool")
            .formatted(Formatting.AQUA, Formatting.BOLD), false);
            
        source.sendFeedback(() -> Text.literal("\nAvailable tests:")
            .formatted(Formatting.WHITE), false);
            
        source.sendFeedback(() -> Text.literal(" • /testdata checkpaths")
            .formatted(Formatting.YELLOW)
            .append(Text.literal(" - Check critical file paths").formatted(Formatting.GRAY)), false);
            
        source.sendFeedback(() -> Text.literal(" • /testdata filetest")
            .formatted(Formatting.YELLOW)
            .append(Text.literal(" - Test file write/read operations").formatted(Formatting.GRAY)), false);
            
        source.sendFeedback(() -> Text.literal(" • /testdata checkpermissions")
            .formatted(Formatting.YELLOW)
            .append(Text.literal(" - Check directory and file permissions").formatted(Formatting.GRAY)), false);
            
        return 1;
    }
    
    /**
     * Check critical file paths in the mod
     */
    private static int checkPaths(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        MinecraftServer server = source.getServer();
        
        source.sendFeedback(() -> Text.literal("🔍 Checking critical file paths...")
            .formatted(Formatting.YELLOW, Formatting.BOLD), true);
            
        // Get the server run directory
        Path runDir = server.getRunDirectory();
        
        source.sendFeedback(() -> Text.literal("Server Run Directory: ")
            .append(Text.literal(runDir.toString()).formatted(Formatting.AQUA)), false);
            
        // Check important directories
        checkDirectory(source, runDir.toFile(), "currency_mod");
        checkDirectory(source, runDir.toFile(), "currency_mod/economy");
        checkDirectory(source, runDir.toFile(), "currency_mod/shops");
        checkDirectory(source, runDir.toFile(), "currency_mod/backups");
        checkDirectory(source, runDir.toFile(), "currency_mod/jobs");
        checkDirectory(source, runDir.toFile(), "currency_mod/plots");
        
        // Check important files
        checkFile(source, runDir.toFile(), "currency_mod/economy.json");
        checkFile(source, runDir.toFile(), "currency_mod/shops.json");
        checkFile(source, runDir.toFile(), "currency_mod/auction_pending_items.json");
        checkFile(source, runDir.toFile(), "currency_mod/job_templates.json");
        checkFile(source, runDir.toFile(), "currency_mod/jobs.json");
        checkFile(source, runDir.toFile(), "currency_mod/plots.json");
        checkFile(source, runDir.toFile(), "currency_mod/shop_transactions.json");
        checkFile(source, runDir.toFile(), "currency_mod/marketplace.json");
        
        return 1;
    }
    
    /**
     * Check if a directory exists and report to user
     */
    private static void checkDirectory(ServerCommandSource source, File baseDir, String relativePath) {
        File dir = new File(baseDir, relativePath);
        
        if (dir.exists() && dir.isDirectory()) {
            source.sendFeedback(() -> Text.literal("✅ Directory exists: ")
                .append(Text.literal(relativePath).formatted(Formatting.GREEN)), false);
        } else if (dir.exists() && !dir.isDirectory()) {
            source.sendFeedback(() -> Text.literal("❌ Path exists but is not a directory: ")
                .append(Text.literal(relativePath).formatted(Formatting.RED)), false);
        } else {
            source.sendFeedback(() -> Text.literal("❌ Directory does not exist: ")
                .append(Text.literal(relativePath).formatted(Formatting.RED)), false);
        }
    }
    
    /**
     * Check if a file exists and report to user
     */
    private static void checkFile(ServerCommandSource source, File baseDir, String relativePath) {
        File file = new File(baseDir, relativePath);
        
        if (file.exists() && file.isFile()) {
            source.sendFeedback(() -> Text.literal("✅ File exists: ")
                .append(Text.literal(relativePath).formatted(Formatting.GREEN))
                .append(Text.literal(" (" + (file.length() / 1024) + " KB)").formatted(Formatting.GRAY)), false);
        } else if (file.exists() && !file.isFile()) {
            source.sendFeedback(() -> Text.literal("❌ Path exists but is not a file: ")
                .append(Text.literal(relativePath).formatted(Formatting.RED)), false);
        } else {
            source.sendFeedback(() -> Text.literal("⚠️ File does not exist: ")
                .append(Text.literal(relativePath).formatted(Formatting.GOLD)), false);
        }
    }
    
    /**
     * Test file write and read operations
     */
    private static int fileTest(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        MinecraftServer server = source.getServer();
        
        source.sendFeedback(() -> Text.literal("🔍 Testing file operations...")
            .formatted(Formatting.YELLOW, Formatting.BOLD), true);
            
        // Generate a unique test filename
        String testFileName = "currency_mod/test_" + UUID.randomUUID() + ".txt";
        String testContent = "This is a test file created at " + System.currentTimeMillis() + "\n" +
                             "It should be automatically deleted after the test.";
        
        // Create test file using FileUtil
        Path runDirPath = server.getRunDirectory();
        File testFile = runDirPath.resolve(testFileName).toFile();
        
        try {
            // Test parent directory creation
            File parent = testFile.getParentFile();
            if (!parent.exists()) {
                boolean created = parent.mkdirs();
                source.sendFeedback(() -> Text.literal(created ? 
                    "✅ Created parent directory: " : 
                    "❌ Failed to create parent directory: ")
                    .append(Text.literal(parent.toString())
                    .formatted(created ? Formatting.GREEN : Formatting.RED)), false);
            }
            
            // Test file writing
            boolean writeSuccess = FileUtil.safeWriteToFile(server, testFile, testContent);
            
            if (writeSuccess) {
                source.sendFeedback(() -> Text.literal("✅ Successfully wrote test file: ")
                    .append(Text.literal(testFileName).formatted(Formatting.GREEN)), false);
                
                // Test file reading
                String readContent = FileUtil.safeReadFromFile(testFile);
                boolean readSuccess = readContent != null && readContent.trim().equals(testContent);
                
                source.sendFeedback(() -> Text.literal(readSuccess ?
                    "✅ Successfully read test file with matching content" :
                    "❌ Failed to read test file or content did not match")
                    .formatted(readSuccess ? Formatting.GREEN : Formatting.RED), false);
                
                // Delete test file
                boolean deleteSuccess = testFile.delete();
                source.sendFeedback(() -> Text.literal(deleteSuccess ?
                    "✅ Successfully deleted test file" :
                    "❌ Failed to delete test file")
                    .formatted(deleteSuccess ? Formatting.GREEN : Formatting.RED), false);
            } else {
                source.sendFeedback(() -> Text.literal("❌ Failed to write test file: ")
                    .append(Text.literal(testFileName).formatted(Formatting.RED)), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("❌ Exception during file test: ")
                .append(Text.literal(e.getMessage()).formatted(Formatting.RED)), false);
            
            try {
                // Attempt to clean up
                if (testFile.exists()) {
                    testFile.delete();
                }
            } catch (Exception ignored) {
                // Ignore cleanup errors
            }
        }
        
        return 1;
    }
    
    /**
     * Check file and directory permissions
     */
    private static int checkPermissions(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        MinecraftServer server = source.getServer();
        
        source.sendFeedback(() -> Text.literal("🔍 Checking file permissions...")
            .formatted(Formatting.YELLOW, Formatting.BOLD), true);
            
        // Get the server run directory
        Path runDirPath = server.getRunDirectory();
        File runDir = runDirPath.toFile();
        
        // Check run directory
        checkDirectoryPermissions(source, runDir, "Server Run Directory");
        
        // Check important directories
        File currencyModDir = new File(runDir, "currency_mod");
        checkDirectoryPermissions(source, currencyModDir, "Currency Mod Directory");
        
        if (currencyModDir.exists()) {
            File economyDir = new File(currencyModDir, "economy");
            checkDirectoryPermissions(source, economyDir, "Economy Data Directory");
            
            File shopsDir = new File(currencyModDir, "shops");
            checkDirectoryPermissions(source, shopsDir, "Shops Data Directory");
            
            File backupsDir = new File(currencyModDir, "backups");
            checkDirectoryPermissions(source, backupsDir, "Backups Directory");
            
            // Check important files
            File economyFile = new File(runDir, "currency_mod/economy.json");
            testFilePermissions(source, economyFile, "Economy Data File");
            
            File shopsFile = new File(runDir, "currency_mod/shops.json");
            testFilePermissions(source, shopsFile, "Shops Data File");
        }
        
        return 1;
    }
    
    /**
     * Test and report directory permissions
     */
    private static void checkDirectoryPermissions(ServerCommandSource source, File dir, String description) {
        if (!dir.exists()) {
            source.sendFeedback(() -> Text.literal("⚠️ " + description + " does not exist: ")
                .append(Text.literal(dir.toString()).formatted(Formatting.GOLD)), false);
            return;
        }
        
        if (!dir.isDirectory()) {
            source.sendFeedback(() -> Text.literal("❌ " + description + " exists but is not a directory: ")
                .append(Text.literal(dir.toString()).formatted(Formatting.RED)), false);
            return;
        }
        
        boolean canRead = dir.canRead();
        boolean canWrite = dir.canWrite();
        boolean canExecute = dir.canExecute();
        
        source.sendFeedback(() -> Text.literal((canRead && canWrite && canExecute) ? "✅ " : "❌ ")
            .append(Text.literal(description + ": ").formatted(Formatting.WHITE))
            .append(Text.literal(dir.toString()).formatted(Formatting.AQUA))
            .append(Text.literal(" [Read: " + (canRead ? "Yes" : "No") + 
                              ", Write: " + (canWrite ? "Yes" : "No") + 
                              ", Execute: " + (canExecute ? "Yes" : "No") + "]")
                  .formatted(canRead && canWrite && canExecute ? Formatting.GREEN : Formatting.RED)), false);
    }
    
    /**
     * Test and report file permissions
     */
    private static void testFilePermissions(ServerCommandSource source, File file, String description) {
        if (!file.exists()) {
            source.sendFeedback(() -> Text.literal("⚠️ " + description + " does not exist: ")
                .append(Text.literal(file.toString()).formatted(Formatting.GOLD)), false);
            return;
        }
        
        if (!file.isFile()) {
            source.sendFeedback(() -> Text.literal("❌ " + description + " exists but is not a file: ")
                .append(Text.literal(file.toString()).formatted(Formatting.RED)), false);
            return;
        }
        
        boolean canRead = file.canRead();
        boolean canWrite = file.canWrite();
        
        source.sendFeedback(() -> Text.literal((canRead && canWrite) ? "✅ " : "❌ ")
            .append(Text.literal(description + ": ").formatted(Formatting.WHITE))
            .append(Text.literal(file.toString()).formatted(Formatting.AQUA))
            .append(Text.literal(" [Read: " + (canRead ? "Yes" : "No") + 
                              ", Write: " + (canWrite ? "Yes" : "No") + "]")
                  .formatted(canRead && canWrite ? Formatting.GREEN : Formatting.RED)), false);
    }
} 