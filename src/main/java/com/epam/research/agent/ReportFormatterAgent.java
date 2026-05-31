package com.epam.research.agent;

import org.springframework.stereotype.Component;

@Component
public class ReportFormatterAgent {

    public String format(String topic, String clarificationContext, String summarizedContent) {
        // TODO: Call Claude API to produce a well-structured Markdown report
        // Structure varies dynamically based on topic, audience, and content
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
