package com.gml.shield.dto;

import java.time.Instant;

/**
 * Contrat de sortie d'une trace d'audit.
 *
 * Le contrôleur n'expose pas directement l'entité JPA afin de conserver une
 * frontière stable entre persistance et API.
 */
public record AuditLogResponse(
        Long id,
        Long userId,
        String email,
        String action,
        String ipAddress,
        int statusCode,
        Instant timestamp
) {
}
