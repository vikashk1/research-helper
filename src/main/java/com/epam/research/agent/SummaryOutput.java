package com.epam.research.agent;

import java.util.List;

public record SummaryOutput(String content, List<SearchResult> sources) {}
