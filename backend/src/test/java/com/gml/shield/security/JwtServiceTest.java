package com.gml.shield.security;

import com.gml.shield.config.JwtProperties;
import com.gml.shield.support.MutableClock;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final Instant START = Instant.parse("2026-09-18T12:00:00Z");
    private static final long ACCESS_EXPIRATION_MS = 60_000;

    @Test
    void generatedTokenContainsExpectedIdentityRoleAndExpiration() {
        MutableClock clock = new MutableClock(START);
        JwtProperties properties = new JwtProperties(
                "test-jwt-secret-with-more-than-thirty-two-bytes-2026",
                ACCESS_EXPIRATION_MS,
                604_800_000
        );
        JwtService jwtService = new JwtService(properties, clock);

        String token = jwtService.generateToken("driver@gml.local", "DRIVER");
        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("driver@gml.local");
        assertThat(claims.get("role", String.class)).isEqualTo("DRIVER");
        assertThat(claims.getIssuedAt()).isEqualTo(Date.from(START));
        assertThat(claims.getExpiration())
                .isEqualTo(Date.from(START.plusMillis(ACCESS_EXPIRATION_MS)));
    }

    @Test
    void tokenBecomesInvalidWhenClockMovesPastExpiration() {
        MutableClock clock = new MutableClock(START);
        JwtProperties properties = new JwtProperties(
                "test-jwt-secret-with-more-than-thirty-two-bytes-2026",
                ACCESS_EXPIRATION_MS,
                604_800_000
        );
        JwtService jwtService = new JwtService(properties, clock);
        String token = jwtService.generateToken("driver@gml.local", "DRIVER");

        assertThat(jwtService.isValid(token)).isTrue();

        clock.advance(Duration.ofMillis(ACCESS_EXPIRATION_MS + 1));

        assertThat(jwtService.isValid(token)).isFalse();
    }
}
