package com.gml.shield.security;

import com.gml.shield.config.JwtProperties;
import com.gml.shield.enums.Role;
import com.gml.shield.model.RefreshToken;
import com.gml.shield.model.User;
import com.gml.shield.repository.RefreshTokenRepository;
import com.gml.shield.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant START = Instant.parse("2026-09-18T12:00:00Z");
    private static final long REFRESH_EXPIRATION_MS = 60_000;

    @Mock
    private RefreshTokenRepository repository;

    private MutableClock clock;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        JwtProperties properties = new JwtProperties(
                "unused-in-this-test", 900_000, REFRESH_EXPIRATION_MS);
        refreshTokenService = new RefreshTokenService(repository, properties, clock);
    }

    @Test
    void createForReplacesPreviousTokenAndUsesConfiguredExpiration() {
        User user = user(true);
        when(repository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken createdToken = refreshTokenService.createFor(user);

        verify(repository).deleteByUser(user);
        verify(repository).flush();
        assertThat(createdToken.getUser()).isSameAs(user);
        assertThat(createdToken.getExpiryDate())
                .isEqualTo(START.plusMillis(REFRESH_EXPIRATION_MS));
        assertThat(createdToken.getToken()).isNotBlank();
    }

    @Test
    void revokeAllForDeletesEveryTokenOfUser() {
        User user = user(false);

        refreshTokenService.revokeAllFor(user);

        verify(repository).deleteByUser(user);
    }

    @Test
    void expiredTokenIsRejectedAndDeleted() {
        User user = user(true);
        RefreshToken token = new RefreshToken(
                "expired-token",
                user,
                START.minusSeconds(1)
        );

        when(repository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.requireValid("expired-token"))
                .isInstanceOf(RefreshTokenException.class)
                .hasMessage("Refresh token expiré, reconnexion nécessaire");

        verify(repository).delete(token);
    }

    @Test
    void tokenExpiresWhenClockMovesPastItsExpiryDate() {
        User user = user(true);
        RefreshToken token = new RefreshToken(
                "clock-token",
                user,
                START.plusSeconds(30)
        );
        when(repository.findByToken("clock-token")).thenReturn(Optional.of(token));

        assertThat(refreshTokenService.requireValid("clock-token")).isSameAs(token);

        clock.advance(Duration.ofSeconds(31));

        assertThatThrownBy(() -> refreshTokenService.requireValid("clock-token"))
                .isInstanceOf(RefreshTokenException.class)
                .hasMessage("Refresh token expiré, reconnexion nécessaire");
    }

    @Test
    void tokenExactlyAtExpiryInstantIsRejected() {
        User user = user(true);
        RefreshToken token = new RefreshToken(
                "boundary-token",
                user,
                START
        );
        when(repository.findByToken("boundary-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.requireValid("boundary-token"))
                .isInstanceOf(RefreshTokenException.class)
                .hasMessage("Refresh token expiré, reconnexion nécessaire");

        verify(repository).delete(token);
    }

    @Test
    void tokenForDisabledUserIsRejectedAndDeleted() {
        User user = user(false);
        RefreshToken token = new RefreshToken(
                "disabled-user-token",
                user,
                START.plusSeconds(60)
        );

        when(repository.findByToken("disabled-user-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.requireValid("disabled-user-token"))
                .isInstanceOf(RefreshTokenException.class)
                .hasMessage("Compte désactivé");

        verify(repository).delete(token);
    }

    @Test
    void unknownTokenIsRejectedWithoutDeletingAnything() {
        when(repository.findByToken("unknown-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.requireValid("unknown-token"))
                .isInstanceOf(RefreshTokenException.class)
                .hasMessage("Refresh token invalide");

        verify(repository, never()).delete(any(RefreshToken.class));
    }

    @Test
    void existingTokenIsRevoked() {
        RefreshToken token = new RefreshToken(
                "token-to-revoke",
                user(true),
                START.plusSeconds(60)
        );
        when(repository.findByToken("token-to-revoke")).thenReturn(Optional.of(token));

        refreshTokenService.revoke("token-to-revoke");

        verify(repository).delete(token);
    }

    private User user(boolean active) {
        User user = new User("driver@gml.local", "hashed-password", Role.DRIVER);
        user.setActive(active);
        return user;
    }
}
