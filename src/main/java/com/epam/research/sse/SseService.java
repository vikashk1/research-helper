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
        emitters.computeIfAbsent(jobId, id -> new CopyOnWriteArrayList<>()).add(emitter);
        return emitter;
    }

    public void emit(Long jobId, String message) {
        List<SseEmitter> jobEmitters = emitters.get(jobId);
        if (jobEmitters == null) {
            return;
        }
        for (SseEmitter emitter : jobEmitters) {
            try {
                emitter.send(SseEmitter.event().data(message));
            } catch (IOException e) {
                log.debug("Emitter for job {} already completed, skipping", jobId);
            }
        }
    }

    public void complete(Long jobId) {
        List<SseEmitter> jobEmitters = emitters.remove(jobId);
        if (jobEmitters == null) {
            return;
        }
        for (SseEmitter emitter : jobEmitters) {
            emitter.complete();
        }
    }
}
