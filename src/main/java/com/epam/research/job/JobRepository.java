package com.epam.research.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findAllByStatus(JobStatus status);

    @Modifying
    @Query("UPDATE Job j SET j.totalInputTokens = j.totalInputTokens + :in, j.totalOutputTokens = j.totalOutputTokens + :out WHERE j.id = :id")
    void addTokenUsage(@Param("id") Long id, @Param("in") long in, @Param("out") long out);
}
