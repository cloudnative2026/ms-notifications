package com.cloudnative.ms_notifications.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.cloudnative.ms_notifications.DTO.Message;

/**
 * Conexiones SSE abiertas, agrupadas por usuario (subject del JWT).
 * Un usuario puede tener varias pestanas abiertas; todas reciben el aviso.
 */
@Service
public class NotificationStreams {

    // El frontend reconecta al expirar, con un token fresco.
    private static final long TIMEOUT = Duration.ofMinutes(30).toMillis();

    private final Map<String, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

    public SseEmitter open(String user) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        Set<SseEmitter> userStreams = streams.computeIfAbsent(user, u -> ConcurrentHashMap.newKeySet());
        userStreams.add(emitter);
        emitter.onCompletion(() -> userStreams.remove(emitter));
        emitter.onTimeout(() -> userStreams.remove(emitter));
        emitter.onError(e -> userStreams.remove(emitter));
        return emitter;
    }

    public void send(String user, Message m) {
        if (user == null) {
            return;
        }
        for (SseEmitter emitter : streams.getOrDefault(user, Set.of())) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(m));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }
    }
}
