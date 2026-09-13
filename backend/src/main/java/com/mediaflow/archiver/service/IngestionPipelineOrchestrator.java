package com.mediaflow.archiver.service;

import com.mediaflow.archiver.entity.MetadataSpec;
import com.mediaflow.archiver.repository.MediaAssetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@Slf4j
public class IngestionPipelineOrchestrator {

    private final IngestionStagingService stagingService;
    private final ExifMetadataService exifService;
    private final FilerService filerService;
    private final ThumbnailProxyService proxyService;
    private final SseBroadcastService sseService;
    private final MediaAssetRepository repository;
    private final SettingsService settingsService;

    public IngestionPipelineOrchestrator(
            IngestionStagingService stagingService,
            ExifMetadataService exifService,
            FilerService filerService,
            ThumbnailProxyService proxyService,
            SseBroadcastService sseService,
            MediaAssetRepository repository,
            SettingsService settingsService) {
        this.stagingService = stagingService;
        this.exifService = exifService;
        this.filerService = filerService;
        this.proxyService = proxyService;
        this.sseService = sseService;
        this.repository = repository;
        this.settingsService = settingsService;
    }

    /**
     * Master Conductor: Routes a fresh SD Card file through all engine blocks gracefully.
     */
    public void processNewFile(Path rawSdCardFile) {
        log.info("\n--- [PIPELINE START] Mapped Raw Asset: {} ---", rawSdCardFile.getFileName());

        try {
            // STEP 1: Staging & Cryptography (Memory-Safe 8KB Chunking)
            String sha256Hash = stagingService.stageAndHashFile(rawSdCardFile);
            Path tempStagedFile = Paths.get(settingsService.getArchivePath(), "TEMP", rawSdCardFile.getFileName().toString());

            // STEP 2: SHA-256 Deduplication Check
            // If this exact file already exists in the archive, skip the entire pipeline silently.
            // This prevents re-importing when the same SD card is plugged in twice.
            if (repository.existsBySha256Hash(sha256Hash)) {
                log.info("DUPLICATE DETECTED: SHA-256 {} already exists in archive. Skipping: {}", 
                         sha256Hash.substring(0, 8), rawSdCardFile.getFileName());
                // Clean up the staged temp file since we don't need it
                java.nio.file.Files.deleteIfExists(tempStagedFile);
                return;
            }

            // STEP 3: Extraction (Mathematical UTC & GPS Natively)
            MetadataSpec metadata = exifService.extractMetadata(tempStagedFile);

            // STEP 4: Proxy Subsystem (Tauri Sidecar HEIC/RAW to JPG / 1080p Downscaling)
            // Locks the proxy name to the SHA-256 hash so the AI queue can inherently find it
            proxyService.createOptimalProxy(tempStagedFile, sha256Hash);

            // STEP 5: The Filer (Execute OS physical move, Write to DB, and Ring the AI Buzzer!)
            filerService.archiveOriginalFile(tempStagedFile, sha256Hash, metadata);
            
            log.info("--- [PIPELINE SUCCESS] Asset is safe, Proxy generated, Database Updated, AI Worker Notified. ---");
            
            // STEP 6: Live UI Feedback!
            // This pushes a physical byte across the network to the React Tauri window instantly.
            sseService.broadcast("Successfully archived and queued: " + rawSdCardFile.getFileName());
            
        } catch (Exception e) {
            log.error("--- [PIPELINE CRASHED] Asset quarantine required for: {} ---", rawSdCardFile, e);
            sseService.broadcast("Pipeline error on: " + rawSdCardFile.getFileName());
        }
    }
}
