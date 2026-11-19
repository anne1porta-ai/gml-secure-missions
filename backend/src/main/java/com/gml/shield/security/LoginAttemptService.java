package com.gml.shield.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Bloque temporairement les tentatives répétées pour un email et une IP.
 */
@Service
public class LoginAttemptService {

    private final Cache<String, Attempt> attempts;
    private final int maxFailedAttempts;
    private final long lockDurationSeconds;
    private final Clock clock;

    public LoginAttemptService(
            @Value("${app.auth.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${app.auth.lock-duration-seconds:300}") long lockDurationSeconds,
            @Value("${app.auth.max-tracked-identities:10000}") long maxTrackedIdentities,
            Clock clock) {
        validateConfiguration(maxFailedAttempts, lockDurationSeconds, maxTrackedIdentities);

        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDurationSeconds = lockDurationSeconds;
        this.clock = clock;

        // Le cache est borné pour qu'une attaque utilisant de nombreuses identités
        // différentes ne puisse pas faire croître la mémoire sans limite.
        this.attempts = Caffeine.newBuilder()
                .maximumSize(maxTrackedIdentities)
                .expireAfterAccess(Duration.ofSeconds(lockDurationSeconds * 2))
                .build();
    }

    public boolean isBlocked(String email, String ipAddress) {
        String key = buildKey(email, ipAddress);
        Attempt attempt = attempts.getIfPresent(key);

        if (attempt == null || attempt.blockedUntil == null) {
            return false;
        }

        if (!Instant.now(clock).isBefore(attempt.blockedUntil)) {
            attempts.invalidate(key);
            return false;
        }

        return true;
    }

    public void loginFailed(String email, String ipAddress) {
        Attempt attempt = getOrCreateAttempt(buildKey(email, ipAddress));

        // Plusieurs requêtes de connexion peuvent arriver en parallèle pour la même
        // identité ; le compteur doit donc être modifié de manière atomique.
        synchronized (attempt) {
            attempt.failedAttempts++;

            if (attempt.failedAttempts >= maxFailedAttempts) {
                attempt.blockedUntil = Instant.now(clock).plusSeconds(lockDurationSeconds);
                attempt.failedAttempts = 0;
            }
        }
    }

    public void loginSucceeded(String email, String ipAddress) {
        attempts.invalidate(buildKey(email, ipAddress));
    }

    public long getRemainingLockSeconds(String email, String ipAddress) {
        Attempt attempt = attempts.getIfPresent(buildKey(email, ipAddress));

        if (attempt == null || attempt.blockedUntil == null) {
            return 0;
        }

        long remainingSeconds = attempt.blockedUntil.getEpochSecond()
                - Instant.now(clock).getEpochSecond();
        return Math.max(remainingSeconds, 0);
    }

    private Attempt getOrCreateAttempt(String key) {
        return attempts.asMap().computeIfAbsent(key, ignoredKey -> new Attempt());
    }

    private String buildKey(String email, String ipAddress) {
        // Associer email et IP évite qu'un seul client puisse bloquer globalement un compte
        // tout en limitant les tentatives répétées sur une même identité depuis une même source.
        String normalizedEmail = normalizeEmail(email);
        String normalizedIpAddress = normalizeIpAddress(ipAddress);

        return normalizedEmail + "|" + normalizedIpAddress;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String normalizeIpAddress(String ipAddress) {
        return ipAddress == null ? "unknown" : ipAddress.trim();
    }

    private void validateConfiguration(int configuredMaxFailedAttempts,
                                       long configuredLockDurationSeconds,
                                       long configuredMaxTrackedIdentities) {
        if (configuredMaxFailedAttempts <= 0
                || configuredLockDurationSeconds <= 0
                || configuredMaxTrackedIdentities <= 0) {
            throw new IllegalArgumentException(
                    "Les paramètres de protection login doivent être positifs"
            );
        }
    }

    private static class Attempt {
        private int failedAttempts;
        private Instant blockedUntil;
    }
}
