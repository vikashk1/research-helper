package com.epam.research.job;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PipelineStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStageStatus status;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;
}
