package com.epam.research.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.WebSearchTool20250305;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchAgent {

    private static final String SYSTEM_PROMPT = """
            You are a web research assistant. Search the web and return comprehensive, factual results about the given topic. Include relevant sources and summarize key findings.""";

    private final AnthropicClient anthropicClient;

    public String search(String topic, String clarificationContext) {
        log.info("Starting web search for topic: '{}'", topic);
        long start = System.currentTimeMillis();

        String userMessage = """
                Topic: %s
                Context: %s""".formatted(topic, clarificationContext);

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_HAIKU_4_5)
                .maxTokens(4096L)
                .system(SYSTEM_PROMPT)
                .addTool(WebSearchTool20250305.builder().build())
                .addUserMessage(userMessage)
                .build();

        String result = anthropicClient.messages().create(params)
                .content()
                .stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining("\n"));

        log.info("Web search completed in {}ms, result length: {} chars", System.currentTimeMillis() - start, result.length());
        return result;
    }
}
