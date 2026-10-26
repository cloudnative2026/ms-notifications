package com.cloudnative.ms_notifications.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    // detenemos el stream a los 10seg para evitar 503 ya q el api gateway no
    // soporta streams
    private static final long TIMEOUT = Duration.ofSeconds(10).toMillis();

    private final Map<String, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

    // Avisos que llegaron mientras el usuario no tenia stream abierto (p. ej.
    // reconectando).
    private final Map<String, List<Message>> pending = new HashMap<>();

    public synchronized SseEmitter open(String user) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        List<Message> queued = pending.remove(user);
        if (queued != null) {
            deliver(emitter, queued);
            return emitter;
        }
        Set<SseEmitter> userStreams = streams.computeIfAbsent(user, u -> ConcurrentHashMap.newKeySet());
        userStreams.add(emitter);
        emitter.onCompletion(() -> userStreams.remove(emitter));
        emitter.onTimeout(() -> {
            userStreams.remove(emitter);
            // Sin complete(), Spring responde el timeout con 503.
            emitter.complete();
        });
        emitter.onError(e -> userStreams.remove(emitter));
        return emitter;
    }

    public synchronized void send(String user, Message m) {
        if (user == null) {
            return;
        }
        Set<SseEmitter> userStreams = streams.remove(user);
        if (userStreams == null || userStreams.isEmpty()) {
            pending.computeIfAbsent(user, u -> new ArrayList<>()).add(m);
            return;
        }
        for (SseEmitter emitter : userStreams) {
            deliver(emitter, List.of(m));
        }
    }

    private void deliver(SseEmitter emitter, List<Message> messages) {
        try {
            for (Message m : messages) {
                emitter.send(SseEmitter.event().name("notification").data(m));
            }
            // API Gateway entrega la respuesta solo al cerrarse: cerramos para que llegue ya.
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}

            // 