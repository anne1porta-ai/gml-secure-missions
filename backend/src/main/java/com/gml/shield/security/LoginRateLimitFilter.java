package com.gml.shield.security;

import tools.jackson.databind.json.JsonMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.gml.shield.dto.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Limite les tentatives de connexion par adresse IP.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/api/auth/login";
    static final String RATE_LIMIT_CODE = "AUTH_RATE_LIMITED";
    static final String RATE_LIMIT_MESSAGE = "Trop de tentatives de connexion. Réessayez plus tard.";

    private final JsonMapper jsonMapper;
    private final Cache<String, WindowCounter> counters;
    private final int maxRequests;
    private final Duration window;
    private final Clock clock;

    public LoginRateLimitFilter(
            JsonMapper jsonMapper,
            @Value("${app.auth.ip-rate-limit.max-requests:20}") int maxRequests,
            @Value("${app.auth.ip-rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${app.auth.ip-rate-limit.max-clients:10000}") long maxClients,
            Clock clock) {
        if (maxRequests <= 0 || windowSeconds <= 0 || maxClients <= 0) {
            throw new IllegalArgumentException("Les paramètres du rate limiting doivent être positifs");
        }

        this.jsonMapper = jsonMapper;
        this.maxRequests = maxRequests;
        this.window = Duration.ofSeconds(windowSeconds);
        this.clock = clock;
        // Cette limite par IP complète le blocage email+IP : elle ralentit un client
        // qui essaierait beaucoup de comptes différents. Le cache reste borné en mémoire.
        this.counters = Caffeine.newBuilder()
                .maximumSize(maxClients)
                .expireAfterAccess(window.multipliedBy(2))
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Le rate limiting est volontairement limité au login pour ne pas ralentir
        // les appels métier authentifiés.
        return !HttpMethod.POST.matches(request.getMethod())
                || !LOGIN_PATH.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String clientKey = normalizeClientAddress(request.getRemoteAddr());
        Instant now = clock.instant();
        WindowCounter counter = counters.get(clientKey, ignored -> new WindowCounter(now));

        RateLimitDecision decision = counter.consume(maxRequests, window, now);
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setHeader("Cache-Control", "no-store");
        jsonMapper.writeValue(
                response.getOutputStream(),
                ApiErrorResponse.of(RATE_LIMIT_CODE, RATE_LIMIT_MESSAGE)
        );
    }

    private static String normalizeClientAddress(String remoteAddress) {
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress.trim();
    }

    private static final class WindowCounter {
        private Instant startedAt;
        private int count;

        private WindowCounter(Instant startedAt) {
            this.startedAt = startedAt;
        }

        private synchronized RateLimitDecision consume(int capacity, Duration window, Instant now) {
            Instant windowEnd = startedAt.plus(window);
            if (!now.isBefore(windowEnd)) {
                startedAt = now;
                count = 0;
                windowEnd = startedAt.plus(window);
            }

            if (count < capacity) {
                count++;
                return new RateLimitDecision(true, 0);
            }

            long retryAfter = Math.max(1, Duration.between(now, windowEnd).toSeconds() + 1);
            return new RateLimitDecision(false, retryAfter);
        }
    }

    private record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
    }
}
