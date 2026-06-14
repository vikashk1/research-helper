package com.epam.research.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
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
            well-structured summary of the key findings relevant to the topic.

            IMPORTANT — source preservation rules:
            1. Retain all inline citation markers (e.g. [1], [2]) exactly as they appear in the raw results, \
            immediately after the fact they support.
            2. At the end of your summary, reproduce the original "## Sources" section verbatim, including every numbered URL entry.
            3. Do not add, remove, or alter any URLs or citation numbers.""";

    private final AnthropicClient anthropicClient;

    public String summarize(String topic, String clarificationContext, String rawSearchResults) {
        log.info("Summarizing search results for topic: '{}', input length: {} chars", topic, rawSearchResults.length());
        long start = System.currentTimeMillis();

        String userMessage = """
                Topic: %s
                Context: %s
                Raw Search Results: %s""".formatted(topic, clarificationContext, rawSearchResults);

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_SONNET_4_6)
                .maxTokens(2048L)
                .system(SYSTEM_PROMPT)
                .addUserMessage(userMessage)
                .build();

        String summary = anthropicClient.messages().create(params)
                .content()
                .stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining("\n"));

        log.info("Summarization completed in {}ms, summary length: {} chars", System.currentTimeMillis() - start, summary.length());
        return summary;
    }
}
