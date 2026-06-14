package com.epam.research.agent;

import java.util.List;

/**
 * Carries a summarized text together with source URLs propagated from WebSearchAgent.
 */
public record SummaryResult(String summary, List<String> sourceUrls) {

    public SummaryResult {
        sourceUrls = sourceUrls == null ? List.of() : List.copyOf(sourceUrls);
    }
}
