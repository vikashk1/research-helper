package com.epam.research.sse;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class SseService {

    public SseEmitter register(Long jobId) {
        // TODO: Create and store SseEmitter keyed by jobId
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void emit(Long jobId, String message) {
        // TODO: Push log message to all active emitters for this jobId
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void complete(Long jobId) {
        // TODO: Complete and remove emitters for this jobId
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
