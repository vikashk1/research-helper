package com.epam.research.job;

import com.epam.research.agent.ClarificationAgent;
import com.epam.research.sse.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    public JobStage startStage(Long jobId, PipelineStage stage) {
        Job job = getJob(jobId);
        JobStage jobStage = new JobStage();
        jobStage.setJob(job);
        jobStage.setStage(stage);
        jobStage.setStatus(JobStage.StageStatus.RUNNING);
        jobStage.setStartedAt(LocalDateTime.now());
        JobStage saved = jobStageRepository.save(jobStage);
        log.debug("Job {} stage {} RUNNING", jobId, stage);
        return saved;
    }

    public void completeStage(Long jobId, JobStage jobStage, long elapsedMs) {
        jobStage.setStatus(JobStage.StageStatus.COMPLETED);
        jobStage.setCompletedAt(LocalDateTime.now());
        jobStage.setElapsedMs(elapsedMs);
        jobStageRepository.save(jobStage);
        log.debug("Job {} stage {} COMPLETED in {}ms", jobId, jobStage.getStage(), elapsedMs);
    }

    public void failStage(Long jobId, JobStage jobStage, long elapsedMs) {
        jobStage.setStatus(JobStage.StageStatus.FAILED);
        jobStage.setCompletedAt(LocalDateTime.now());
        jobStage.setElapsedMs(elapsedMs);
        jobStageRepository.save(jobStage);
        log.debug("Job {} stage {} FAILED after {}ms", jobId, jobStage.getStage(), elapsedMs);
    }

    public List<JobStage> getStages(Long jobId) {
        getJob(jobId); // validate existence
        return jobStageRepository.findByJobIdOrderByStartedAtAsc(jobId);
    }
}
