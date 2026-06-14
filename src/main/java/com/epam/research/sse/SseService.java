package com.epam.research.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseService {

    private final ConcurrentHashMap<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseEmitter register(Long jobId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        List<SseEmitter> list = emitters.computeIfAbsent(jobId, id -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        log.info("SSE client registered for job {}, total clients: {}", jobId, list.size());
        return emitter;
    }

    public void emit(Long jobId, String message) {
        sendEvent(jobId, "log", message);
    }

    public void emitStage(Long jobId, StageEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            sendEvent(jobId, "stage", json);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise StageEvent for job {}: {}", jobId, e.getMessage());
        }
    }

    public void complete(Long jobId) {
        List<SseEmitter> jobEmitters = emitters.remove(jobId);
        if (jobEmitters == null) {
            log.debug("No SSE clients to complete for job {}", jobId);
            return;
        }
        log.info("Completing {} SSE client(s) for job {}", jobEmitters.size(), jobId);
        for (SseEmitter emitter : jobEmitters) {
            emitter.complete();
        }
    }

    private void sendEvent(Long jobId, String eventName, String data) {
        List<SseEmitter> jobEmitters = emitters.get(jobId);
        if (jobEmitters == null) {
            log.debug("No SSE clients for job {}, skipping emit", jobId);
            return;
        }
        for (SseEmitter emitter : jobEmitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                log.debug("SSE emitter for job {} disconnected, skipping: {}", jobId, e.getMessage());
            }
        }
    }
}
