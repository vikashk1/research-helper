package com.epam.research.job;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobStageRepository extends JpaRepository<JobStage, Long> {
    void deleteAllByJobId(Long jobId);
}
