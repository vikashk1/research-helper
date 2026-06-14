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

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobLogRepository jobLogRepository;
    private final ClarificationAgent clarificationAgent;
    private final SseService sseService;
    private final JobFutureRegistry jobFutureRegistry;

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
        job.setStatus(JobStatus.PENDING);
        job.setReport(null);
        job.setErrorMessage(null);
        Job saved = jobRepository.save(job);
        log.info("Job {} restarted", jobId);
        return saved;
    }

    public Job cancelJob(Long jobId) {
        Job job = getJob(jobId);
        Set<JobStatus> cancellable = Set.of(JobStatus.PENDING, JobStatus.IN_PROGRESS);
        if (!cancellable.contains(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Job " + jobId + " cannot be cancelled in status: " + job.getStatus());
        }
        jobFutureRegistry.cancel(jobId);
        job.setStatus(JobStatus.CANCELLED);
        job.setErrorMessage("Cancelled by user");
        Job saved = jobRepository.save(job);
        sseService.complete(jobId);
        log.info("Job {} status -> CANCELLED", jobId);
        return saved;
    }
}
