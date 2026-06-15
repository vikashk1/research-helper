package com.epam.research.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobStageRepository extends JpaRepository<JobStage, Long> {
    void deleteAllByJobId(Long jobId);
    Optional<JobStage> findByJobIdAndStage(Long jobId, PipelineStage stage);
}
