package net.currencymod.util;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * Utility class for safe file operations
 */
public class FileUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/FileUtil");
    private static final String BACKUP_DIR = "currency_mod/backups";
    private static final DateTimeFormatter BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Ensure a directory exists, creating it if it doesn't
     *
     * @param directory The directory to check/create
     * @return true if the directory exists or was created, false otherwise
     */
    public static boolean ensureDirectoryExists(File directory) {
        try {
            if (directory.exists()) {
                if (!directory.isDirectory()) {
                    LOGGER.error("Path exists but is not a directory: {}", directory);
                    return false;
                }
                return true;
            }
            
            boolean created = directory.mkdirs();
            if (!created) {
                LOGGER.error("Failed to create directory: {}", directory);
                return false;
            }
            
            LOGGER.info("Created directory: {}", directory);
            return true;
        } catch (Exception e) {
            LOGGER.error("Error ensuring directory exists: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get a file in the server's run directory
     *
     * @param server The Minecraft server instance
     * @param relativePath The relative path from the run directory
     * @return The file object
     */
    public static File getServerFile(MinecraftServer server, String relativePath) {
        try {
            // In 1.21.1, getRunDirectory returns a Path, not a File
            Path runDir = server.getRunDirectory();
            Path filePath = runDir.resolve(relativePath);
            
            // Ensure parent directory exists
            File parentDir = filePath.getParent().toFile();
            ensureDirectoryExists(parentDir);
            
            return filePath.toFile();
        } catch (Exception e) {
            LOGGER.error("Error getting server file: {}", e.getMessage());
            // Fallback to current directory
            return new File(relativePath);
        }
    }

    /**
     * Create a backup of a file
     *
     * @param server The Minecraft server instance
     * @param originalFile The file to backup
     * @return true if backup was successful, false otherwise
     */
    public static boolean backupFile(MinecraftServer server, File originalFile) {
        if (!originalFile.exists() || !originalFile.isFile()) {
            LOGGER.debug("No file to backup at {}", originalFile);
            return false;
        }

        try {
            // In 1.21.1, getRunDirectory returns a Path, not a File
            Path runDir = server.getRunDirectory();
            Path backupDirPath = runDir.resolve(BACKUP_DIR);
            File backupDir = backupDirPath.toFile();
            
            if (!ensureDirectoryExists(backupDir)) {
                LOGGER.error("Could not create backup directory");
                return false;
            }
            
            String timestamp = LocalDateTime.now().format(BACKUP_DATE_FORMAT);
            String fileName = originalFile.getName();
            String backupFileName = fileName + "." + timestamp + ".bak";
            
            File backupFile = new File(backupDir, backupFileName);
            
            Files.copy(originalFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Created backup of {} at {}", originalFile, backupFile);
            
            return true;
        } catch (Exception e) {
            LOGGER.error("Error backing up file {}: {}", originalFile, e.getMessage());
            return false;
        }
    }

    /**
     * Safely write content to a file, creating a backup if the file already exists
     *
     * @param server The Minecraft server instance
     * @param file The file to write to
     * @param content The content to write
     * @return true if write was successful, false otherwise
     */
    public static boolean safeWriteToFile(MinecraftServer server, File file, String content) {
        try {
            // Log the file path we're trying to write to
            LOGGER.info("Attempting to write to file: {}", file.getAbsolutePath());
            
            // Safety check: don't write empty content that would erase existing data
            if ((content == null || content.trim().isEmpty()) && file.exists() && file.length() > 0) {
                LOGGER.warn("Prevented writing empty content to existing file: {}", file);
                return false;
            }
            
            // Verify we have permissions before attempting writes
            File parentDir = file.getParentFile();
            if (parentDir != null) {
                // Log parent directory state for debugging
                if (!parentDir.exists()) {
                    LOGGER.info("Parent directory {} does not exist, attempting to create", parentDir.getAbsolutePath());
                } else if (!parentDir.canWrite()) {
                    LOGGER.warn("Parent directory {} exists but is not writable", parentDir.getAbsolutePath());
                }
                
                // Create all parent directories
                if (!ensureDirectoryExists(parentDir)) {
                    LOGGER.error("Failed to create/access parent directory: {}", parentDir.getAbsolutePath());
                    return false;
                }
            }
            
            // If file exists, create a backup first
            if (file.exists()) {
                if (file.canWrite()) {
                    backupFile(server, file);
                } else {
                    LOGGER.error("Cannot write to existing file: {} (permissions denied)", file.getAbsolutePath());
                    return false;
                }
            }
            
            // Write to a temporary file first for atomic replacement
            File tempFile = new File(file.getAbsolutePath() + ".tmp");
            try {
                // Use FileOutputStream with explicit flush to ensure data is written
                try (FileOutputStream fos = new FileOutputStream(tempFile);
                     OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {
                    writer.write(content);
                    writer.flush();
                    fos.flush();
                    fos.getFD().sync(); // Force synchronization with filesystem
                }
                
                // Verify the temp file was written successfully
                if (!tempFile.exists() || tempFile.length() == 0) {
                    LOGGER.error("Failed to write content to temp file: {}", tempFile);
                    tempFile.delete();
                    return false;
                }
                
                // Rename the temp file to the target file for atomic operation
                if (file.exists()) {
                    if (!file.delete()) {
                        LOGGER.error("Failed to delete existing file during atomic write: {}", file.getAbsolutePath());
                        return false;
                    }
                }
                
                boolean renamed = tempFile.renameTo(file);
                
                if (!renamed) {
                    LOGGER.error("Failed to rename temp file to target file: {} -> {}", tempFile, file);
                    
                    // Attempt a direct copy as fallback
                    try {
                        LOGGER.info("Attempting fallback direct copy method");
                        Files.copy(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        tempFile.delete(); // Clean up the temp file
                        renamed = true;
                    } catch (Exception e) {
                        LOGGER.error("Fallback direct copy also failed: {}", e.getMessage());
                    }
                    
                    if (!renamed) {
                        return false;
                    }
                }
                
                // Log success
                LOGGER.info("Successfully wrote to file {} ({} bytes)", file, file.length());
                return true;
            } finally {
                // Always try to clean up temp file
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error writing to file {}: {}", file, e.getMessage());
            // Print the full stack trace for diagnosis
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            LOGGER.error("Stack trace: {}", sw.toString());
            return false;
        }
    }

    /**
     * Safely read content from a file
     *
     * @param file The file to read from
     * @return The file content, or null if the file does not exist or cannot be read
     */
    public static String safeReadFromFile(File file) {
        if (!file.exists() || !file.isFile()) {
            LOGGER.debug("No file to read at {}", file);
            return null;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        } catch (Exception e) {
            LOGGER.error("Error reading from file {}: {}", file, e.getMessage());
            return null;
        }
    }

    /**
     * Check if a file is accessible for reading and/or writing
     *
     * @param file The file to check
     * @param needWrite Whether write permission is needed
     * @return true if the file is accessible, false otherwise
     */
    public static boolean isFileAccessible(File file, boolean needWrite) {
        try {
            // Check if parent directory exists and is accessible
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                LOGGER.debug("Parent directory does not exist for {}", file);
                return false;
            }
            
            // If file exists, check if it's readable and writable if needed
            if (file.exists()) {
                if (!file.canRead()) {
                    LOGGER.debug("File {} is not readable", file);
                    return false;
                }
                
                if (needWrite && !file.canWrite()) {
                    LOGGER.debug("File {} is not writable", file);
                    return false;
                }
                
                return true;
            }
            
            // If file doesn't exist but we need to write, check if we can create it
            if (needWrite) {
                if (parentDir != null && !parentDir.canWrite()) {
                    LOGGER.debug("Cannot write to parent directory of {}", file);
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            LOGGER.error("Error checking file accessibility {}: {}", file, e.getMessage());
            return false;
        }
    }
} 