package com.epam.research.job;

import com.epam.research.agent.ClarificationAgent;
import com.epam.research.sse.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobLogRepository jobLogRepository;
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
        return jobRepository.save(job);
    }

    public Job getJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId));
    }

    public List<Job> getAllJobs() {
        return jobRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public void appendLog(Long jobId, String message) {
        Job job = getJob(jobId);
        JobLog log = new JobLog();
        log.setJob(job);
        log.setMessage(message);
        jobLogRepository.save(log);
        sseService.emit(jobId, message);
    }
}
