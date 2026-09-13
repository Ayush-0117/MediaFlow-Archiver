package com.mediaflow.archiver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class FileCrawlerService {

    private final IngestionPipelineOrchestrator orchestrator;
    private static final List<String> SUPPORTED_EXTENSIONS = Arrays.asList(
            // Standard Web Images
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp",
            // Apple HEIC
            ".heic", ".heif",
            // Camera RAW Formats (Canon, Nikon, DNG, Sony, etc)
            ".raw", ".cr2", ".nef", ".dng", ".arw",
            // High Resolution Videos
            ".mp4", ".mov", ".mkv", ".avi", ".webm"
    );

    public FileCrawlerService(IngestionPipelineOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public void crawlAndStage(Path rootPath) {
        log.info("Initiating File Crawler on Mount Point: {}", rootPath);

        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // Skip hidden directories and OS system folders
                    String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (dirName.startsWith(".") || dirName.equalsIgnoreCase("System Volume Information") || dirName.equalsIgnoreCase("$RECYCLE.BIN")) {
                        log.debug("Skipping hidden/system directory: {}", dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && file.getFileName() != null) {
                        String fileName = file.getFileName().toString().toLowerCase();
                        
                        // Ignore hidden files (like Mac's .DS_Store or ._photo.jpg forks)
                        if (fileName.startsWith(".")) {
                            return FileVisitResult.CONTINUE;
                        }

                        boolean isSupported = SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);

                        if (isSupported) {
                            log.info("Media File Found: {}", file);
                            // Feed directly into the Master Pipeline Conductor
                            orchestrator.processNewFile(file);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.warn("Cannot read file/directory (Permission denied): {}", file);
                    return FileVisitResult.CONTINUE; // Survive and skip
                }
            });
        } catch (IOException e) {
            log.error("Critical failure while crawling mount point: {}", rootPath, e);
        }
        log.info("File Crawl securely completed for Mount Point: {}", rootPath);
    }
}
