package com.epam.research.job;

import java.time.LocalDateTime;

public record JobStageDto(
        PipelineStage stage,
        JobStageStatus status,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
    static JobStageDto from(JobStage s) {
        return new JobStageDto(s.getStage(), s.getStatus(), s.getStartedAt(), s.getEndedAt());
    }
}
