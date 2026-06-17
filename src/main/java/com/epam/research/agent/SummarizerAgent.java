package com.epam.research.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.epam.research.job.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummarizerAgent {

    private static final String SYSTEM_PROMPT = """
            You are a research summarizer. Given raw search results and context, produce a concise, \
            well-structured summary of the key findings relevant to the topic.""";

    private final AnthropicClient anthropicClient;
    private final JobService jobService;

    public AgentResult summarize(Long jobId, String topic, String clarificationContext, String rawSearchResults) {
        log.info("Summarizing search results for topic: '{}', input length: {} chars", topic, rawSearchResults.length());
        long start = System.currentTimeMillis();
        jobService.appendStageEvent(jobId, "SUMMARIZE", "start", "Starting summarization for: " + topic);
        jobService.appendStageEvent(jobId, "SUMMARIZE", "activity", "Analyzing search results...");

        String userMessage = """
                Topic: %s
                Context: %s
                Raw Search Results: %s""".formatted(topic, clarificationContext, rawSearchResults);

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_HAIKU_4_5)
                .maxTokens(2048L)
                .system(SYSTEM_PROMPT)
                .addUserMessage(userMessage)
                .build();

        jobService.appendStageEvent(jobId, "SUMMARIZE", "activity", "Generating summary...");
        Message response = anthropicClient.messages().create(params);
        String summary = response.content()
                .stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining("\n"));

        log.info("Summarization completed in {}ms, summary length: {} chars", System.currentTimeMillis() - start, summary.length());
        jobService.appendStageEvent(jobId, "SUMMARIZE", "end", "Summarization complete, " + summary.length() + " chars");
        return new AgentResult(summary, response.usage().inputTokens(), response.usage().outputTokens());
    }
}
