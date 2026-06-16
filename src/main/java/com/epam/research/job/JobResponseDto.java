package com.epam.research.job;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record JobResponseDto(
        Long id,
        String topic,
        Map<String, String> clarificationAnswers,
        JobStatus status,
        String report,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<JobStageDto> stages,
        long totalInputTokens,
        long totalOutputTokens,
        String modelId
) {
    static JobResponseDto from(Job job, List<JobStageDto> stages, String modelId) {
        return new JobResponseDto(
                job.getId(),
                job.getTopic(),
                job.getClarificationAnswers(),
                job.getStatus(),
                job.getReport(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                stages,
                job.getTotalInputTokens(),
                job.getTotalOutputTokens(),
                modelId
        );
    }
}
