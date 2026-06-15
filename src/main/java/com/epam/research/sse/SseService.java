package com.epam.research.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class SseService {

    private final ConcurrentHashMap<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(Long jobId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        List<SseEmitter> list = emitters.computeIfAbsent(jobId, id -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        log.info("SSE client registered for job {}, total clients: {}", jobId, list.size());
        return emitter;
    }

    public void emit(Long jobId, String message) {
        sendToEmitters(jobId, SseEmitter.event().data(message));
    }

    public void emitStage(Long jobId, String json) {
        sendToEmitters(jobId, SseEmitter.event().name("stage").data(json));
    }

    private void sendToEmitters(Long jobId, SseEmitter.SseEventBuilder event) {
        List<SseEmitter> jobEmitters = emitters.get(jobId);
        if (jobEmitters == null) {
            log.debug("No SSE clients for job {}, skipping emit", jobId);
            return;
        }
        for (SseEmitter emitter : jobEmitters) {
            try {
                emitter.send(event);
            } catch (IOException e) {
                log.debug("SSE emitter for job {} disconnected, skipping: {}", jobId, e.getMessage());
            }
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
}
