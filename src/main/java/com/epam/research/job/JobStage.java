package com.epam.research.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class JobStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PipelineStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StageStatus status;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    /** Elapsed duration in milliseconds; populated when stage completes or fails. */
    private Long elapsedMs;

    @PrePersist
    void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }

    public enum StageStatus {
        RUNNING,
        COMPLETED,
        FAILED
    }
}
