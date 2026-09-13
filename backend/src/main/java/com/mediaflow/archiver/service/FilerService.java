package com.mediaflow.archiver.service;

import com.mediaflow.archiver.entity.MediaAsset;
import com.mediaflow.archiver.entity.MetadataSpec;
import com.mediaflow.archiver.event.AiInferenceWakeEvent;
import com.mediaflow.archiver.repository.MediaAssetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class FilerService {

    private final MediaAssetRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final SettingsService settingsService;

    public FilerService(MediaAssetRepository repository, ApplicationEventPublisher eventPublisher, SettingsService settingsService) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.settingsService = settingsService;
    }

    public Path archiveOriginalFile(Path stagedTempFile, String sha256, MetadataSpec metadata) {
        try {
            // Defense-in-depth: Reject filenames with shell-dangerous characters
            String rawName = stagedTempFile.getFileName().toString();
            if (rawName.matches(".*[;|&`$\\\"'<>\\x00].*")) {
                throw new RuntimeException("SECURITY: Rejected filename with dangerous characters: " + rawName);
            }

            ZonedDateTime takenAtUtc = metadata != null && metadata.getTakenAtUtc() != null 
                    ? metadata.getTakenAtUtc() : ZonedDateTime.now();

            String year = takenAtUtc.format(DateTimeFormatter.ofPattern("yyyy"));
            String month = takenAtUtc.format(DateTimeFormatter.ofPattern("MM"));
            
            Path chronologicalDir = Paths.get(settingsService.getArchivePath(), year, month);
            if (!Files.exists(chronologicalDir)) {
                Files.createDirectories(chronologicalDir);
            }

            String originalFileName = stagedTempFile.getFileName().toString();
            String extension = "";
            String baseName = originalFileName;
            
            int dotIndex = originalFileName.lastIndexOf(".");
            if (dotIndex > 0) {
                extension = originalFileName.substring(dotIndex);
                baseName = originalFileName.substring(0, dotIndex);
            }
            
            String safeHash = sha256 != null && sha256.length() > 8 ? sha256.substring(0, 8) : "NOPREFIX";
            String finalFileName = baseName + "_" + safeHash + extension;

            Path finalDestination = chronologicalDir.resolve(finalFileName);

            Files.move(stagedTempFile, finalDestination, StandardCopyOption.REPLACE_EXISTING);
            log.info("Successfully filed original asset! Permanently archived to: {}", finalDestination);
            
            MediaAsset asset = new MediaAsset();
            asset.setOriginalFilename(originalFileName);
            asset.setRelativePath(year + "/" + month + "/" + finalFileName);
            asset.setSha256Hash(sha256 != null ? sha256 : "UNKNOWN_" + System.currentTimeMillis());
            asset.setFileType(extension.replace(".", "").toUpperCase());
            asset.setFileSize(Files.size(finalDestination));
            asset.setStatus(MediaAsset.AiStatus.PENDING_AI);
            
            // Link the extracted EXIF data directly into PostgreSQL alongside the asset!
            asset.setMetadata(metadata);
            
            repository.save(asset);

            log.info("Ringing the Internal JVM Buzzer to wake the sleeping 0% CPU AI Thread...");
            eventPublisher.publishEvent(new AiInferenceWakeEvent(this));
            
            return finalDestination;

        } catch (IOException e) {
            log.error("CRITICAL FAILURE: Could not execute OS move for file: {}.", stagedTempFile, e);
            throw new RuntimeException("Failed to physically file mathematical original asset", e);
        }
    }
}
