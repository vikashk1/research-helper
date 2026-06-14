package com.epam.research.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextCitation;
import com.anthropic.models.messages.WebSearchTool20250305;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchAgent {

    private static final String SYSTEM_PROMPT = """
            You are a web research assistant. Search the web and return comprehensive, factual results \
            about the given topic. Cite the sources you use in your response.""";

    private final AnthropicClient anthropicClient;

    public WebSearchOutput search(String topic, String clarificationContext) {
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

        // Collect text content and deduplicated citations keyed by URL
        StringBuilder contentBuilder = new StringBuilder();
        Map<String, SearchResult> citationsByUrl = new LinkedHashMap<>();
        AtomicInteger indexCounter = new AtomicInteger(1);

        anthropicClient.messages().create(params)
                .content()
                .forEach(block -> block.text().ifPresent(textBlock -> {
                    contentBuilder.append(textBlock.text()).append("\n");
                    textBlock.citations().ifPresent(citations -> citations.forEach(citation -> {
                        if (citation.isWebSearchResultLocation()) {
                            var loc = citation.asWebSearchResultLocation();
                            String url = loc.url();
                            if (!citationsByUrl.containsKey(url)) {
                                citationsByUrl.put(url, new SearchResult(
                                        indexCounter.getAndIncrement(),
                                        loc.title().orElse(""),
                                        url,
                                        loc.citedText()
                                ));
                            }
                        }
                    }));
                }));

        String content = contentBuilder.toString().stripTrailing();
        List<SearchResult> sources = new ArrayList<>(citationsByUrl.values());

        log.info("Web search completed in {}ms, result length: {} chars, sources found: {}",
                System.currentTimeMillis() - start, content.length(), sources.size());
        return new WebSearchOutput(content, sources);
    }
}
