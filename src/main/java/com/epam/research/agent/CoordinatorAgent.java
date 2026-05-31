package com.epam.research.agent;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CoordinatorAgent {

    @Async
    public void runPipeline(Long jobId, String topic, Map<String, String> clarificationAnswers) {
        // TODO: Orchestrate WebSearchAgent → SummarizerAgent → ReportFormatterAgent
        // Update job status and emit SSE log events at each step
        // Apply retry logic with exponential backoff per subagent
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
