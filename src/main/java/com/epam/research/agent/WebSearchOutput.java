package com.epam.research.agent;

import java.util.List;

public record WebSearchOutput(String content, List<SearchResult> sources) {

    public String sourcesAsMarkdown() {
        if (sources.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (SearchResult source : sources) {
            sb.append(source.toMarkdownReference()).append("\n");
        }
        return sb.toString().stripTrailing();
    }
}
