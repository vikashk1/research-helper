package com.epam.research.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportFormatterAgent {

    private static final String SYSTEM_PROMPT = """
            You are a research report writer. Given summarized content and context, produce a \
            well-structured Markdown report. Adapt the structure dynamically based on the topic, \
            audience, and content — do not use a fixed template. \
            Use inline citations like [1], [2] where the content references specific sources. \
            Do NOT add a sources/references section yourself — it will be appended automatically.""";

    private final AnthropicClient anthropicClient;

    public String format(String topic, String clarificationContext, SummaryResult summaryResult) {
        log.info("Formatting report for topic: '{}', summary length: {} chars, sources: {}",
                topic, summaryResult.summary().length(), summaryResult.sourceUrls().size());
        long start = System.currentTimeMillis();

        String userMessage = """
                Topic: %s
                Context: %s
                Summarized Content: %s""".formatted(topic, clarificationContext, summaryResult.summary());

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

        String fullReport = appendSourcesSection(report, summaryResult.sourceUrls());
        log.info("Report formatted in {}ms, report length: {} chars", System.currentTimeMillis() - start, fullReport.length());
        return fullReport;
    }

    private String appendSourcesSection(String report, List<String> sourceUrls) {
        if (sourceUrls.isEmpty()) {
            return report;
        }
        StringBuilder sb = new StringBuilder(report.stripTrailing());
        sb.append("\n\n## Sources\n\n");
        for (int i = 0; i < sourceUrls.size(); i++) {
            String url = sourceUrls.get(i);
            sb.append("[").append(i + 1).append("] [").append(url).append("](").append(url).append(")\n");
        }
        return sb.toString();
    }
}
