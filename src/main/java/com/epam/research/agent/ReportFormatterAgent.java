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
public class ReportFormatterAgent {

    private static final String SYSTEM_PROMPT = """
            You are a research report writer. Given summarized content and context, produce a \
            well-structured Markdown report. Adapt the structure dynamically based on the topic, \
            audience, and content — do not use a fixed template.

            IMPORTANT — citation and sources rules:
            1. Retain all inline citation markers (e.g. [1], [2]) exactly as they appear in the summarized content.
            2. At the very end of the report, include a "## Sources" section.
            3. In the "## Sources" section, list every source as a numbered Markdown hyperlink:
               [N] [<URL>](<URL>)
               Use the numbered URLs from the summarized content's "## Sources" section. Do not renumber.
            4. Do not fabricate URLs. Only include URLs that appeared in the summarized content.""";

    private final AnthropicClient anthropicClient;

    public String format(String topic, String clarificationContext, String summarizedContent) {
        log.info("Formatting report for topic: '{}', summary length: {} chars", topic, summarizedContent.length());
        long start = System.currentTimeMillis();

        String userMessage = """
                Topic: %s
                Context: %s
                Summarized Content: %s""".formatted(topic, clarificationContext, summarizedContent);

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_SONNET_4_6)
                .maxTokens(2048L)
                .system(SYSTEM_PROMPT)
                .addUserMessage(userMessage)
                .build();

        String report = anthropicClient.messages().create(params)
                .content()
                .stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining("\n"));

        log.info("Report formatted in {}ms, report length: {} chars", System.currentTimeMillis() - start, report.length());
        return report;
    }
}
