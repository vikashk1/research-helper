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
            well-structured summary of the key findings relevant to the topic. When referencing \
            information from a source, include the source number in brackets, e.g. [1], [2]. \
            Use only the source numbers provided in the input.""";

    private final AnthropicClient anthropicClient;

    public SummaryOutput summarize(String topic, String clarificationContext, WebSearchOutput searchOutput) {
        log.info("Summarizing search results for topic: '{}', input length: {} chars, sources: {}",
                topic, searchOutput.content().length(), searchOutput.sources().size());
        long start = System.currentTimeMillis();

        String sourcesSection = searchOutput.sources().isEmpty() ? "No sources available." :
                searchOutput.sources().stream()
                        .map(s -> "[%d] %s - %s".formatted(s.index(), s.title(), s.url()))
                        .collect(Collectors.joining("\n"));

        String userMessage = """
                Topic: %s
                Context: %s
                Available Sources:
                %s
                Raw Search Results: %s""".formatted(
                topic, clarificationContext, sourcesSection, searchOutput.content());

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
        return new SummaryOutput(summary, searchOutput.sources());
    }
}
