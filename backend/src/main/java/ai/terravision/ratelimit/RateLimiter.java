package ai.terravision.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window, in-memory request counter keyed by an arbitrary string (rule + client).
 * Deliberately simple: it lives in this JVM's memory, so limits are per instance and
 * reset on restart -- fine for a single-instance deployment. Running several instances
 * would need a shared store (e.g. Redis) behind this same interface.
 */
public class RateLimiter {

    /** Outcome of one attempt: whether it was allowed, and how long to wait if not. */
    public record Result(boolean allowed, long retryAfterSeconds) {
    }

    private static final int SWEEP_EVERY = 2000;

    private record Window(long startMillis, int count) {
    }

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private int callsSinceSweep;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    public Result tryAcquire(String key, int maxRequests, Duration window) {
        long now = clock.millis();
        long windowMillis = window.toMillis();
        long[] retryAfterMillis = {0};
        boolean[] allowed = {true};

        windows.compute(key, (k, current) -> {
            if (current == null || now - current.startMillis >= windowMillis) {
                return new Window(now, 1);
            }
            if (current.count >= maxRequests) {
                allowed[0] = false;
                retryAfterMillis[0] = windowMillis - (now - current.startMillis);
                return current;
            }
            return new Window(current.startMillis, current.count + 1);
        });

        maybeSweep(now, windowMillis);
        return new Result(allowed[0], allowed[0] ? 0 : Math.max(1, (retryAfterMillis[0] + 999) / 1000));
    }

    /** Drops expired windows now and then so the map can't grow without bound. */
    private synchronized void maybeSweep(long now, long windowMillis) {
        if (++callsSinceSweep < SWEEP_EVERY) {
            return;
        }
        callsSinceSweep = 0;
        // Windows differ per rule; 1 hour is the longest rule, so anything older is dead.
        long cutoff = now - Math.max(windowMillis, Duration.ofHours(1).toMillis());
        windows.entrySet().removeIf(e -> e.getValue().startMillis < cutoff);
    }
}
