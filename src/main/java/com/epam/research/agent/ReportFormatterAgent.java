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
            When citing a fact from a source, embed the source number in brackets inline, e.g. [1], [2]. \
            Do NOT include a Sources section — it will be appended automatically.""";

    private final AnthropicClient anthropicClient;

    public String format(String topic, String clarificationContext, SummaryOutput summaryOutput) {
        log.info("Formatting report for topic: '{}', summary length: {} chars, sources: {}",
                topic, summaryOutput.content().length(), summaryOutput.sources().size());
        long start = System.currentTimeMillis();

        String sourcesContext = summaryOutput.sources().isEmpty() ? "No sources." :
                summaryOutput.sources().stream()
                        .map(s -> "[%d] %s - %s".formatted(s.index(), s.title(), s.url()))
                        .collect(Collectors.joining("\n"));

        String userMessage = """
                Topic: %s
                Context: %s
                Available Sources (use [N] inline citations):
                %s
                Summarized Content: %s""".formatted(
                topic, clarificationContext, sourcesContext, summaryOutput.content());

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_SONNET_4_6)
                .maxTokens(2048L)
                .system(SYSTEM_PROMPT)
                .addUserMessage(userMessage)
                .build();

        String reportBody = anthropicClient.messages().create(params)
                .content()
                .stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining("\n"));

        log.info("Report body formatted in {}ms, length: {} chars",
                System.currentTimeMillis() - start, reportBody.length());

        return appendSourcesSection(reportBody, summaryOutput.sources());
    }

    private String appendSourcesSection(String reportBody, List<SearchResult> sources) {
        if (sources.isEmpty()) {
            return reportBody;
        }
        String sourcesSection = sources.stream()
                .map(s -> "%s [%s](%s)".formatted(s.toInlineCitation(), s.title(), s.url()))
                .collect(Collectors.joining("\n"));
        return reportBody + "\n\n## Sources\n\n" + sourcesSection;
    }
}
