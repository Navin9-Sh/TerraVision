package ai.terravision.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {

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

    private synchronized void maybeSweep(long now, long windowMillis) {
        if (++callsSinceSweep < SWEEP_EVERY) {
            return;
        }
        callsSinceSweep = 0;
        long cutoff = now - Math.max(windowMillis, Duration.ofHours(1).toMillis());
        windows.entrySet().removeIf(e -> e.getValue().startMillis < cutoff);
    }
}
