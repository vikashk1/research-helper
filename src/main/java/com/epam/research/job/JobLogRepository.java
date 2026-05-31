package com.epam.research.job;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobLogRepository extends JpaRepository<JobLog, Long> {
    void deleteAllByJobId(Long jobId);
}
