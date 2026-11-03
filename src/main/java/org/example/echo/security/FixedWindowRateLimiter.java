package org.example.echo.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class FixedWindowRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(FixedWindowRateLimiter.class);
    private static final String IN_MEMORY_BACKEND = "in-memory";

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(@Value("${app.security.rate-limit.backend:in-memory}") String backend) {
        String normalizedBackend = backend == null ? "" : backend.trim().toLowerCase(Locale.ROOT);
        if (!IN_MEMORY_BACKEND.equals(normalizedBackend)) {
            throw new IllegalStateException("Unsupported app.security.rate-limit.backend: " + backend);
        }
        log.warn("Using JVM-local in-memory rate limiter; configure a shared backend before running multiple app instances");
    }

    public boolean tryConsume(String key, int capacity, Duration window) {
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();
        AtomicBoolean allowed = new AtomicBoolean(false);

        counters.compute(key, (ignored, counter) -> {
            if (counter == null || counter.isExpired(now)) {
                allowed.set(true);
                return new WindowCounter(now, windowMillis, 1);
            }

            if (counter.requestCount() < capacity) {
                allowed.set(true);
                return counter.incremented();
            }
            return counter;
        });

        return allowed.get();
    }

    public void clear() {
        counters.clear();
    }

    @Scheduled(fixedDelayString = "${app.security.rate-limit.cleanup-fixed-delay-ms:60000}")
    void cleanupExpiredCounters() {
        long now = System.currentTimeMillis();
        counters.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private record WindowCounter(long windowStartedAtMillis, long windowMillis, int requestCount) {
        private boolean isExpired(long now) {
            return now - windowStartedAtMillis >= windowMillis;
        }

        private WindowCounter incremented() {
            return new WindowCounter(windowStartedAtMillis, windowMillis, requestCount + 1);
        }
    }
}
