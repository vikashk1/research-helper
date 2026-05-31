package com.epam.research.job;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class JobService {

    public List<String> getClarificationQuestions(String topic) {
        // TODO: Delegate to ClarificationAgent
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Job createJob(String topic, Map<String, String> clarificationAnswers) {
        // TODO: Persist job with PENDING status, trigger async pipeline
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Job getJob(Long jobId) {
        // TODO: Fetch job by ID
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<Job> getAllJobs() {
        // TODO: Return all jobs ordered by createdAt DESC
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void appendLog(Long jobId, String message) {
        // TODO: Persist a JobLog entry and emit SSE event
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
