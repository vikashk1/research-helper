package com.epam.research.agent;

import com.epam.research.job.JobService;
import com.epam.research.sse.SseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CoordinatorAgent {

    private final JobService jobService;
    private final WebSearchAgent webSearchAgent;
    private final SummarizerAgent summarizerAgent;
    private final ReportFormatterAgent reportFormatterAgent;
    private final SseService sseService;
    private final long retryDelayMs;

    public CoordinatorAgent(
            JobService jobService,
            WebSearchAgent webSearchAgent,
            SummarizerAgent summarizerAgent,
            ReportFormatterAgent reportFormatterAgent,
            SseService sseService,
            @Value("${coordinator.retry.delay-ms:1000}") long retryDelayMs) {
        this.jobService = jobService;
        this.webSearchAgent = webSearchAgent;
        this.summarizerAgent = summarizerAgent;
        this.reportFormatterAgent = reportFormatterAgent;
        this.sseService = sseService;
        this.retryDelayMs = retryDelayMs;
    }

    @Async
    public void runPipeline(Long jobId, String topic, Map<String, String> clarificationAnswers) {
        try {
            jobService.markInProgress(jobId);

            String context = clarificationAnswers.entrySet().stream()
                    .map(e -> "Q: " + e.getKey() + "\nA: " + e.getValue())
                    .collect(Collectors.joining("\n\n"));

            String rawResults = withRetry(() -> webSearchAgent.search(topic, context));
            jobService.appendLog(jobId, "Search complete");

            String summary = withRetry(() -> summarizerAgent.summarize(topic, context, rawResults));
            jobService.appendLog(jobId, "Summary complete");

            String report = withRetry(() -> reportFormatterAgent.format(topic, context, summary));

            jobService.markCompleted(jobId, report);
            sseService.complete(jobId);
        } catch (Exception e) {
            log.error("Pipeline failed for job {}", jobId, e);
            jobService.markFailed(jobId, e.getMessage());
            sseService.complete(jobId);
        }
    }

    private <T> T withRetry(Callable<T> action) throws Exception {
        int attempts = 3;
        long delay = retryDelayMs;
        Exception last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                return action.call();
            } catch (Exception e) {
                last = e;
                if (i < attempts - 1 && delay > 0) Thread.sleep(delay);
                delay *= 2;
            }
        }
        throw last;
    }
}
