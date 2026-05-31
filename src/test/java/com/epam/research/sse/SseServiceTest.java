package com.epam.research.sse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class SseServiceTest {

    private final SseService sseService = new SseService();

    @Test
    void should_returnNonNullEmitter_when_registering() {
        SseEmitter emitter = sseService.register(1L);
        assertThat(emitter).isNotNull();
    }

    @Test
    void should_returnNewEmitter_when_registeringTwiceForSameJob() {
        SseEmitter first = sseService.register(1L);
        SseEmitter second = sseService.register(1L);
        assertThat(first).isNotSameAs(second);
    }

    @Test
    void should_notThrow_when_emittingToUnknownJob() {
        assertThatNoException().isThrownBy(() -> sseService.emit(99L, "hello"));
    }

    @Test
    void should_notThrow_when_completingUnknownJob() {
        assertThatNoException().isThrownBy(() -> sseService.complete(99L));
    }

    @Test
    void should_notThrow_when_emittingAfterJobCompleted() {
        sseService.register(1L);
        sseService.complete(1L);
        assertThatNoException().isThrownBy(() -> sseService.emit(1L, "late message"));
    }

    @Test
    void should_removeEmitters_when_jobCompleted() {
        sseService.register(1L);
        sseService.complete(1L);
        // Re-registering after complete should still work cleanly
        SseEmitter fresh = sseService.register(1L);
        assertThat(fresh).isNotNull();
    }
}
