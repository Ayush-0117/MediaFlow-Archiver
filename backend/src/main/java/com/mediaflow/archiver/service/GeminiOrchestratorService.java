package com.mediaflow.archiver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediaflow.archiver.entity.MediaAsset;
import com.mediaflow.archiver.event.AiInferenceWakeEvent;
import com.mediaflow.archiver.repository.MediaAssetRepository;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class GeminiOrchestratorService {

    @Value("${gemini.api.key:YOUR_API_KEY_HERE}")
    private String geminiApiKey;

    /** Maximum number of times a single asset will be retried before permanent FAILED status. */
    private static final int MAX_RETRIES = 3;

    // ═══════════════════════════════════════════════════════════════
    // MODEL ROTATION — Automatically cycles through Gemini models
    // when one model's free-tier quota (20 req/day) is exhausted.
    // Each model has its own independent daily quota.
    // ═══════════════════════════════════════════════════════════════
    private static final String[] MODEL_ROTATION = {
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-2.5-flash-lite",
        "gemini-3.1-flash-lite",
        "gemini-3.5-flash"
    };
    private static final String API_URL_TEMPLATE =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private volatile int currentModelIndex = 0;
    private final Set<String> exhaustedModels = new HashSet<>();

    private final MediaAssetRepository repository;
    private final SseBroadcastService sseService;
    private final SettingsService settingsService;
    private final AtomicBoolean isWorking = new AtomicBoolean(false);
    private volatile boolean stopRequested = false;
    
    // We utilize pure Java Spring HTTP components instead of external Google SDK wrapper libraries 
    // to strictly prevent fatal versioning conflicts in the Tauri desktop environment!
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    // --- AI Usage Tracking (Thread-Safe) ---
    private final AtomicInteger dailyRequestCount = new AtomicInteger(0);
    private volatile LocalDate dailyResetDate = LocalDate.now();
    private volatile boolean quotaPaused = false;
    private volatile String quotaPausedReason = null;
    /** Learned dynamically from Google's 429 response. -1 = unknown (no limit hit yet). */
    private volatile int detectedQuotaLimit = -1;

    public GeminiOrchestratorService(MediaAssetRepository repository, SseBroadcastService sseService, SettingsService settingsService) {
        this.repository = repository;
        this.sseService = sseService;
        this.settingsService = settingsService;
        log.info("Gemini Database-Backed AI Queue Initialized. 0% Idle CPU Active.");
    }

    /**
     * Fixes "Startup Amnesia" and "Zombie Tasks". On boot, automatically checks the database to see
     * if the computer crashed previously leaving unfinished PENDING_AI photos.
     */
    @PostConstruct
    public void checkForOrphansOnBoot() {
        log.info("Scanning PostgreSQL for orphaned AI tasks...");
        
        List<MediaAsset> zombies = repository.findByStatus(MediaAsset.AiStatus.PROCESSING);
        if (!zombies.isEmpty()) {
            for (MediaAsset zombie : zombies) {
                zombie.setStatus(MediaAsset.AiStatus.PENDING_AI);
            }
            repository.saveAll(zombies);
            log.warn("Resurrected {} Zombie tasks from previous power failure back into the Queue!", zombies.size());
        }

        wakeUpWorker();
    }

    @EventListener
    public void onNewFileCommitted(AiInferenceWakeEvent event) {
        log.debug("Heard Database Buzzer! Waking up AI Worker...");
        wakeUpWorker();
    }

    /**
     * Guarantees only ONE thread processes the database queue to mathematically
     * prevent Thundering Herds and 429 Google API Bans.
     */
    private synchronized void wakeUpWorker() {
        if (isWorking.compareAndSet(false, true)) {
            Thread workerThread = new Thread(this::drainDatabaseQueue);
            workerThread.setDaemon(true);
            workerThread.start();
        } else {
            log.debug("AI Worker is already awake and processing. Suppressing redundant buzzer.");
        }
    }

    private void drainDatabaseQueue() {
        stopRequested = false;
        log.info("AI Worker Thread Activated. Draining PostgreSQL PENDING_AI queue...");
        
        try {
            // ═══════════════════════════════════════════════════════════
            // PHASE 1: Process all PENDING_AI assets
            // ═══════════════════════════════════════════════════════════
            processQueue();

            // Check if stop was requested during primary processing
            if (stopRequested) {
                log.info("🛑 Ingestion stopped by user after primary queue.");
                sseService.broadcast("🛑 Ingestion stopped by user.");
                return;
            }

            // Count results after primary queue
            long completed = repository.countByStatus(MediaAsset.AiStatus.COMPLETED);
            long failed = repository.countByStatus(MediaAsset.AiStatus.FAILED);

            // Notify: Primary ingestion complete
            log.info("✅ Primary ingestion complete! {} completed, {} failed.", completed, failed);
            sseService.broadcast("✅ Ingestion complete! " + completed + " assets processed successfully.");

            // ═══════════════════════════════════════════════════════════
            // PHASE 2: Auto-retry FAILED assets
            // ═══════════════════════════════════════════════════════════
            if (failed > 0) {
                log.info("🔄 Found {} failed assets. Auto-retrying...", failed);
                sseService.broadcast("🔄 " + failed + " assets failed. Auto-retrying with fresh models...");

                // Small delay to let SSE message arrive in the frontend
                try { Thread.sleep(2000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); return;
                }

                // Reset failed assets back to PENDING_AI
                List<MediaAsset> failedAssets = repository.findByStatus(MediaAsset.AiStatus.FAILED);
                for (MediaAsset asset : failedAssets) {
                    asset.setStatus(MediaAsset.AiStatus.PENDING_AI);
                    asset.setRetryCount(0); // Fresh start
                }
                repository.saveAll(failedAssets);
                log.info("  Re-queued {} failed assets for retry.", failedAssets.size());

                // Process the retry queue
                processQueue();

                // Final count after retry
                long finalCompleted = repository.countByStatus(MediaAsset.AiStatus.COMPLETED);
                long finalFailed = repository.countByStatus(MediaAsset.AiStatus.FAILED);
                long recovered = finalCompleted - completed;

                if (finalFailed == 0) {
                    log.info("🎉 All assets processed! {} total, {} recovered from failures.", finalCompleted, recovered);
                    sseService.broadcast("🎉 All " + finalCompleted + " assets processed! " + recovered + " recovered from earlier failures.");
                } else {
                    log.info("⚠️ Retry complete. {} recovered, {} still failed.", recovered, finalFailed);
                    sseService.broadcast("⚠️ Retry complete. " + recovered + " recovered, " + finalFailed + " still failed.");
                }
            } else {
                log.info("🎉 All {} assets processed with zero failures!", completed);
                sseService.broadcast("🎉 All " + completed + " assets processed with zero failures!");
            }

            log.info("AI Worker shutting down to 0% CPU Sleep.");

        } finally {
            // ALWAYS release the lock, even on unexpected crashes
            isWorking.set(false);
        }
    }

    /**
     * Core processing loop: pulls PENDING_AI assets one at a time and
     * sends them through Gemini. Exits when the queue is empty.
     */
    private void processQueue() {
        while (!stopRequested) {
            Optional<MediaAsset> taskOpt = repository.findFirstByStatusOrderByCreatedAtAsc(MediaAsset.AiStatus.PENDING_AI);

            if (taskOpt.isEmpty()) break;

            MediaAsset asset = taskOpt.get();

            try {
                asset.setStatus(MediaAsset.AiStatus.PROCESSING);
                repository.save(asset);

                processAssetWithGemini(asset);

                // Success path
                asset.setStatus(MediaAsset.AiStatus.COMPLETED);
                repository.save(asset);

                sseService.broadcast("AI tagging complete: " + asset.getOriginalFilename());

                // Dynamic rate limit: calculate sleep based on detected quota
                // If limit is 20 RPM → 60/20 = 3s. Unknown → default 4.5s
                long sleepMs = (detectedQuotaLimit > 0)
                        ? (long) Math.ceil(60_000.0 / detectedQuotaLimit) + 500  // +500ms safety margin
                        : 4500;
                log.info("Sleeping {}ms to stay within {} RPM limit.", sleepMs,
                         detectedQuotaLimit > 0 ? detectedQuotaLimit : "unknown");
                Thread.sleep(sleepMs);

            } catch (InterruptedException e) {
                log.warn("Gemini Orchestrator execution pool interrupted.");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                log.error("AI processing failed for asset ID {} ({}): {}",
                          asset.getId(), asset.getOriginalFilename(), errorMsg);

                // ── BRANCH 1: Rate Limit (429) ─────────────────────────────
                // 429 is NOT a real error — it's Google saying "slow down".
                // Do NOT burn a retry. Try rotating to the next model first.
                if (errorMsg.contains("429")) {
                    asset.setStatus(MediaAsset.AiStatus.PENDING_AI);
                    // retryCount stays unchanged — this was not a real failure
                    repository.save(asset);

                    // Extract quotaValue (e.g., '"quotaValue": "20"')
                    try {
                        int qvIdx = errorMsg.indexOf("quotaValue");
                        if (qvIdx > 0) {
                            String after = errorMsg.substring(qvIdx);
                            String num = after.replaceAll("[^0-9]", " ").trim().split("\\s+")[0];
                            detectedQuotaLimit = Integer.parseInt(num);
                        }
                    } catch (Exception ignored) {}

                    // Mark current model as exhausted
                    String exhaustedModel = getCurrentModelName();
                    exhaustedModels.add(exhaustedModel);

                    // Try rotating to the next available model
                    if (rotateToNextModel()) {
                        String newModel = getCurrentModelName();
                        log.warn("⚡ {} quota exhausted! Switching to: {}", exhaustedModel, newModel);
                        sseService.broadcast("⚡ " + exhaustedModel + " quota exhausted. Switching to " + newModel + "...");
                        // No sleep needed — immediately retry with new model
                    } else {
                        // ALL models exhausted — must wait for quota reset
                        quotaPaused = true;

                        long waitMs = 60_000;
                        try {
                            int rdIdx = errorMsg.indexOf("retryDelay");
                            if (rdIdx > 0) {
                                String chunk = errorMsg.substring(rdIdx, Math.min(rdIdx + 50, errorMsg.length()));
                                String numPart = chunk.replaceAll("[^0-9.]", " ").trim().split("\\s+")[0];
                                double parsed = Double.parseDouble(numPart);
                                waitMs = chunk.contains("ms") ? (long) parsed : (long) (parsed * 1000);
                            }
                        } catch (Exception ignored) {}

                        quotaPausedReason = "All " + MODEL_ROTATION.length + " models exhausted. Waiting for quota reset.";
                        log.warn("🛑 ALL {} Gemini models exhausted! Sleeping {}s for quota reset...",
                                 MODEL_ROTATION.length, waitMs / 1000);
                        sseService.broadcast("🛑 All " + MODEL_ROTATION.length + " AI models exhausted. Waiting " + (waitMs / 1000) + "s for reset...");

                        try { Thread.sleep(waitMs); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt(); break;
                        }
                        quotaPaused = false;
                    }
                    // Loop continues — same asset will be re-fetched from PENDING_AI

                // ── BRANCH 2: Real Error (corrupt file, missing proxy, etc.) ──
                } else {
                    int currentRetries = asset.getRetryCount() != null ? asset.getRetryCount() : 0;

                    if (currentRetries < MAX_RETRIES) {
                        asset.setStatus(MediaAsset.AiStatus.PENDING_AI);
                        asset.setRetryCount(currentRetries + 1);
                        log.warn("Re-queuing asset for retry ({}/{}): {}",
                                 currentRetries + 1, MAX_RETRIES, asset.getOriginalFilename());
                    } else {
                        asset.setStatus(MediaAsset.AiStatus.FAILED);
                        // Tag the asset with a specific "failed" JSON so the UI can easily display/filter it
                        asset.setAiTagsJson("{\"description\":\"AI Processing Failed\",\"hasPerson\":false,\"dominantColor\":\"unknown\",\"error\":true,\"tags\":[\"failed_ai\"]}");
                        log.error("PERMANENTLY FAILED after {} retries. Skipping asset: {}",
                                  MAX_RETRIES, asset.getOriginalFilename());
                    }
                    repository.save(asset);
                    sseService.broadcast("AI error on: " + asset.getOriginalFilename() + " (retry " + (currentRetries + 1) + "/" + MAX_RETRIES + ")");

                    try { Thread.sleep(2000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); break;
                    }
                }
            }
        }
    }

    /**
     * Cooperatively requests the AI worker thread to stop after finishing its current asset.
     * The worker checks this flag before picking up each new item from the queue.
     * @return true if a worker was running and will be stopped, false if no worker was active.
     */
    public boolean requestStop() {
        if (isWorking.get()) {
            stopRequested = true;
            log.info("🛑 Stop requested. Worker will halt after the current asset completes.");
            sseService.broadcast("🛑 Stop requested. Finishing current asset...");
            return true;
        }
        return false;
    }

    /**
     * Returns current AI usage stats for the frontend settings/ingestion UI.
     */
    public java.util.Map<String, Object> getAiStats() {
        // Auto-reset counter at midnight
        LocalDate today = LocalDate.now();
        if (!today.equals(dailyResetDate)) {
            dailyRequestCount.set(0);
            dailyResetDate = today;
            quotaPaused = false;
            quotaPausedReason = null;
            exhaustedModels.clear();
            currentModelIndex = 0;
        }

        long pending = repository.countByStatus(MediaAsset.AiStatus.PENDING_AI);
        long completed = repository.countByStatus(MediaAsset.AiStatus.COMPLETED);
        long failed = repository.countByStatus(MediaAsset.AiStatus.FAILED);
        long processing = repository.countByStatus(MediaAsset.AiStatus.PROCESSING);
        long totalAssets = repository.count();

        // Calculate disk usage
        long archiveSizeBytes = 0;
        long proxySizeBytes = 0;
        try {
            Path archivePath = Paths.get(settingsService.getArchivePath());
            if (Files.exists(archivePath)) {
                archiveSizeBytes = Files.walk(archivePath)
                        .filter(Files::isRegularFile)
                        .mapToLong(p -> { try { return Files.size(p); } catch (Exception e) { return 0; } })
                        .sum();
            }
            Path proxyPath = Paths.get(settingsService.getArchivePath(), "PROXIES");
            if (Files.exists(proxyPath)) {
                proxySizeBytes = Files.walk(proxyPath)
                        .filter(Files::isRegularFile)
                        .mapToLong(p -> { try { return Files.size(p); } catch (Exception e) { return 0; } })
                        .sum();
            }
        } catch (Exception e) {
            log.debug("Could not calculate disk usage: {}", e.getMessage());
        }

        // Using HashMap because Map.of() is limited to 10 entries
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("requestsToday", dailyRequestCount.get());
        stats.put("dailyLimit", detectedQuotaLimit);
        stats.put("quotaPaused", quotaPaused);
        stats.put("quotaPausedReason", quotaPausedReason != null ? quotaPausedReason : "");
        stats.put("workerActive", isWorking.get());
        stats.put("totalAssets", totalAssets);
        stats.put("pending", pending);
        stats.put("completed", completed);
        stats.put("failed", failed);
        stats.put("processing", processing);
        stats.put("archiveSizeBytes", archiveSizeBytes);
        stats.put("proxySizeBytes", proxySizeBytes);
        stats.put("archiveSizeMB", String.format("%.1f", archiveSizeBytes / (1024.0 * 1024.0)));
        stats.put("proxySizeMB", String.format("%.1f", proxySizeBytes / (1024.0 * 1024.0)));
        stats.put("currentModel", getCurrentModelName());
        stats.put("exhaustedModels", String.join(", ", exhaustedModels));
        stats.put("availableModels", MODEL_ROTATION.length - exhaustedModels.size());
        stats.put("stopRequested", stopRequested);
        return stats;
    }

    /**
     * Tests connectivity to the Gemini API using the currently configured API key.
     */
    public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> testApiConnectivity() {
        try {
            String activeApiKey = settingsService.getAll().get("geminiApiKey");
            if (activeApiKey == null || activeApiKey.isBlank()) {
                activeApiKey = this.geminiApiKey;
            }

            if (activeApiKey == null || activeApiKey.isBlank() || "NOT_SET".equals(activeApiKey) || "YOUR_API_KEY_HERE".equals(activeApiKey)) {
                return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of(
                    "status", "error",
                    "message", "API Key is not configured."
                ));
            }

            String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_ROTATION[0];
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("x-goog-api-key", activeApiKey);
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
            
            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(apiUrl, org.springframework.http.HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return org.springframework.http.ResponseEntity.ok(java.util.Map.of(
                    "status", "success",
                    "message", "Successfully authenticated with Gemini API."
                ));
            } else {
                return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of(
                    "status", "error",
                    "message", "Google API rejected the key. HTTP " + response.getStatusCode()
                ));
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
             return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of(
                    "status", "error",
                    "message", "Authentication Failed (HTTP " + e.getStatusCode() + ")"
             ));
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.internalServerError().body(java.util.Map.of(
                "status", "error",
                "message", "Failed to connect to Google API: " + e.getMessage()
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MODEL ROTATION HELPERS
    // ═══════════════════════════════════════════════════════════════

    /** Returns the name of the currently active Gemini model. */
    private String getCurrentModelName() {
        return MODEL_ROTATION[currentModelIndex % MODEL_ROTATION.length];
    }

    /**
     * Attempts to rotate to the next non-exhausted model.
     * @return true if a fresh model was found, false if ALL models are exhausted.
     */
    private boolean rotateToNextModel() {
        for (int i = 1; i <= MODEL_ROTATION.length; i++) {
            int candidateIdx = (currentModelIndex + i) % MODEL_ROTATION.length;
            String candidate = MODEL_ROTATION[candidateIdx];
            if (!exhaustedModels.contains(candidate)) {
                currentModelIndex = candidateIdx;
                return true;
            }
        }
        return false; // All models exhausted
    }

    /**
     * Executes the actual Gemini Vision API HTTP call for a single asset.
     * The API key is passed as a secure header (x-goog-api-key) instead of a URL parameter
     * to prevent accidental log/proxy/URL exposure.
     */
    private void processAssetWithGemini(MediaAsset asset) throws Exception {
        // Try getting API key from UI settings first, fallback to environment variable
        String activeApiKey = settingsService.getAll().get("geminiApiKey");
        if (activeApiKey == null || activeApiKey.isBlank()) {
            activeApiKey = this.geminiApiKey;
        }

        // Guard: refuse to fire if the API key was never configured
        if (activeApiKey == null || activeApiKey.isBlank() || "NOT_SET".equals(activeApiKey) || "YOUR_API_KEY_HERE".equals(activeApiKey)) {
            throw new RuntimeException("GEMINI_API_KEY is not configured in Settings or Environment. Skipping AI tagging.");
        }

        // 1. Locate the deterministic Proxy JPG on disk
        Path proxyPath = Paths.get(settingsService.getArchivePath(), "PROXIES", asset.getSha256Hash() + ".jpg");

        if (!Files.exists(proxyPath)) {
            throw new RuntimeException("Proxy JPG missing! Expected at: " + proxyPath);
        }

        String modelName = getCurrentModelName();
        log.info("[{}] Executing Gemini API on: {}", modelName, asset.getOriginalFilename());

        // Auto-reset counter at midnight
        LocalDate today = LocalDate.now();
        if (!today.equals(dailyResetDate)) {
            dailyRequestCount.set(0);
            dailyResetDate = today;
            quotaPaused = false;
            quotaPausedReason = null;
            exhaustedModels.clear();
            currentModelIndex = 0;
            log.info("🔄 Midnight reset — all models available again.");
        }
        dailyRequestCount.incrementAndGet();
        quotaPaused = false;
        byte[] proxyBytes = Files.readAllBytes(proxyPath);
        String base64Encoded = Base64.getEncoder().encodeToString(proxyBytes);

        // 3. Assemble the HTTP request with secure header-based authentication
        //    API key is passed via x-goog-api-key header to prevent URL-level logging leaks
        String apiUrl = String.format(API_URL_TEMPLATE, modelName);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", activeApiKey);

        Map<String, Object> textPart = Map.of("text", 
                "You are the Mediaflow Archiver AI. Output ONLY raw JSON mapping this image data. " +
                "You must strictly include these exact fields: " +
                "'description' (a full natural language description of the scene), " +
                "'hasPerson' (boolean, true if any human is visible), " +
                "'dominantColor' (string, the most prominent color), " +
                "'subject' (string or array), 'environment' (string), 'mood' (string). " +
                "DO NOT WRAP IN MARKDOWN TICK BLOCKS.");
        Map<String, Object> inlineData = Map.of("mimeType", "image/jpeg", "data", base64Encoded);
        Map<String, Object> imagePart = Map.of("inlineData", inlineData);
        
        Map<String, Object> contentMap = Map.of("parts", Arrays.asList(textPart, imagePart));
        Map<String, Object> requestBody = Map.of("contents", Arrays.asList(contentMap));

        HttpEntity<String> requestEntity = new HttpEntity<>(mapper.writeValueAsString(requestBody), headers);

        // 4. Execute the HTTP call
        ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Google API rejected the payload. HTTP: " + response.getStatusCode());
        }

        // 5. Unpack and clean the native JSON Google Response Tree
        com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(response.getBody());
        com.fasterxml.jackson.databind.JsonNode candidatesNode = rootNode.path("candidates");
        
        if (candidatesNode.isMissingNode() || !candidatesNode.isArray() || candidatesNode.isEmpty()) {
            throw new RuntimeException("Gemini returned no candidates in response body.");
        }
        
        String extractedAiTagString = candidatesNode.get(0)
                .path("content").path("parts").get(0).path("text").asText();
        
        // Fallback sanitization in case Gemini disobeys rule against Markdown ticks
        extractedAiTagString = extractedAiTagString.replace("```json", "").replace("```", "").trim();
        
        asset.setAiTagsJson(extractedAiTagString);
        log.info("Gemini Cloud Integration complete! Captured {}... for {}", 
                 extractedAiTagString.substring(0, Math.min(30, extractedAiTagString.length())), 
                 asset.getOriginalFilename());

        // --- SMART FILE RENAMING ---
        try {
            com.fasterxml.jackson.databind.JsonNode tagNode = mapper.readTree(extractedAiTagString);
            
            // Handle subject being either a string or an array
            com.fasterxml.jackson.databind.JsonNode subjectNode = tagNode.path("subject");
            String subject;
            if (subjectNode.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (com.fasterxml.jackson.databind.JsonNode elem : subjectNode) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(elem.asText(""));
                }
                subject = sb.toString();
            } else {
                subject = subjectNode.asText("");
            }
            String env = tagNode.path("environment").asText("");
            
            String slug = (subject + " " + env).replaceAll("[^a-zA-Z0-9 ]", "").trim().replaceAll(" +", "_").toLowerCase();
            if (slug.isBlank()) slug = "ai_processed";
            if (slug.length() > 60) slug = slug.substring(0, 60);
            if (slug.endsWith("_")) slug = slug.substring(0, slug.length() - 1);
            
            Path originalPath = java.nio.file.Paths.get(settingsService.getArchivePath(), asset.getRelativePath());
            if (java.nio.file.Files.exists(originalPath)) {
                String oldName = originalPath.getFileName().toString();
                String ext = "";
                int dotIndex = oldName.lastIndexOf(".");
                if (dotIndex > 0) ext = oldName.substring(dotIndex);
                
                String hashPrefix = asset.getSha256Hash().substring(0, 8);
                String newFileName = slug + "_" + hashPrefix + ext;
                Path newPath = originalPath.getParent().resolve(newFileName);
                
                // Collision safety: append a counter if a file with this name already exists
                int counter = 1;
                while (java.nio.file.Files.exists(newPath) && !newPath.equals(originalPath)) {
                    newFileName = slug + "_" + hashPrefix + "_" + counter + ext;
                    newPath = originalPath.getParent().resolve(newFileName);
                    counter++;
                }
                
                if (!newPath.equals(originalPath)) {
                    java.nio.file.Files.move(originalPath, newPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                    
                    String oldRelative = asset.getRelativePath();
                    String newRelative;
                    int lastSlash = oldRelative.lastIndexOf("/");
                    if (lastSlash >= 0) {
                        newRelative = oldRelative.substring(0, lastSlash + 1) + newFileName;
                    } else {
                        newRelative = newFileName;
                    }
                    
                    asset.setRelativePath(newRelative);
                    log.info("Smart Renamed File: {} -> {}", oldName, newFileName);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to smart-rename physical file for {}. Proceeding with original name. Error: {}", 
                     asset.getOriginalFilename(), e.getMessage());
        }
    }
}
