package com.epam.research.job;

import com.epam.research.agent.CoordinatorAgent;
import com.epam.research.sse.SseService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final CoordinatorAgent coordinatorAgent;
    private final SseService sseService;

    @PostMapping("/clarify")
    public List<String> getClarificationQuestions(@RequestBody Map<String, String> body) {
        String topic = body.get("topic");
        log.info("POST /api/jobs/clarify - topic: '{}'", topic);
        return jobService.getClarificationQuestions(topic);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SuppressWarnings("unchecked")
    public Job createJob(@RequestBody Map<String, Object> body) {
        String topic = (String) body.get("topic");
        Map<String, String> clarificationAnswers = (Map<String, String>) body.get("clarificationAnswers");
        log.info("POST /api/jobs - topic: '{}'", topic);
        Job job = jobService.createJob(topic, clarificationAnswers);
        coordinatorAgent.runPipeline(job.getId(), topic, clarificationAnswers);
        log.debug("Pipeline dispatched for job {}", job.getId());
        return job;
    }

    @GetMapping
    public List<Job> getAllJobs() {
        log.debug("GET /api/jobs");
        return jobService.getAllJobs();
    }

    @GetMapping("/{id}")
    public Job getJob(@PathVariable Long id) {
        log.debug("GET /api/jobs/{}", id);
        return jobService.getJob(id);
    }

    @Operation(summary = "Delete a job and its associated logs by ID")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJob(@PathVariable Long id) {
        log.info("DELETE /api/jobs/{}", id);
        jobService.deleteJob(id);
    }

    @Operation(summary = "Delete all completed jobs and their associated logs")
    @DeleteMapping("/completed")
    public Map<String, Integer> deleteAllCompleted() {
        log.info("DELETE /api/jobs/completed");
        int count = jobService.deleteAllCompleted();
        return Map.of("deleted", count);
    }

    @PostMapping("/{id}/restart")
    public Job restartJob(@PathVariable Long id) {
        log.info("POST /api/jobs/{}/restart", id);
        Job job = jobService.restartJob(id);
        coordinatorAgent.runPipeline(job.getId(), job.getTopic(), job.getClarificationAnswers());
        log.debug("Pipeline re-dispatched for job {}", id);
        return job;
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(@PathVariable Long id) {
        log.debug("GET /api/jobs/{}/stream - SSE client connected", id);
        return sseService.register(id);
    }
}
