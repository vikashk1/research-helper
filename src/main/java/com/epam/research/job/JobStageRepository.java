package com.epam.research.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobStageRepository extends JpaRepository<JobStage, Long> {
    List<JobStage> findByJobIdOrderByStartedAtAsc(Long jobId);
    void deleteAllByJobId(Long jobId);
}
