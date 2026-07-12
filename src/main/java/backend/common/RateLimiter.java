package backend.common;

import backend.common.exception.RateLimitExceededException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory sliding-window rate limiter. Each (key, limit) pair is
 * tracked independently. Memory is bounded by the number of distinct keys
 * seen in the last window.
 * <p>
 * For production scale, swap to Bucket4j + Redis. The interface is stable:
 * the controller just calls {@link #checkOrThrow(String, int, Duration)}.
 */
@Component
public class RateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public void checkOrThrow(String key, int maxRequests, Duration windowSize) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(windowSize);

        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart.isBefore(cutoff)) {
                return new Window(now, 1);
            }
            return new Window(existing.windowStart, existing.count + 1);
        });

        if (w.count > maxRequests) {
            throw new RateLimitExceededException(
                    "Too many requests. Please try again later.");
        }
    }

    private record Window(Instant windowStart, int count) {}
}
