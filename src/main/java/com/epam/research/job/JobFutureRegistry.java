package com.epam.research.job;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

@Component
public class JobFutureRegistry {

    private final ConcurrentHashMap<Long, Future<?>> futures = new ConcurrentHashMap<>();

    public void register(Long jobId, Future<?> future) {
        futures.put(jobId, future);
    }

    public boolean cancel(Long jobId) {
        Future<?> future = futures.remove(jobId);
        if (future == null) {
            return false;
        }
        return future.cancel(true);
    }

    public void remove(Long jobId) {
        futures.remove(jobId);
    }
}
