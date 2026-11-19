package com.gml.shield.security;

import com.gml.shield.config.JwtProperties;
import com.gml.shield.model.RefreshToken;
import com.gml.shield.model.User;
import com.gml.shield.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Crée, vérifie et révoque les refresh tokens.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final long expirationMs;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository,
                               JwtProperties properties,
                               Clock clock) {
        this.repository = repository;
        this.expirationMs = properties.refreshExpirationMs();
        this.clock = clock;
    }

    @Transactional
    public RefreshToken createFor(User user) {
        // Un seul refresh token reste actif par utilisateur. La rotation invalide
        // l'ancien token dès qu'une nouvelle session est émise et limite sa réutilisation.
        repository.deleteByUser(user);
        repository.flush();

        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID().toString(),
                user,
                Instant.now(clock).plusMillis(expirationMs)
        );

        return repository.save(refreshToken);
    }

    @Transactional
    public RefreshToken requireValid(String tokenValue) {
        RefreshToken refreshToken = findRefreshToken(tokenValue);

        validateNotExpired(refreshToken);
        validateUserIsActive(refreshToken);

        return refreshToken;
    }

    @Transactional
    public void revoke(String tokenValue) {
        repository.findByToken(tokenValue).ifPresent(repository::delete);
    }

    @Transactional
    public void revokeAllFor(User user) {
        // Appelé à la désactivation d'un compte : aucune session longue ne doit lui survivre.
        repository.deleteByUser(user);
    }

    private RefreshToken findRefreshToken(String tokenValue) {
        return repository.findByToken(tokenValue)
                .orElseThrow(() -> new RefreshTokenException("Refresh token invalide"));
    }

    private void validateNotExpired(RefreshToken refreshToken) {
        if (!refreshToken.getExpiryDate().isAfter(Instant.now(clock))) {
            repository.delete(refreshToken);
            throw new RefreshTokenException(
                    "Refresh token expiré, reconnexion nécessaire"
            );
        }
    }

    private void validateUserIsActive(RefreshToken refreshToken) {
        if (!refreshToken.getUser().isActive()) {
            repository.delete(refreshToken);
            throw new RefreshTokenException("Compte désactivé");
        }
    }
}
