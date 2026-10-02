package ai.terravision.ratelimit;

import ai.terravision.auth.AuthenticatedUser;
import ai.terravision.common.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    record Rule(String name, String method, String path, int max, Duration window, boolean perUser) {
    }

    static final List<Rule> RULES = List.of(
            new Rule("login", "POST", "/api/v1/auth/login", 10, Duration.ofMinutes(1), false),
            new Rule("register", "POST", "/api/v1/auth/register", 5, Duration.ofMinutes(10), false),
            new Rule("resend", "POST", "/api/v1/auth/resend-verification", 5, Duration.ofMinutes(10), false),
            new Rule("forgot", "POST", "/api/v1/auth/forgot-password", 5, Duration.ofMinutes(10), false),
            new Rule("reset", "POST", "/api/v1/auth/reset-password", 10, Duration.ofMinutes(10), false),
            new Rule("refresh", "POST", "/api/v1/auth/refresh", 30, Duration.ofMinutes(1), false),
            new Rule("predict", "POST", "/api/v1/predict", 30, Duration.ofMinutes(1), true));

    private final RateLimiter limiter;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public RateLimitFilter(RateLimiter limiter, ObjectMapper objectMapper, boolean enabled) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public RateLimitFilter(ObjectMapper objectMapper, boolean enabled) {
        this(new RateLimiter(Clock.systemUTC()), objectMapper, enabled);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || ruleFor(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = ruleFor(request);
        String key = rule.name() + ":" + clientKey(request, rule);
        RateLimiter.Result result = limiter.tryAcquire(key, rule.max(), rule.window());

        if (result.allowed()) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Rate limit hit rule={} key={} retryAfterSeconds={}", rule.name(), key, result.retryAfterSeconds());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                Instant.now(), 429, "Too Many Requests",
                "Too many requests. Please try again in " + result.retryAfterSeconds() + " seconds.",
                request.getRequestURI()));
    }

    private Rule ruleFor(HttpServletRequest request) {
        for (Rule rule : RULES) {
            if (rule.method().equals(request.getMethod()) && rule.path().equals(request.getRequestURI())) {
                return rule;
            }
        }
        return null;
    }

    private String clientKey(HttpServletRequest request, Rule rule) {
        if (rule.perUser()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
                return "user-" + user.userId();
            }
        }
        return request.getRemoteAddr();
    }
}
