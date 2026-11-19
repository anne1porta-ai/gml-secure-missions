package com.gml.shield.dto;

import com.gml.shield.enums.MissionStatus;

import java.time.LocalDateTime;

/**
 * Données renvoyées pour une mission.
 *
 * Le code d'accès n'y figure jamais, quel que soit le rôle : le chauffeur assigné l'obtient
 * à la demande via GET /api/missions/{id}/secret-code, et uniquement pendant la mission.
 */
public record MissionResponse(
        Long id,
        String title,
        String destination,
        String description,
        LocalDateTime scheduledAt,
        MissionStatus status,
        String driverEmail,
        String dispatcherEmail,
        LocalDateTime createdAt
) {
}
