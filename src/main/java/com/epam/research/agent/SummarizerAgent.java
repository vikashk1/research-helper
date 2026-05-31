package com.epam.research.agent;

import org.springframework.stereotype.Component;

@Component
public class SummarizerAgent {

    public String summarize(String topic, String clarificationContext, String rawSearchResults) {
        // TODO: Call Claude API to summarize raw search results
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
