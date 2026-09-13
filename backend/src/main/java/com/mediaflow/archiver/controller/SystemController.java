package com.mediaflow.archiver.controller;

import com.mediaflow.archiver.entity.MediaAsset;
import com.mediaflow.archiver.repository.MediaAssetRepository;
import com.mediaflow.archiver.service.FileCrawlerService;
import com.mediaflow.archiver.service.GeminiOrchestratorService;
import com.mediaflow.archiver.service.SseBroadcastService;
import com.mediaflow.archiver.service.SettingsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "${archiver.cors.allowed-origins:*}")
public class SystemController {

    private final MediaAssetRepository repository;
    private final SseBroadcastService sseService;
    private final FileCrawlerService crawlerService;
    private final GeminiOrchestratorService geminiService;
    private final SettingsService settingsService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public SystemController(MediaAssetRepository repository, SseBroadcastService sseService, 
                           FileCrawlerService crawlerService, GeminiOrchestratorService geminiService,
                           SettingsService settingsService,
                           org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.sseService = sseService;
        this.crawlerService = crawlerService;
        this.geminiService = geminiService;
        this.settingsService = settingsService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Server-Sent Events (SSE) Endpoint.
     * React automatically connects an EventSource to this address to stream live extraction feedback.
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToHardwareEvents() {
        // Long.MAX_VALUE keeps the pipe permanently open and streaming
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); 
        sseService.addEmitter(emitter);
        return emitter;
    }

    /**
     * The Master Gallery Endpoint (Paginated).
     * React queries this to draw the high-performance Masonry grid.
     * Supports: GET /api/media?page=0&size=50
     * Defaults to page 0, 50 items per page, sorted by newest first.
     */
    @GetMapping("/media")
    public Page<MediaAsset> fetchMedia(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository.findAll(pageable);
    }

    /**
     * Legacy unpaginated endpoint. Returns ALL media for batch operations.
     */
    @GetMapping("/media/all")
    public List<MediaAsset> fetchAllMedia() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /**
     * AI Usage Stats Endpoint.
     * Returns live request counts, queue status, and quota state for the frontend dashboard.
     */
    @GetMapping("/ai-status")
    public Map<String, Object> getAiStatus() {
        return geminiService.getAiStats();
    }

    /**
     * Settings Endpoints.
     * GET  /api/settings — returns all current user preferences.
     * PUT  /api/settings — updates one or more settings (e.g., archivePath).
     */
    @GetMapping("/settings")
    public Map<String, String> getSettings() {
        return settingsService.getAll();
    }

