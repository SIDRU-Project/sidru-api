package com.sidru.sidru_api.iam.infrastructure.authorization.sfs.pipeline;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting for sensitive endpoints (RNF-07).
 *
 * <p>Two protections, applied per client IP on the authentication endpoints:
 * <ul>
 *   <li><b>Request rate:</b> at most {@code maxPerMinute} requests per minute (fixed
 *       window). Excess requests get HTTP 429.</li>
 *   <li><b>Brute-force lockout:</b> after {@code maxAuthFailures} consecutive failed
 *       sign-ins (HTTP 401), the IP is blocked for {@code blockSeconds}; a successful
 *       sign-in resets the counter.</li>
 * </ul>
 *
 * <p>In-memory (single instance, MVP scope). For a multi-instance deployment this state
 * should move to a shared store (e.g. Redis). Instantiated directly in the security
 * filter chain — not a Spring bean — to avoid double servlet registration.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String SENSITIVE_SEGMENT = "/authentication";
    private static final String SIGN_IN_SUFFIX = "/sign-in";
    private static final long WINDOW_MILLIS = 60_000L;

    private final int maxPerMinute;
    private final int maxAuthFailures;
    private final long blockMillis;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FailureState> failures = new ConcurrentHashMap<>();

    public RateLimitFilter(int maxPerMinute, int maxAuthFailures, int blockSeconds) {
        this.maxPerMinute = maxPerMinute;
        this.maxAuthFailures = maxAuthFailures;
        this.blockMillis = blockSeconds * 1000L;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only guard the sensitive (authentication) endpoints.
        return !request.getRequestURI().contains(SENSITIVE_SEGMENT);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = clientIp(request);
        long now = System.currentTimeMillis();

        if (isBlocked(ip, now)) {
            reject(response, "Demasiados intentos fallidos. Inténtalo más tarde.", blockMillis / 1000);
            return;
        }
        if (exceedsRate(ip, now)) {
            LOGGER.warn("Rate limit exceeded for IP {} on {}", ip, request.getRequestURI());
            reject(response, "Demasiadas solicitudes. Espera un momento.", 60);
            return;
        }

        filterChain.doFilter(request, response);

        // Brute-force tracking only on the sign-in endpoint.
        if (request.getRequestURI().endsWith(SIGN_IN_SUFFIX)) {
            if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
                registerFailure(ip, now);
            } else if (response.getStatus() < 400) {
                failures.remove(ip);
            }
        }
    }

    private boolean exceedsRate(String ip, long now) {
        Window window = windows.compute(ip, (k, w) -> {
            if (w == null || now - w.start >= WINDOW_MILLIS) {
                return new Window(now);
            }
            return w;
        });
        return window.count.incrementAndGet() > maxPerMinute;
    }

    private boolean isBlocked(String ip, long now) {
        FailureState state = failures.get(ip);
        return state != null && state.blockedUntil > now;
    }

    private void registerFailure(String ip, long now) {
        FailureState state = failures.compute(ip, (k, s) -> s == null ? new FailureState() : s);
        int count = state.count.incrementAndGet();
        if (count >= maxAuthFailures) {
            state.blockedUntil = now + blockMillis;
            LOGGER.warn("IP {} blocked for {} s after {} failed sign-ins", ip, blockMillis / 1000, count);
        }
    }

    private void reject(HttpServletResponse response, String message, long retryAfterSeconds) throws IOException {
        response.setStatus(429); // Too Many Requests
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"too_many_requests\",\"message\":\"" + message + "\"}");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        private final long start;
        private final AtomicInteger count = new AtomicInteger(0);

        private Window(long start) {
            this.start = start;
        }
    }

    private static final class FailureState {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long blockedUntil = 0L;
    }
}
