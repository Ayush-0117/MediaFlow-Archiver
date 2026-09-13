package com.mediaflow.archiver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HWPartition;
import oshi.hardware.HardwareAbstractionLayer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Unified Hardware & External Device Monitor.
 *
 * Continuously scans for newly connected external media sources and
 * automatically triggers the ingestion pipeline when media is found.
 *
 * Supported device categories:
 *   1. Block Devices (via OSHI) — SD cards, USB flash drives, digital cameras, card readers.
 *   2. MTP Devices (via GVFS)  — Android phones, iPhones, tablets, some digicams.
 *
 * MTP devices on Linux are exposed as FUSE mounts under:
 *   /run/user/{uid}/gvfs/mtp:host={device_id}/
 *
 * The service scans this directory for new mount entries every 5 seconds,
 * auto-mounts unmounted MTP volumes via `gio mount`, and crawls the
 * standard media directories (DCIM, Pictures, Download) inside each device.
 */
@Service
@Slf4j
public class HardwareMonitorService {

    private final HardwareAbstractionLayer hal;
    private final Set<String> knownBlockDrives;
    private final Set<String> knownMtpDevices;
    private final Set<String> activelyCrawling;
    private final FileCrawlerService crawlerService;
    private final SseBroadcastService sseBroadcastService;

    /** Standard directories on Android/iOS that contain user media */
    private static final String[] MEDIA_DIRECTORIES = {
        "DCIM", "Pictures", "Download", "Movies", "Camera"
    };

    /** GVFS mount root for the current user */
    private final Path gvfsRoot;

    public HardwareMonitorService(FileCrawlerService crawlerService,
                                  SseBroadcastService sseBroadcastService) {
        this.crawlerService = crawlerService;
        this.sseBroadcastService = sseBroadcastService;
        SystemInfo systemInfo = new SystemInfo();
        this.hal = systemInfo.getHardware();
        this.knownBlockDrives = new HashSet<>();
        this.knownMtpDevices = new HashSet<>();
        this.activelyCrawling = new HashSet<>();

        // Resolve the GVFS root for the current user
        String uid = System.getProperty("user.name");
        Path possibleGvfs = Paths.get("/run/user/1000/gvfs");
        try {
            // Try to resolve the actual UID
            Process proc = Runtime.getRuntime().exec(new String[]{"id", "-u"});
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String uidStr = br.readLine();
                if (uidStr != null && !uidStr.isBlank()) {
                    possibleGvfs = Paths.get("/run/user/" + uidStr.trim() + "/gvfs");
                }
            }
        } catch (Exception e) {
            log.debug("Could not resolve UID, using default GVFS path: {}", possibleGvfs);
        }
        this.gvfsRoot = possibleGvfs;

        // Initialize base block drives so we don't ingest the OS disk on startup
        for (HWDiskStore disk : hal.getDiskStores()) {
            knownBlockDrives.add(disk.getName());
        }

        // Initialize existing GVFS/MTP mounts so we don't re-ingest on restart
        scanExistingGvfsMounts();

        log.info("HardwareMonitorService active. Ignored {} pre-existing drives. GVFS root: {}",
                knownBlockDrives.size(), gvfsRoot);

