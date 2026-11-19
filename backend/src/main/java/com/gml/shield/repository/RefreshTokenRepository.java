package com.gml.shield.repository;

import com.gml.shield.model.RefreshToken;
import com.gml.shield.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Accès aux refresh tokens enregistrés en base.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByUser(User user);
}
