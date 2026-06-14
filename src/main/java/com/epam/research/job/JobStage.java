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

    public long elapsedMs() {
        if (startedAt == null) return 0;
        LocalDateTime end = completedAt != null ? completedAt : LocalDateTime.now();
        return java.time.Duration.between(startedAt, end).toMillis();
    }
}
