package com.epam.research.agent;

import com.epam.research.job.Job;
import com.epam.research.job.JobFutureRegistry;
import com.epam.research.job.JobService;
import com.epam.research.job.JobStatus;
import com.epam.research.sse.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoordinatorAgentTest {

    @Mock private JobService jobService;
    @Mock private WebSearchAgent webSearchAgent;
    @Mock private SummarizerAgent summarizerAgent;
    @Mock private ReportFormatterAgent reportFormatterAgent;
    @Mock private SseService sseService;
    @Mock private JobFutureRegistry jobFutureRegistry;

    private CoordinatorAgent coordinatorAgent;

    private static final Long JOB_ID = 1L;
    private static final String TOPIC = "climate change";
    private static final Map<String, String> ANSWERS = Map.of(
            "What is the scope?", "global",
            "What time period?", "2020-2025"
    );

    @BeforeEach
    void setUp() {
        // retryDelayMs=0 so retry tests run instantly
        coordinatorAgent = new CoordinatorAgent(
                jobService, webSearchAgent, summarizerAgent, reportFormatterAgent, sseService, jobFutureRegistry, 0L);
        // Default: job is IN_PROGRESS (not CANCELLED) so failure paths proceed normally
        Job job = new Job();
        job.setId(JOB_ID);
        job.setStatus(JobStatus.IN_PROGRESS);
        when(jobService.getJob(JOB_ID)).thenReturn(job);
    }

    private void stubHappyPath() {
        when(webSearchAgent.search(anyString(), anyString())).thenReturn("raw results");
        when(summarizerAgent.summarize(anyString(), anyString(), anyString())).thenReturn("summary");
        when(reportFormatterAgent.format(anyString(), anyString(), anyString())).thenReturn("# Report");
    }

    @Test
    void should_markJobInProgress_when_pipelineStarts() {
        stubHappyPath();
        coordinatorAgent.runPipeline(JOB_ID, TOPIC, ANSWERS);
        verify(jobService).markInProgress(JOB_ID);
    }

    @Test
    void should_markJobCompleted_when_allAgentsSucceed() {
        stubHappyPath();
        coordinatorAgent.runPipeline(JOB_ID, TOPIC, ANSWERS);
        verify(jobService).markCompleted(JOB_ID, "# Report");
    }

    @Test
    void should_completeSseEmitter_when_pipelineSucceeds() {
        stubHappyPath();
        coordinatorAgent.runPipeline(JOB_ID, TOPIC, ANSWERS);
        verify(sseService).complete(JOB_ID);
    }

    @Test
    void should_retryThreeTimes_and_markFailed_when_searchAlwaysFails() {
        when(webSearchAgent.search(anyString(), anyString()))
                .thenThrow(new RuntimeException("search failed"));

        coordinatorAgent.runPipeline(JOB_ID, TOPIC, ANSWERS);

        verify(webSearchAgent, times(3)).search(anyString(), anyString());
        verify(jobService).markFailed(eq(JOB_ID), anyString());
        verify(sseService).complete(JOB_ID);
    }

    @Test
    void should_succeedAfterRetry_when_searchFailsOnFirstAttempt() {
        when(webSearchAgent.search(anyString(), anyString()))
                .thenThrow(new RuntimeException("temporary"))
                .thenReturn("raw results");
        when(summarizerAgent.summarize(anyString(), anyString(), anyString())).thenReturn("summary");
        when(reportFormatterAgent.format(anyString(), anyString(), anyString())).thenReturn("# Report");

        coordinatorAgent.runPipeline(JOB_ID, TOPIC, ANSWERS);

        verify(webSearchAgent, times(2)).search(anyString(), anyString());
        verify(jobService).markCompleted(JOB_ID, "# Report");
    }

    @Test
    void should_callAgentsInOrderWithCorrectData_when_pipelineRuns() {
        stubHappyPath();
        coordinatorAgent.runPipeline(JOB_ID, TOPIC, ANSWERS);

        verify(webSearchAgent).search(eq(TOPIC), anyString());
        verify(summarizerAgent).summarize(eq(TOPIC), anyString(), eq("raw results"));
        verify(reportFormatterAgent).format(eq(TOPIC), anyString(), eq("summary"));
    }

    @Test
    void should_includeClarificationAnswers_when_buildingContextForAgents() {
        stubHappyPath();
        coordinatorAgent.runPipeline(JOB_ID, TOPIC, ANSWERS);

        verify(webSearchAgent).search(anyString(), contains("global"));
    }
}
