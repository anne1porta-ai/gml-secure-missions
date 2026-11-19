package com.gml.shield.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Données de création d'une mission.
 */
public record CreateMissionRequest(
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 500) String destination,
        @NotBlank @Size(max = 200) String secretCode,
        @NotBlank @Email @Size(max = 254) String driverEmail,
        // Détails facultatifs : la date planifiée est une heure locale, sans fuseau.
        @Size(max = 1000) String description,
        LocalDateTime scheduledAt
) {
}
