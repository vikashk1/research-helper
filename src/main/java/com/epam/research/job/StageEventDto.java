package com.epam.research.job;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StageEventDto {
    private final PipelineStage stage;
    private final StageStatus status;
    private final long elapsedMs;
}
