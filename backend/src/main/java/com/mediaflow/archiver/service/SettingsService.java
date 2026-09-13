package com.mediaflow.archiver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central Settings Service — the single source of truth for all user-configurable preferences.
 * 
 * Settings are persisted to ~/.mediaflow/config.json so they survive restarts.
 * On first launch, defaults come from application.properties.
 * The frontend can read/write settings via GET/PUT /api/settings.
 */
@Service
@Slf4j
public class SettingsService {

    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".mediaflow");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    private final ObjectMapper mapper = new ObjectMapper();

    /** Default from application.properties — used only on first launch */
    @Value("${archiver.root.path:/tmp/archive}")
    private String defaultArchivePath;

    /** The live, mutable settings map */
    private final Map<String, String> settings = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                // Load existing config
                @SuppressWarnings("unchecked")
                Map<String, String> loaded = mapper.readValue(CONFIG_FILE.toFile(), Map.class);
                settings.putAll(loaded);
                log.info("Loaded user settings from {}", CONFIG_FILE);
            } else {
                // First launch — seed with defaults
                settings.put("archivePath", defaultArchivePath);
                save();
                log.info("Created default settings at {}", CONFIG_FILE);
            }
        } catch (IOException e) {
            log.error("Failed to load settings, using defaults: {}", e.getMessage());
            settings.put("archivePath", defaultArchivePath);
        }

        // Ensure archive directory exists
        try {
            Path archivePath = Paths.get(getArchivePath());
            if (!Files.exists(archivePath)) {
                Files.createDirectories(archivePath);
                log.info("Created archive directory: {}", archivePath);
            }
        } catch (IOException e) {
            log.warn("Could not create archive directory: {}", e.getMessage());
        }
    }

    /**
     * Returns the current archive root path. This is THE method all services should call
     * instead of reading their own @Value("archiver.root.path").
     */
    public String getArchivePath() {
        return settings.getOrDefault("archivePath", defaultArchivePath);
    }

    /**
     * Returns all current settings as an immutable snapshot.
     */
    public Map<String, String> getAll() {
        return Map.copyOf(settings);
    }

    /**
     * Updates one or more settings and persists to disk.
     * Returns the full updated settings map.
     */
    public Map<String, String> update(Map<String, String> updates) throws IOException {
        // Validate archivePath if being changed
        if (updates.containsKey("archivePath")) {
            String newPath = updates.get("archivePath");
            if (newPath == null || newPath.isBlank()) {
                throw new IllegalArgumentException("Archive path cannot be empty.");
            }
            Path target = Paths.get(newPath);
            if (!Files.exists(target)) {
                Files.createDirectories(target);
                log.info("Created new archive directory: {}", target);
            }
            if (!Files.isWritable(target)) {
                throw new IllegalArgumentException("Archive path is not writable: " + newPath);
            }
        }

        settings.putAll(updates);
        save();
        log.info("Settings updated: {}", updates.keySet());
        return Map.copyOf(settings);
    }

    private void save() throws IOException {
        if (!Files.exists(CONFIG_DIR)) {
            Files.createDirectories(CONFIG_DIR);
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(CONFIG_FILE.toFile(), settings);
    }
}
