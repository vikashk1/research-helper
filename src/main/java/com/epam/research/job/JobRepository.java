package com.epam.research.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findAllByStatus(JobStatus status);

    @Modifying
    @Query("UPDATE Job j SET j.totalInputTokens = j.totalInputTokens + :input, j.totalOutputTokens = j.totalOutputTokens + :output WHERE j.id = :id")
    void addTokenUsage(@Param("id") Long id, @Param("input") long input, @Param("output") long output);
}
