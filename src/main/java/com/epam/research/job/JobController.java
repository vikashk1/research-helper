package com.epam.research.job;

import com.epam.research.agent.CoordinatorAgent;
import com.epam.research.sse.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

@Slf4j
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final CoordinatorAgent coordinatorAgent;
    private final SseService sseService;
    private final JobFutureRegistry jobFutureRegistry;

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
        Future<Void> future = coordinatorAgent.runPipeline(job.getId(), topic, clarificationAnswers);
        jobFutureRegistry.register(job.getId(), future);
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

    @PostMapping("/{id}/restart")
    public Job restartJob(@PathVariable Long id) {
        log.info("POST /api/jobs/{}/restart", id);
        Job job = jobService.restartJob(id);
        Future<Void> future = coordinatorAgent.runPipeline(job.getId(), job.getTopic(), job.getClarificationAnswers());
        jobFutureRegistry.register(job.getId(), future);
        log.debug("Pipeline re-dispatched for job {}", id);
        return job;
    }

    @PostMapping("/{id}/cancel")
    public Job cancelJob(@PathVariable Long id) {
        log.info("POST /api/jobs/{}/cancel", id);
        return jobService.cancelJob(id);
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(@PathVariable Long id) {
        log.debug("GET /api/jobs/{}/stream - SSE client connected", id);
        return sseService.register(id);
    }
}
