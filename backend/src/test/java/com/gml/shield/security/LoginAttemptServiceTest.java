package com.gml.shield.security;

import com.gml.shield.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private static final Instant START = Instant.parse("2026-09-18T12:00:00Z");

    @Test
    void accountAndAddressPairIsBlockedAfterConfiguredFailures() {
        MutableClock clock = new MutableClock(START);
        LoginAttemptService service = new LoginAttemptService(2, 60, 100, clock);

        service.loginFailed("driver1@gml.local", "192.0.2.10");
        assertThat(service.isBlocked("driver1@gml.local", "192.0.2.10")).isFalse();

        service.loginFailed("driver1@gml.local", "192.0.2.10");

        assertThat(service.isBlocked("driver1@gml.local", "192.0.2.10")).isTrue();
        assertThat(service.getRemainingLockSeconds("driver1@gml.local", "192.0.2.10"))
                .isEqualTo(60);
    }

    @Test
    void blockedIdentityIsReleasedWhenConfiguredDurationExpires() {
        MutableClock clock = new MutableClock(START);
        LoginAttemptService service = new LoginAttemptService(1, 60, 100, clock);

        service.loginFailed("driver1@gml.local", "192.0.2.10");
        assertThat(service.isBlocked("driver1@gml.local", "192.0.2.10")).isTrue();

        clock.advance(Duration.ofSeconds(60));

        assertThat(service.isBlocked("driver1@gml.local", "192.0.2.10")).isFalse();
        assertThat(service.getRemainingLockSeconds("driver1@gml.local", "192.0.2.10"))
                .isZero();
    }

    @Test
    void successfulLoginClearsPreviousFailures() {
        MutableClock clock = new MutableClock(START);
        LoginAttemptService service = new LoginAttemptService(2, 60, 100, clock);

        service.loginFailed("driver1@gml.local", "192.0.2.10");
        service.loginSucceeded("driver1@gml.local", "192.0.2.10");
        service.loginFailed("driver1@gml.local", "192.0.2.10");

        assertThat(service.isBlocked("driver1@gml.local", "192.0.2.10")).isFalse();
    }
}
