package com.epam.research.agent;

public record SearchResult(int index, String title, String url, String snippet) {

    public String toMarkdownReference() {
        return "[%d] [%s](%s)".formatted(index, title, url);
    }

    public String toInlineCitation() {
        return "[%d]".formatted(index);
    }
}
