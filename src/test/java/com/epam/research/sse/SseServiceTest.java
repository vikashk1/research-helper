package com.epam.research.sse;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;

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

    @Test
    void should_notThrow_when_emitStageCalledOnUnknownJob() {
        assertThatNoException().isThrownBy(() -> sseService.emitStage(99L, "{\"stage\":\"search\"}"));
    }

    @Test
    void should_sendEventWithNameStage_when_emitStageCalled() throws Exception {
        SseEmitter mockEmitter = mock(SseEmitter.class);

        // Inject mock emitter into the service's internal emitters map via reflection
        Field emittersField = SseService.class.getDeclaredField("emitters");
        emittersField.setAccessible(true);
        ConcurrentHashMap<Long, List<SseEmitter>> emittersMap =
                (ConcurrentHashMap<Long, List<SseEmitter>>) emittersField.get(sseService);
        List<SseEmitter> emitterList = new CopyOnWriteArrayList<>();
        emitterList.add(mockEmitter);
        emittersMap.put(30L, emitterList);

        sseService.emitStage(30L, "{\"stage\":\"search\",\"type\":\"start\"}");

        // Production code calls ResponseBodyEmitter.send(Set<DataWithMediaType>).
        // Retrieve the argument from Mockito's invocation log to avoid overload-resolution
        // ambiguity between send(Object) and send(SseEventBuilder) at the verify site.
        var invocations = Mockito.mockingDetails(mockEmitter).getInvocations();
        assertThat(invocations).hasSize(1);
        Object arg = invocations.iterator().next().getArgument(0);
        assertThat(arg).isInstanceOf(Set.class);
        @SuppressWarnings("unchecked")
        Set<DataWithMediaType> data = (Set<DataWithMediaType>) arg;

        // Each DataWithMediaType carries one SSE wire-format chunk; together they encode
        // the event name and data lines (e.g. "event:stage\ndata:...\n\n").
        String wireText = data.stream()
                .map(dwmt -> dwmt.getData().toString())
                .reduce("", String::concat);
        assertThat(wireText).contains("event:stage");
    }
}
