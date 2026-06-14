package com.epam.research.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.WebSearchTool20250305;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchAgent {

    private static final String SYSTEM_PROMPT = """
            You are a web research assistant. Search the web and return comprehensive, factual results \
            about the given topic.

            At the end of your response, include a sources section formatted EXACTLY as:
            SOURCES:
            - https://example.com/article-1
            - https://example.com/article-2

            List every URL you referenced. Do not number them — use a leading dash.""";

    private static final Pattern SOURCES_SECTION = Pattern.compile(
            "(?s)SOURCES:\\s*\\n((?:-\\s*https?://\\S+\\s*\\n?)+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SOURCE_URL = Pattern.compile(
            "-\\s*(https?://\\S+)");

    private final AnthropicClient anthropicClient;

    public SearchResult search(String topic, String clarificationContext) {
        log.info("Starting web search for topic: '{}'", topic);
        long start = System.currentTimeMillis();

        String userMessage = """
                Topic: %s
                Context: %s""".formatted(topic, clarificationContext);

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_SONNET_4_6)
                .maxTokens(4096L)
                .system(SYSTEM_PROMPT)
                .addTool(WebSearchTool20250305.builder().build())
                .addUserMessage(userMessage)
                .build();

        String raw = anthropicClient.messages().create(params)
                .content()
                .stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining("\n"));

        SearchResult result = splitContentAndSources(raw);
        log.info("Web search completed in {}ms, content length: {} chars, sources: {}",
                System.currentTimeMillis() - start, result.content().length(), result.sourceUrls().size());
        return result;
    }

    private SearchResult splitContentAndSources(String raw) {
        Matcher sectionMatcher = SOURCES_SECTION.matcher(raw);
        if (!sectionMatcher.find()) {
            // No structured sources block — return raw as content with empty source list
            return new SearchResult(raw, List.of());
        }

        String sourcesBlock = sectionMatcher.group(1);
        String content = raw.substring(0, sectionMatcher.start()).stripTrailing();

        List<String> urls = new ArrayList<>();
        Matcher urlMatcher = SOURCE_URL.matcher(sourcesBlock);
        while (urlMatcher.find()) {
            urls.add(urlMatcher.group(1).strip());
        }

        return new SearchResult(content, urls);
    }
}
