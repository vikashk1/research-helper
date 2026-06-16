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
public class ReportFormatterAgent {

    private static final String SYSTEM_PROMPT = """
            You are a research report writer. Given summarized content and context, produce a \
            well-structured Markdown report. Adapt the structure dynamically based on the topic, \
            audience, and content — do not use a fixed template.""";

    private final AnthropicClient anthropicClient;
    private final JobService jobService;

    public String format(Long jobId, String topic, String clarificationContext, String summarizedContent) {
        log.info("Formatting report for topic: '{}', summary length: {} chars", topic, summarizedContent.length());
        long start = System.currentTimeMillis();
        jobService.appendStageEvent(jobId, "FORMAT", "start", "Starting report formatting for: " + topic);
        jobService.appendStageEvent(jobId, "FORMAT", "activity", "Assembling report sections...");

        String userMessage = """
                Topic: %s
                Context: %s
                Summarized Content: %s""".formatted(topic, clarificationContext, summarizedContent);

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_HAIKU_4_5)
                .maxTokens(2048L)
                .system(SYSTEM_PROMPT)
                .addUserMessage(userMessage)
                .build();

        Message response = anthropicClient.messages().create(params);
        jobService.addTokenUsage(jobId, response.usage().inputTokens(), response.usage().outputTokens());
        String report = response.content()
                .stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining("\n"));

        jobService.appendStageEvent(jobId, "FORMAT", "activity", "Report formatting complete");
        log.info("Report formatted in {}ms, report length: {} chars", System.currentTimeMillis() - start, report.length());
        jobService.appendStageEvent(jobId, "FORMAT", "end", "Report formatting complete, " + report.length() + " chars");
        return report;
    }
}
