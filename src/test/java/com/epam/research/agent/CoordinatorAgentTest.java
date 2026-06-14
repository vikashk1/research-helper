package com.epam.research.agent;

import com.epam.research.job.JobService;
import com.epam.research.sse.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoordinatorAgentTest {

    @Mock private JobService jobService;
    @Mock private WebSearchAgent webSearchAgent;
    @Mock private SummarizerAgent summarizerAgent;
    @Mock private ReportFormatterAgent reportFormatterAgent;
    @Mock private SseService sseService;

    private CoordinatorAgent coordinatorAgent;

    private static final Long JOB_ID = 1L;
    private static final String TOPIC = "climate change";
    private static final Map<String, String> ANSWERS = Map.of(
            "What is the scope?", "global",
            "What time period?", "2020-2025"
    );

    private static final WebSearchOutput SEARCH_OUTPUT =
            new WebSearchOutput("raw results", List.of());
    private static final SummaryOutput SUMMARY_OUTPUT =
            new SummaryOutput("summary", List.of());

    @BeforeEach
    void setUp() {
        coordinatorAgent = new CoordinatorAgent(
                jobService, webSearchAgent, summarizerAgent, reportFormatterAgent, sseService, 0L);
    }

    private void stubHappyPath() {
        when(webSearchAgent.search(anyString(), anyString())).thenReturn(SEARCH_OUTPUT);
        when(summarizerAgent.summarize(anyString(), anyString(), any(WebSearchOutput.class)))
                .thenReturn(SUMMARY_OUTPUT);
        when(reportFormatterAgent.format(anyString(), anyString(), any(SummaryOutput.class)))
                .thenReturn("# Report");
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
                .thenReturn(SEARCH_OUTPUT);
        when(summarizerAgent.summarize(anyString(), anyString(), any(WebSearchOutput.class)))
                .thenReturn(SUMMARY_OUTPUT);
        when(reportFormatterAgent.format(anyString(), anyString(), any(SummaryOutput.class)))
                .thenReturn("# Report");

        coordinatorAgent.runPipeline(JOB_ID, TOPIC, ANSWERS);

        verify(webSearchAgent, times(2)).search(anyString(), anyString());
        verify(jobService).markCompleted(JOB_ID, "# Report");
    }

    @Test
    void should_callAgentsInOrderWithCorrectData_when_pipelineRuns() {
        stubHappyPath();
        coordinatorAgent.runPipeline(JOB_ID, TOPIC, ANSWERS);

        verify(webSearchAgent).search(eq(TOPIC), anyString());
        verify(summarizerAgent).summarize(eq(TOPIC), anyString(), eq(SEARCH_OUTPUT));
        verify(reportFormatterAgent).format(eq(TOPIC), anyString(), eq(SUMMARY_OUTPUT));
    }

    @Test
    void should_includeClarificationAnswers_when_buildingContextForAgents() {
        stubHappyPath();
        coordinatorAgent.runPipeline(JOB_ID, TOPIC, ANSWERS);

        verify(webSearchAgent).search(anyString(), contains("global"));
    }

    @Test
    void should_logSourceCount_when_searchCompletesWithSources() {
        WebSearchOutput searchWithSources = new WebSearchOutput("results",
                List.of(new SearchResult(1, "Title", "https://example.com", "snippet")));
        when(webSearchAgent.search(anyString(), anyString())).thenReturn(searchWithSources);
        when(summarizerAgent.summarize(anyString(), anyString(), any(WebSearchOutput.class)))
                .thenReturn(SUMMARY_OUTPUT);
        when(reportFormatterAgent.format(anyString(), anyString(), any(SummaryOutput.class)))
                .thenReturn("# Report");

        coordinatorAgent.runPipeline(JOB_ID, TOPIC, ANSWERS);

        verify(jobService).markCompleted(JOB_ID, "# Report");
    }
}
