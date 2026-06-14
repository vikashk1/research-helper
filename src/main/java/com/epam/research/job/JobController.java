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

    @Operation(summary = "Get clarification questions for a research topic")
    @PostMapping("/clarify")
    public List<String> getClarificationQuestions(@RequestBody Map<String, String> body) {
        String topic = body.get("topic");
        log.info("POST /api/jobs/clarify - topic: '{}'", topic);
        return jobService.getClarificationQuestions(topic);
    }

    @Operation(summary = "Create a new research job and start the pipeline")
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

    @Operation(summary = "List all research jobs")
    @GetMapping
    public List<Job> getAllJobs() {
        log.debug("GET /api/jobs");
        return jobService.getAllJobs();
    }

    @Operation(summary = "Get a research job by ID")
    @GetMapping("/{id}")
    public Job getJob(@PathVariable Long id) {
        log.debug("GET /api/jobs/{}", id);
        return jobService.getJob(id);
    }

    @Operation(summary = "Restart a failed or completed research job")
    @PostMapping("/{id}/restart")
    public Job restartJob(@PathVariable Long id) {
        log.info("POST /api/jobs/{}/restart", id);
        Job job = jobService.restartJob(id);
        coordinatorAgent.runPipeline(job.getId(), job.getTopic(), job.getClarificationAnswers());
        log.debug("Pipeline re-dispatched for job {}", id);
        return job;
    }

    @Operation(summary = "Stream live log events for a job via SSE")
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(@PathVariable Long id) {
        log.debug("GET /api/jobs/{}/stream - SSE client connected", id);
        return sseService.register(id);
    }

    @Operation(summary = "Get pipeline stage progress for a job")
    @GetMapping("/{id}/stages")
    public List<JobStage> getStages(@PathVariable Long id) {
        log.debug("GET /api/jobs/{}/stages", id);
        return jobService.getStages(id);
    }
}
