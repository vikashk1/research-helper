package com.epam.research.job;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    // POST /api/jobs/clarify        — get clarifying questions for a topic
    // POST /api/jobs                — create a job and start the pipeline
    // GET  /api/jobs                — list all jobs
    // GET  /api/jobs/{id}           — get job details and report
    // GET  /api/jobs/{id}/stream    — SSE stream for live log updates

    @PostMapping("/clarify")
    public List<String> getClarificationQuestions(@RequestBody Map<String, String> body) {
        // TODO: Delegate to JobService
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PostMapping
    public Job createJob(@RequestBody Map<String, Object> body) {
        // TODO: Delegate to JobService
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GetMapping
    public List<Job> getAllJobs() {
        // TODO: Delegate to JobService
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GetMapping("/{id}")
    public Job getJob(@PathVariable Long id) {
        // TODO: Delegate to JobService
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(@PathVariable Long id) {
        // TODO: Register and return SSE emitter for this job
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
