package com.epam.research.job;

import com.epam.research.agent.ClarificationAgent;
import com.epam.research.sse.SseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobLogRepository jobLogRepository;
    private final JobStageRepository jobStageRepository;
    private final ClarificationAgent clarificationAgent;
    private final SseService sseService;
    private final ObjectMapper objectMapper;

    public List<String> getClarificationQuestions(String topic) {
        return clarificationAgent.generateQuestions(topic);
    }

    public Job createJob(String topic, Map<String, String> clarificationAnswers) {
        Job job = new Job();
        job.setTopic(topic);
        job.setClarificationAnswers(clarificationAnswers);
        job.setStatus(JobStatus.PENDING);
        Job saved = jobRepository.save(job);
        log.info("Job {} created for topic: '{}'", saved.getId(), topic);
        return saved;
    }

    public Job getJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> {
                    log.warn("Job {} not found", jobId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId);
                });
    }

    @Transactional(readOnly = true)
    public JobResponseDto getJobResponse(Long jobId) {
        Job job = getJob(jobId);
        List<JobStageDto> stageDtos = jobStageRepository.findAllByJobIdOrderByIdAsc(jobId)
                .stream()
                .map(JobStageDto::from)
                .toList();
        return JobResponseDto.from(job, stageDtos);
    }

    public List<Job> getAllJobs() {
        List<Job> jobs = jobRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        log.debug("Retrieved {} jobs", jobs.size());
        return jobs;
    }

    public void appendLog(Long jobId, String message) {
        Job job = getJob(jobId);
        JobLog jobLog = new JobLog();
        jobLog.setJob(job);
        jobLog.setMessage(message);
        jobLogRepository.save(jobLog);
        sseService.emit(jobId, message);
        log.debug("Job {} log appended: '{}'", jobId, message);
    }

    public void markInProgress(Long jobId) {
        Job job = getJob(jobId);
        job.setStatus(JobStatus.IN_PROGRESS);
        jobRepository.save(job);
        log.info("Job {} status -> IN_PROGRESS", jobId);
    }

    public void markCompleted(Long jobId, String report) {
        Job job = getJob(jobId);
        job.setStatus(JobStatus.COMPLETED);
        job.setReport(report);
        jobRepository.save(job);
        log.info("Job {} status -> COMPLETED, report length: {} chars", jobId, report.length());
    }

    public void markFailed(Long jobId, String reason) {
        Job job = getJob(jobId);
        job.setStatus(JobStatus.FAILED);
        job.setErrorMessage(reason);
        jobRepository.save(job);
        log.warn("Job {} status -> FAILED, reason: {}", jobId, reason);
    }

    @Transactional
    public void appendStageEvent(Long jobId, String stage, String type, String message) {
        Job job = getJob(jobId);
        PipelineStage pipelineStage;
        try {
            pipelineStage = PipelineStage.valueOf(stage.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown stage: " + stage);
        }

        JobStage jobStage = jobStageRepository.findByJobIdAndStage(jobId, pipelineStage)
                .orElseGet(() -> {
                    JobStage s = new JobStage();
                    s.setJob(job);
                    s.setStage(pipelineStage);
                    s.setStatus(JobStageStatus.PENDING);
                    return s;
                });

        LocalDateTime now = LocalDateTime.now();
        long elapsed;

        switch (type) {
            case "start" -> {
                jobStage.setStatus(JobStageStatus.ACTIVE);
                jobStage.setStartedAt(now);
                elapsed = 0;
            }
            case "end" -> {
                jobStage.setStatus(JobStageStatus.COMPLETED);
                jobStage.setEndedAt(now);
                elapsed = jobStage.getStartedAt() != null
                        ? Duration.between(jobStage.getStartedAt(), now).toMillis()
                        : 0;
            }
            case "activity" -> {
                elapsed = jobStage.getStartedAt() != null
                        ? Duration.between(jobStage.getStartedAt(), now).toMillis()
                        : 0;
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown type: " + type);
        }

        jobStageRepository.save(jobStage);

        String json;
        try {
            json = objectMapper.writeValueAsString(Map.of(
                    "stage", stage,
                    "type", type,
                    "message", message,
                    "elapsed", elapsed
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize stage event", e);
        }
        sseService.emitStage(jobId, json);
        log.debug("Job {} stage event: stage={}, type={}, elapsed={}ms", jobId, stage, type, elapsed);
    }

    @Transactional
    public void addTokenUsage(Long jobId, long inputTokens, long outputTokens) {
        Job job = getJob(jobId);
        job.setTotalInputTokens(job.getTotalInputTokens() + inputTokens);
        job.setTotalOutputTokens(job.getTotalOutputTokens() + outputTokens);
        jobRepository.save(job);
        log.debug("Job {} token usage updated: +{} input, +{} output tokens", jobId, inputTokens, outputTokens);
    }

    @Transactional
    public void deleteJob(Long jobId) {
        Job job = getJob(jobId);
        jobLogRepository.deleteAllByJobId(jobId);
        jobStageRepository.deleteAllByJobId(jobId);
        jobRepository.delete(job);
        log.info("Job {} deleted", jobId);
    }

    @Transactional
    public int deleteAllCompleted() {
        List<Job> completed = jobRepository.findAllByStatus(JobStatus.COMPLETED);
        for (Job job : completed) {
            jobLogRepository.deleteAllByJobId(job.getId());
            jobStageRepository.deleteAllByJobId(job.getId());
        }
        jobRepository.deleteAll(completed);
        log.info("Deleted {} completed jobs", completed.size());
        return completed.size();
    }

    @Transactional
    public Job restartJob(Long jobId) {
        Job job = getJob(jobId);
        jobLogRepository.deleteAllByJobId(jobId);
        jobStageRepository.deleteAllByJobId(jobId);
        job.setStatus(JobStatus.PENDING);
        job.setReport(null);
        job.setErrorMessage(null);
        Job saved = jobRepository.save(job);
        log.info("Job {} restarted", jobId);
        return saved;
    }
}
