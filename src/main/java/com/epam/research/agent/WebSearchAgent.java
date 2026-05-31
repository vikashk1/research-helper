package com.epam.research.agent;

import com.anthropic.client.AnthropicClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchAgent {

    private final AnthropicClient anthropicClient;

    public String search(String topic, String clarificationContext) {
        // TODO: Call Claude API with built-in web search tool enabled
        // Returns raw search results as text
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