        // Notify UI that the system is ready and scanning
        if (knownMtpDevices.isEmpty()) {
            sseBroadcastService.broadcast("⏳ No external devices detected. Plug in a phone, SD card, or USB drive to begin.");
        }
    }

    /**
     * Master scan loop — runs every 5 seconds.
     * Checks for both block devices (SD/USB) and MTP devices (phones).
     */
    @Scheduled(fixedDelay = 5000)
    public void scanForNewDevices() {
        scanForNewBlockDrives();
        scanForNewMtpDevices();
    }

    // ════════════════════════════════════════════════════════════════════
    // BLOCK DEVICE DETECTION (SD Cards, USB Drives, Digicams)
    // ════════════════════════════════════════════════════════════════════

    private void scanForNewBlockDrives() {
        List<HWDiskStore> currentDrives = hal.getDiskStores();

        for (HWDiskStore disk : currentDrives) {
            if (!knownBlockDrives.contains(disk.getName())) {
                log.info("🔌 New Block Device Detected! Name: {} (Size: {} bytes, Model: {})",
                        disk.getName(), disk.getSize(), disk.getModel());
                knownBlockDrives.add(disk.getName());
                sseBroadcastService.broadcast("🔌 External drive detected: " + disk.getModel());

                for (HWPartition partition : disk.getPartitions()) {
                    String mountPoint = partition.getMountPoint();
                    if (mountPoint != null && !mountPoint.trim().isEmpty()) {
                        dispatchCrawl(Paths.get(mountPoint), "Block Device: " + disk.getModel());
                    }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // EXTERNAL DEVICE DETECTION (MTP + GPhoto2: Android, iOS, Tablets, Digicams)
    // ════════════════════════════════════════════════════════════════════

    /** Prefixes that indicate an external media device in GVFS */
    private static final String[] GVFS_DEVICE_PREFIXES = { "mtp:", "gphoto2:" };

    private boolean isDeviceMount(String dirName) {
        for (String prefix : GVFS_DEVICE_PREFIXES) {
            if (dirName.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Scans the GVFS mount directory for new device entries.
     * Supports both MTP (file transfer) and GPhoto2 (camera/PTP) protocols.
     */
    private void scanForNewMtpDevices() {
        if (!Files.exists(gvfsRoot) || !Files.isDirectory(gvfsRoot)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(gvfsRoot)) {
            for (Path entry : stream) {
                String dirName = entry.getFileName().toString();

                if (!isDeviceMount(dirName)) continue;

                if (!knownMtpDevices.contains(dirName)) {
                    knownMtpDevices.add(dirName);

                    String protocol = dirName.startsWith("gphoto2:") ? "GPhoto2/PTP" : "MTP";
                    String deviceName = extractDeviceName(dirName);
                    log.info("📱 New {} Device Detected! {} (mount: {})", protocol, deviceName, dirName);
                    sseBroadcastService.broadcast("📱 Device connected (" + protocol + "): " + deviceName);

                    crawlExternalDevice(entry, deviceName, protocol);
                }
            }
        } catch (Exception e) {
            log.debug("GVFS scan failed (this is normal if no devices are connected): {}", e.getMessage());
        }
    }

    /**
     * Crawls an external device by looking for standard media directories.
     * Handles two layouts:
     *   - MTP:     root / Internal shared storage / DCIM / ...
     *   - GPhoto2: root / DCIM / ...  (media dirs directly at root)
     */
    private void crawlExternalDevice(Path deviceRoot, String deviceName, String protocol) {
        // First: check if media directories exist directly at root (GPhoto2 layout)
        boolean foundDirectMediaDirs = false;
        for (String mediaDir : MEDIA_DIRECTORIES) {
            Path directPath = deviceRoot.resolve(mediaDir);
            if (Files.exists(directPath) && Files.isDirectory(directPath)) {
                foundDirectMediaDirs = true;
                log.info("  📂 Found media directory: {}/{}", deviceName, mediaDir);
                sseBroadcastService.broadcast("📂 Scanning " + deviceName + "/" + mediaDir);
                dispatchCrawl(directPath, protocol + ": " + deviceName + "/" + mediaDir);
            }
        }

        if (foundDirectMediaDirs) return;

        // Second: drill into storage volumes (MTP layout — "Internal shared storage", "SD Card")
        try (DirectoryStream<Path> storageStream = Files.newDirectoryStream(deviceRoot)) {
            for (Path storageVolume : storageStream) {
                if (!Files.isDirectory(storageVolume)) continue;

                String volumeName = storageVolume.getFileName().toString();
                log.info("  📂 Found storage volume: {}/{}", deviceName, volumeName);
                sseBroadcastService.broadcast("📂 Scanning " + deviceName + "/" + volumeName);

                for (String mediaDir : MEDIA_DIRECTORIES) {
                    Path mediaDirPath = storageVolume.resolve(mediaDir);
                    if (Files.exists(mediaDirPath) && Files.isDirectory(mediaDirPath)) {
                        dispatchCrawl(mediaDirPath,
                                protocol + ": " + deviceName + "/" + volumeName + "/" + mediaDir);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read device storage: {}", e.getMessage());
            sseBroadcastService.broadcast("⚠️ Could not read device: " + deviceName + " — " + e.getMessage());
        }
    }

    /**
     * Also attempts to auto-mount any unmounted MTP volumes detected by GIO.
     * Called periodically to catch devices that are plugged in but not yet
     * mounted by the desktop environment.
     *
     * gio mount -l output format:
     *   Volume(0): RMX2117
     *     Type: GProxyVolume (GProxyVolumeMonitorMTP)
     *   Mount(0): RMX2117 -> mtp://realme_RMX2117_.../
     *     Type: GProxyShadowMount (GProxyVolumeMonitorMTP)
     *
     * If a Volume exists with MTP type but has NO corresponding Mount, the
     * device is plugged in but not yet accessible. We auto-mount it.
     */
    @Scheduled(fixedDelay = 8000, initialDelay = 5000)
    public void attemptAutoMountDevices() {
        try {
            // Use -li flag to get activation_root URIs for unmounted volumes
            Process proc = Runtime.getRuntime().exec(new String[]{"gio", "mount", "-li"});
            List<String> lines = new java.util.ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    lines.add(line);
                }
            }

            // Parse volumes: find external device volumes (MTP or GPhoto2)
            // that have can_mount=1 but no corresponding GVFS mount yet
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);

                // Look for Volume lines with MTP or GPhoto2 type on the NEXT line
                if (line.trim().startsWith("Volume(") && line.contains(":")) {
                    String volName = line.substring(line.indexOf(":") + 1).trim();

                    // Check if next line indicates it's an external device
                    boolean isExternal = false;
                    if (i + 1 < lines.size()) {
                        String typeLine = lines.get(i + 1);
                        isExternal = typeLine.contains("GProxyVolumeMonitorMTP")
                                  || typeLine.contains("GProxyVolumeMonitorGPhoto2");
                    }
                    if (!isExternal) continue;

                    // Extract the activation_root URI from subsequent lines
                    String activationRoot = null;
                    for (int j = i + 2; j < Math.min(i + 15, lines.size()); j++) {
                        String sub = lines.get(j).trim();
                        if (sub.startsWith("activation_root=")) {
                            activationRoot = sub.substring("activation_root=".length());
                            break;
                        }
                        // Stop if we hit the next Volume/Drive/Mount entry
                        if (sub.startsWith("Volume(") || sub.startsWith("Drive(") || sub.startsWith("Mount(")) break;
                    }

                    // Check if this device is already mounted in GVFS
                    boolean alreadyMounted = false;
                    if (Files.exists(gvfsRoot)) {
                        try (DirectoryStream<Path> gvfsStream = Files.newDirectoryStream(gvfsRoot)) {
                            for (Path entry : gvfsStream) {
                                if (isDeviceMount(entry.getFileName().toString())) {
                                    alreadyMounted = true;
                                    break;
                                }
                            }
                        }
                    }

                    if (!alreadyMounted && activationRoot != null) {
                        log.info("📱 Unmounted device detected: {} — mounting via {}", volName, activationRoot);
                        sseBroadcastService.broadcast("📱 Device detected: " + volName + " — mounting...");

                        Process mountProc = Runtime.getRuntime().exec(new String[]{"gio", "mount", activationRoot});
                        int exitCode = mountProc.waitFor();
                        if (exitCode == 0) {
                            log.info("  ✅ Auto-mount successful: {}", activationRoot);
                            sseBroadcastService.broadcast("✅ " + volName + " mounted! Scanning for media...");
                        } else {
                            try (BufferedReader errBr = new BufferedReader(
                                    new InputStreamReader(mountProc.getErrorStream()))) {
                                String errMsg = errBr.readLine();
                                if (errMsg != null && errMsg.contains("Already mounted")) {
                                    log.info("  Device already mounted — GVFS scanner will pick it up.");
                                } else {
                                    log.warn("  ⚠️ Auto-mount failed (exit {}): {}", exitCode, errMsg);
                                    sseBroadcastService.broadcast("⚠️ Could not mount " + volName + ". Tap 'Allow' on your phone.");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("GIO mount scan skipped: {}", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // SHARED UTILITIES
    // ════════════════════════════════════════════════════════════════════

    /**
     * Dispatches an async crawl for a given path, with deduplication.
     */
    private void dispatchCrawl(Path targetPath, String source) {
        String key = targetPath.toString();
        if (activelyCrawling.contains(key)) {
            log.debug("Already crawling: {}. Skipping.", key);
            return;
        }

        log.info("  🚀 Dispatching ingestion crawler for: {} (source: {})", targetPath, source);
        sseBroadcastService.broadcast("🚀 Starting ingestion: " + source);
        activelyCrawling.add(key);

        CompletableFuture.runAsync(() -> {
            try {
                crawlerService.crawlAndStage(targetPath);
                sseBroadcastService.broadcast("✅ Ingestion complete: " + source);
            } catch (Exception e) {
                log.error("Crawl failed for {}: {}", targetPath, e.getMessage());
                sseBroadcastService.broadcast("❌ Ingestion failed: " + source + " — " + e.getMessage());
            } finally {
                activelyCrawling.remove(key);
            }
        });
    }

    /**
     * Extracts a human-readable device name from the GVFS mount directory name.
     * Input:  "mtp:host=realme_RMX2117_RKQWPBL7PZWKZD9D"
     *     or  "gphoto2:host=realme_RMX2117_RKQWPBL7PZWKZD9D"
     * Output: "Realme RMX2117"
     */
    private String extractDeviceName(String gvfsDirName) {
        try {
            // Strip known prefixes
            String hostPart = gvfsDirName
                    .replace("mtp:host=", "")
                    .replace("gphoto2:host=", "");
            // Split by underscore — typically: brand_model_serial
            String[] parts = hostPart.split("_");
            if (parts.length >= 2) {
                String brand = parts[0].substring(0, 1).toUpperCase() + parts[0].substring(1).toLowerCase();
                return brand + " " + parts[1];
            }
            return hostPart;
        } catch (Exception e) {
            return gvfsDirName;
        }
    }

    /**
     * Records any pre-existing GVFS mounts at startup so we don't re-crawl them.
     */
    private void scanExistingGvfsMounts() {
        if (!Files.exists(gvfsRoot)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(gvfsRoot)) {
            for (Path entry : stream) {
                String dirName = entry.getFileName().toString();
                if (isDeviceMount(dirName)) {
                    knownMtpDevices.add(dirName);
                    log.info("  Pre-existing device mount detected (will NOT re-crawl): {}", extractDeviceName(dirName));
                }
            }
        } catch (Exception e) {
            log.debug("Could not scan existing GVFS mounts: {}", e.getMessage());
        }
    }
}
