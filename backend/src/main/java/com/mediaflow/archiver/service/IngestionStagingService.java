package com.mediaflow.archiver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;

@Service
@Slf4j
public class IngestionStagingService {

    private final SettingsService settingsService;

    public IngestionStagingService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** 
     * Flag to ensure the /TEMP sweeper runs exactly once, and completes BEFORE 
     * any new files are staged. This prevents the Medium #9 race condition where 
     * the sweeper could delete actively-staging files.
     */
    private volatile boolean bootSweepCompleted = false;

    /**
     * The Quarantine Sweeper.
     * Eradicates physical "Zombie Media". If the PC crashed halfway through chunking a massive 4K video,
     * the unfinished corrupt blob stays physically stuck in /TEMP using gigabytes of SSD space forever.
     * On boot, this perfectly sweeps the vault into the trash.
     * 
     * Called internally at the start of the first staging operation, AFTER Spring context is fully loaded,
     * preventing race conditions with @PostConstruct execution order.
     */
    private synchronized void ensureBootSweepCompleted() {
        if (bootSweepCompleted) return;
        
        Path tempDir = Paths.get(settingsService.getArchivePath(), "TEMP");
        try {
            if (Files.exists(tempDir)) {
                log.warn("Boot Sweep: Cleaning Quarantine /TEMP folder to erase Zombie Media from prior crashes...");
                
                try (java.util.stream.Stream<Path> files = Files.walk(tempDir)) {
                    files.filter(path -> !path.equals(tempDir))
                         .sorted(java.util.Comparator.reverseOrder()) // Delete files before directories
                         .map(Path::toFile)
                         .forEach(java.io.File::delete);
                }
                
                log.info("Quarantine /TEMP vault has been sanitized.");
            }
        } catch (Exception e) {
            log.error("Failed to cleanly sweep the /TEMP staging repository", e);
        }
        
        bootSweepCompleted = true;
    }

    /**
     * Protects the original file by copying it to the staging TEMP directory.
     * Generates a SHA-256 hash incrementally in 8KB chunks during the transfer to save JVM RAM.
     */
    public String stageAndHashFile(Path sourceFilePath) {
        // Guarantee the zombie sweep runs exactly once, before the first file is staged
        ensureBootSweepCompleted();
        
        Path stagingDir = Paths.get(settingsService.getArchivePath(), "TEMP");
        try {
            if (!Files.exists(stagingDir)) {
                Files.createDirectories(stagingDir);
            }

            Path stagedFile = stagingDir.resolve(sourceFilePath.getFileName());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Chunked copy and digest calculation (8KB as per spec)
            try (InputStream is = Files.newInputStream(sourceFilePath);
                 DigestInputStream dis = new DigestInputStream(is, digest);
                 OutputStream os = Files.newOutputStream(stagedFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = dis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }

            // Convert hash bytes to Hex String
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            
            String finalHash = hexString.toString();
            log.info("File securely staged: {} | SHA-256: {}", stagedFile.getFileName(), finalHash);
            
            return finalHash;

        } catch (Exception e) {
            log.error("Critical failure during staging file: {}", sourceFilePath, e);
            throw new RuntimeException("Failed to stage and hash file", e);
        }
    }
}
