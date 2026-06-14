package com.epam.research.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

@Slf4j
@Component
public class JobFutureRegistry {

    private final ConcurrentHashMap<Long, Future<?>> futures = new ConcurrentHashMap<>();

    public void register(Long jobId, Future<?> future) {
        futures.put(jobId, future);
        log.debug("Registered future for job {}", jobId);
    }

    public boolean cancel(Long jobId) {
        Future<?> future = futures.remove(jobId);
        if (future == null) {
            log.debug("No future found for job {}", jobId);
            return false;
        }
        boolean cancelled = future.cancel(true);
        log.info("Cancelled future for job {}, result: {}", jobId, cancelled);
        return cancelled;
    }

    public void remove(Long jobId) {
        futures.remove(jobId);
        log.debug("Removed future for job {}", jobId);
    }
}
