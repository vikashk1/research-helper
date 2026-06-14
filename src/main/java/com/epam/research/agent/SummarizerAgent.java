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
            well-structured summary of the key findings relevant to the topic. \
            Where appropriate, annotate claims with inline citations using [1], [2], etc. \
            that correspond to the numbered source list you will be given.""";

    private final AnthropicClient anthropicClient;

    public SummaryResult summarize(String topic, String clarificationContext, SearchResult searchResult) {
        log.info("Summarizing search results for topic: '{}', input length: {} chars, sources: {}",
                topic, searchResult.content().length(), searchResult.sourceUrls().size());
        long start = System.currentTimeMillis();

        String numberedSources = buildNumberedSources(searchResult);

        String userMessage = """
                Topic: %s
                Context: %s
                Raw Search Results: %s
                %s""".formatted(topic, clarificationContext, searchResult.content(), numberedSources);

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

        log.info("Summarization completed in {}ms, summary length: {} chars",
                System.currentTimeMillis() - start, summary.length());
        return new SummaryResult(summary, searchResult.sourceUrls());
    }

    private String buildNumberedSources(SearchResult searchResult) {
        if (searchResult.sourceUrls().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Source References:\n");
        for (int i = 0; i < searchResult.sourceUrls().size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(searchResult.sourceUrls().get(i)).append("\n");
        }
        return sb.toString();
    }
}
