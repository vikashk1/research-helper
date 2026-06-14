package com.epam.research.agent;

import java.util.List;

/**
 * Carries raw search content together with the source URLs returned by WebSearchAgent.
 */
public record SearchResult(String content, List<String> sourceUrls) {

    public SearchResult {
        sourceUrls = sourceUrls == null ? List.of() : List.copyOf(sourceUrls);
    }
}
