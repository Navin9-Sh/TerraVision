package ai.terravision.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void allowsUpToTheLimitThenBlocksWithRetryAfter() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(clock);
        Duration window = Duration.ofMinutes(1);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("k", 3, window).allowed()).isTrue();
        }
        clock.advance(Duration.ofSeconds(20));
        RateLimiter.Result blocked = limiter.tryAcquire("k", 3, window);

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isEqualTo(40);
    }

    @Test
    void windowResetsAfterItExpires() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(clock);
        Duration window = Duration.ofMinutes(1);

        limiter.tryAcquire("k", 1, window);
        assertThat(limiter.tryAcquire("k", 1, window).allowed()).isFalse();

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("k", 1, window).allowed()).isTrue();
    }

    @Test
    void keysAreCountedIndependently() {
        RateLimiter limiter = new RateLimiter(new MutableClock());
        assertThat(limiter.tryAcquire("ip-1", 1, Duration.ofMinutes(1)).allowed()).isTrue();
        assertThat(limiter.tryAcquire("ip-1", 1, Duration.ofMinutes(1)).allowed()).isFalse();
        assertThat(limiter.tryAcquire("ip-2", 1, Duration.ofMinutes(1)).allowed()).isTrue();
    }

    @Test
    void filterReturns429AfterTheLoginLimitAndLeavesOtherPathsAlone() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimiter(new MutableClock()), new ObjectMapper().findAndRegisterModules(), true);

        int lastStatus = 0;
        MockHttpServletResponse limited = null;
        for (int i = 0; i < 11; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setRemoteAddr("203.0.113.9");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            lastStatus = response.getStatus();
            limited = response;
        }

        assertThat(lastStatus).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotBlank();
        assertThat(limited.getContentAsString()).contains("Too many requests");

        MockHttpServletRequest otherClient = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        otherClient.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse ok = new MockHttpServletResponse();
        filter.doFilter(otherClient, ok, new MockFilterChain());
        assertThat(ok.getStatus()).isEqualTo(200);

        MockHttpServletRequest history = new MockHttpServletRequest("GET", "/api/v1/history");
        history.setRemoteAddr("203.0.113.9");
        MockHttpServletResponse unaffected = new MockHttpServletResponse();
        filter.doFilter(history, unaffected, new MockFilterChain());
        assertThat(unaffected.getStatus()).isEqualTo(200);
    }

    @Test
    void disabledFilterNeverBlocks() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimiter(new MutableClock()), new ObjectMapper(), false);
        for (int i = 0; i < 50; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
