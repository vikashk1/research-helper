package com.epam.research.sse;

import com.epam.research.job.PipelineStage;

/**
 * Structured SSE payload for pipeline stage progress.
 * Serialised to JSON and sent with event name "stage".
 */
public record StageEvent(
        PipelineStage stage,
        StageStatus status,
        long elapsedMs
) {
    public enum StageStatus {
        STARTED,
        COMPLETED,
        FAILED
    }
}