    @PutMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody Map<String, String> updates) {
        try {
            Map<String, String> result = settingsService.update(updates);
            return ResponseEntity.ok(Map.of("status", "saved", "settings", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/settings/test-gemini")
    public ResponseEntity<Map<String, Object>> testGeminiConnectivity() {
        return geminiService.testApiConnectivity();
    }

    /**
     * Search Endpoint (Paginated).
     * Searches across AI tags, filenames, file paths, and camera model.
     * GET /api/media/search?q=chocolate&page=0&size=50
     */
    @GetMapping("/media/search")
    public Page<MediaAsset> searchMedia(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (q == null || q.isBlank()) {
            return repository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        }
        return repository.searchAllPaginated(q.trim(), PageRequest.of(page, size));
    }

    /**
     * Advanced Filtration Endpoint (Paginated).
     * Combines multiple faceted filters.
     * GET /api/media/filter?type=VIDEO&startDate=2023-01-01&status=COMPLETED
     */
    @GetMapping("/media/filter")
    public Page<MediaAsset> filterMedia(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String make,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) Boolean hasPerson,
            @RequestParam(required = false) String dominantColor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        return repository.filterAssets(q, status, type, startDate, endDate, make, model, hasPerson, dominantColor, PageRequest.of(page, size));
    }

    /**
     * Delete Asset Endpoint.
     * Permanently removes a media asset: DB row + original file + proxy thumbnail.
     * DELETE /api/media/{id}
     */
    @DeleteMapping("/media/{id}")
    public ResponseEntity<Map<String, Object>> deleteAsset(@PathVariable Long id) {
        var assetOpt = repository.findById(id);
        if (assetOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "error",
                    "message", "Asset not found with ID: " + id
            ));
        }

        MediaAsset asset = assetOpt.get();
        String filename = asset.getOriginalFilename();

        // 1. Delete original file from archive
        try {
            Path originalPath = Paths.get(settingsService.getArchivePath(), asset.getRelativePath());
            if (Files.exists(originalPath)) {
                Files.delete(originalPath);
            }
        } catch (Exception e) {
            // Log but don't block deletion — DB cleanup is more important
        }

        // 2. Delete proxy thumbnail
        try {
            String sha = asset.getSha256Hash();
            if (sha != null) {
                Path proxy = Paths.get(settingsService.getArchivePath(), "PROXIES", sha + ".jpg");
                if (Files.exists(proxy)) {
                    Files.delete(proxy);
                }
            }
        } catch (Exception e) {
            // Log but don't block
        }

        // 3. Delete DB row
        repository.deleteById(id);

        return ResponseEntity.ok(Map.of(
                "status", "deleted",
                "message", "Asset permanently removed: " + filename
        ));
    }

    /**
     * Proxy Thumbnail Serving Endpoint.
     * Serves the downscaled JPEG proxy by its SHA-256 hash.
     * Used by the React gallery for fast thumbnail rendering.
     */
    @GetMapping("/proxy/{hash}")
    public ResponseEntity<org.springframework.core.io.Resource> serveProxy(@PathVariable String hash) {
        try {
            Path proxyPath = Paths.get(settingsService.getArchivePath(), "PROXIES", hash + ".jpg");
            if (!Files.exists(proxyPath)) {
                return ResponseEntity.notFound().build();
            }
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(proxyPath.toUri());
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .header("Cache-Control", "public, max-age=86400")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Original File Serving Endpoint.
     * Serves the full-resolution original file for the inspector panel preview.
     */
    @GetMapping("/original/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> serveOriginal(@PathVariable Long id) {
        try {
            var asset = repository.findById(id).orElse(null);
            if (asset == null) return ResponseEntity.notFound().build();
            
            Path filePath = Paths.get(settingsService.getArchivePath(), asset.getRelativePath());
            if (!Files.exists(filePath)) return ResponseEntity.notFound().build();
            
            String filename = filePath.getFileName().toString();
            String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf(".") + 1).toLowerCase() : "";
            MediaType mediaType = switch (ext) {
                case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
                case "png" -> MediaType.IMAGE_PNG;
                case "gif" -> MediaType.IMAGE_GIF;
                case "webp" -> MediaType.parseMediaType("image/webp");
                case "mp4" -> MediaType.parseMediaType("video/mp4");
                case "webm" -> MediaType.parseMediaType("video/webm");
                default -> MediaType.APPLICATION_OCTET_STREAM;
            };
            
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header("Cache-Control", "public, max-age=3600")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Manual Folder Ingestion Endpoint.
     * 
     * This is the universal trigger that handles ALL non-USB sources:
     *   - WiFi transfers (AirDrop, Nearby Share, KDE Connect)
     *   - MTP devices (Android phones connected via USB)
     *   - Network shares (SMB/NFS mounted folders)
     *   - Cloud sync folders (Google Drive, iCloud, Dropbox)
     *   - Any arbitrary local directory (Downloads, Desktop, etc.)
     *
     * The React UI opens a native Tauri file dialog → user picks a folder → 
     * frontend POSTs the path here → backend crawls it through the same pipeline.
     */
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, String>> manualIngest(@RequestBody Map<String, String> request) {
        String folderPath = request.get("path");

        if (folderPath == null || folderPath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Missing 'path' field in request body."
            ));
        }

        Path targetPath = Paths.get(folderPath);

        if (!Files.exists(targetPath) || !Files.isDirectory(targetPath)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Path does not exist or is not a directory: " + folderPath
            ));
        }

        // Security: block path traversal attempts (e.g. "../../etc/passwd")
        try {
            Path resolved = targetPath.toRealPath();
            if (!resolved.toString().equals(targetPath.toAbsolutePath().normalize().toString())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "Path traversal detected. Rejected."
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Invalid path: " + e.getMessage()
            ));
        }

        // Dispatch async so the HTTP response returns instantly
        sseService.broadcast("Manual ingestion started for: " + folderPath);
        CompletableFuture.runAsync(() -> crawlerService.crawlAndStage(targetPath));

        return ResponseEntity.ok(Map.of(
                "status", "accepted",
                "message", "Ingestion pipeline dispatched for: " + folderPath
        ));
    }

    /**
     * Retry Failed AI Tasks.
     * Resets all FAILED assets back to PENDING_AI and wakes the AI worker.
     * Use this after fixing an invalid API key or network issue.
     */
    @PostMapping("/retry-failed")
    public ResponseEntity<Map<String, Object>> retryFailed() {
        var failedAssets = repository.findByStatus(com.mediaflow.archiver.entity.MediaAsset.AiStatus.FAILED);
        int count = failedAssets.size();
        
        for (var asset : failedAssets) {
            asset.setStatus(com.mediaflow.archiver.entity.MediaAsset.AiStatus.PENDING_AI);
            asset.setRetryCount(0);
        }
        repository.saveAll(failedAssets);

        if (count > 0) {
            // Wake the sleeping AI worker
            eventPublisher.publishEvent(new com.mediaflow.archiver.event.AiInferenceWakeEvent(this));
        }

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "requeued", count,
                "message", count + " assets re-queued for AI tagging."
        ));
    }

    /**
     * Stop Ingestion Endpoint.
     * Cooperatively signals the AI worker to stop after finishing the current asset.
     * Any remaining PENDING_AI assets stay in the queue and can be resumed later.
     */
    @PostMapping("/stop-ingestion")
    public ResponseEntity<Map<String, Object>> stopIngestion() {
        boolean wasStopped = geminiService.requestStop();
        if (wasStopped) {
            return ResponseEntity.ok(Map.of(
                    "status", "stopping",
                    "message", "Ingestion stop requested. Worker will halt after the current asset."
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                    "status", "idle",
                    "message", "No active ingestion to stop."
            ));
        }
    }
}
