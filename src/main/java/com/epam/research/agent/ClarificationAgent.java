package com.epam.research.agent;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClarificationAgent {

    public List<String> generateQuestions(String topic) {
        // TODO: Call Claude API to generate 2-3 clarifying questions based on the topic
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
