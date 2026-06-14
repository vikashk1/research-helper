package com.epam.research.agent;

import com.epam.research.job.JobService;
import com.epam.research.job.JobStage;
import com.epam.research.job.PipelineStage;
import com.epam.research.sse.SseService;
import com.epam.research.sse.StageEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
        MDC.put("jobId", String.valueOf(jobId));
        long pipelineStart = System.currentTimeMillis();
        try {
            log.info("Pipeline started for job {} with topic: '{}'", jobId, topic);
            jobService.markInProgress(jobId);

            String context = clarificationAnswers.entrySet().stream()
                    .map(e -> "Q: " + e.getKey() + "\nA: " + e.getValue())
                    .collect(Collectors.joining("\n\n"));

            log.debug("Clarification context built ({} Q&A pairs)", clarificationAnswers.size());

            String rawResults = runStage(jobId, PipelineStage.SEARCH,
                    () -> webSearchAgent.search(topic, context));
            jobService.appendLog(jobId, "Search complete");

            String summary = runStage(jobId, PipelineStage.SUMMARIZE,
                    () -> summarizerAgent.summarize(topic, context, rawResults));
            jobService.appendLog(jobId, "Summary complete");

            String report = runStage(jobId, PipelineStage.FORMAT,
                    () -> reportFormatterAgent.format(topic, context, summary));

            jobService.markCompleted(jobId, report);
            sseService.complete(jobId);
            log.info("Pipeline completed for job {} in {}ms", jobId, System.currentTimeMillis() - pipelineStart);
        } catch (Exception e) {
            log.error("Pipeline failed for job {} after {}ms", jobId, System.currentTimeMillis() - pipelineStart, e);
            jobService.markFailed(jobId, e.getMessage());
            sseService.complete(jobId);
        } finally {
            MDC.remove("jobId");
        }
    }

    private <T> T runStage(Long jobId, PipelineStage stage, Callable<T> action) throws Exception {
        long stageStart = System.currentTimeMillis();
        JobStage jobStage = jobService.startStage(jobId, stage);
        sseService.emitStage(jobId, new StageEvent(stage, StageEvent.StageStatus.STARTED, 0));
        try {
            T result = withRetry(action);
            long elapsed = System.currentTimeMillis() - stageStart;
            jobService.completeStage(jobId, jobStage, elapsed);
            sseService.emitStage(jobId, new StageEvent(stage, StageEvent.StageStatus.COMPLETED, elapsed));
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - stageStart;
            jobService.failStage(jobId, jobStage, elapsed);
            sseService.emitStage(jobId, new StageEvent(stage, StageEvent.StageStatus.FAILED, elapsed));
            throw e;
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
                if (i < attempts - 1) {
                    log.warn("Action failed on attempt {}/{}, retrying in {}ms: {}", i + 1, attempts, delay, e.getMessage());
                    if (delay > 0) Thread.sleep(delay);
                }
                delay *= 2;
            }
        }
        throw last;
    }
}
