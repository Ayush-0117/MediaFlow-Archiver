package com.mediaflow.archiver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class SseBroadcastService {
    
    // A thread-safe list to hold open connections to the React Tauri window
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public void addEmitter(SseEmitter emitter) {
        emitters.add(emitter);
        // Clean up connections if the user closes the React app or network drops
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
    }

    /**
     * Fires a live string directly into the React UI without requiring React to manually refresh.
     */
    public void broadcast(String message) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("ingestion-progress").data(message));
            } catch (IOException e) {
                // If network connection died, silently remove the dead emitter
                emitters.remove(emitter);
            }
        }
    }
}
