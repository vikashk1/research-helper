package com.epam.research.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClarificationAgent {

    private static final String SYSTEM_PROMPT = """
            You are a research assistant. Generate exactly 2-3 concise clarifying questions to help focus a research request. Return ONLY the questions, one per line, no numbering or extra text.""";

    private final AnthropicClient anthropicClient;

    public List<String> generateQuestions(String topic) {
        log.info("Generating clarification questions for topic: '{}'", topic);
        long start = System.currentTimeMillis();

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_HAIKU_4_5)
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

        List<String> questions = Arrays.stream(responseText.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());

        log.info("Generated {} clarification questions in {}ms", questions.size(), System.currentTimeMillis() - start);
        log.debug("Questions: {}", questions);
        return questions;
    }
}

