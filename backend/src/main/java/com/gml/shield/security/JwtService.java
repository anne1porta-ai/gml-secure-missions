package com.gml.shield.security;

import com.gml.shield.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;

/**
 * Crée, lit et vérifie les JWT utilisés pour l'authentification.
 */
@Component
public class JwtService {

    private final long expirationMs;
    private final SecretKey key;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        String secret = properties.secret();

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET doit être défini.");
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        // Refuser une clé trop courte évite de signer les sessions avec un
        // secret HS256 insuffisant à cause d'une erreur de configuration.
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET doit contenir au moins 32 octets.");
        }

        this.expirationMs = properties.expirationMs();
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.clock = clock;
    }

    public String generateToken(String email, String role) {
        Date now = Date.from(clock.instant());
        Date expiry = new Date(now.getTime() + expirationMs);

        // Le JWT contient une identité et une échéance, mais les droits effectifs
        // restent relus en base par JwtAuthenticationFilter à chaque requête.
        return Jwts.builder()
                .setSubject(email)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseClaims(String token) {
        // Ce contrôle vérifie la signature et l'expiration ; lire simplement
        // le contenu décodé du JWT ne permettrait pas de lui faire confiance.
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .setClock(() -> Date.from(clock.instant()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }
}
