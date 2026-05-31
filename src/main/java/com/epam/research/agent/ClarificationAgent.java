package com.epam.research.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ClarificationAgent {

    private static final String SYSTEM_PROMPT = """
            You are a research assistant. Generate exactly 2-3 concise clarifying questions to help focus a research request. Return ONLY the questions, one per line, no numbering or extra text.""";

    private final AnthropicClient anthropicClient;

    public List<String> generateQuestions(String topic) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_SONNET_4_6)
                .maxTokens(512L)
                .system(SYSTEM_PROMPT)
                .addUserMessage(topic)
                .build();

        String responseText = anthropicClient.messages().create(params)
                .content()
                .stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining("\n"));

        return Arrays.stream(responseText.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
    }
}

