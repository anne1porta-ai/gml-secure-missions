package com.gml.shield.event;

import java.time.Instant;

/**
 * Evénement métier publié pendant la transaction de création d'une mission.
 * Il ne contient que les données nécessaires à la notification afin d'éviter
 * toute dépendance à une entité JPA après la fin de la transaction.
 */
public record MissionCreatedEvent(
        String driverEmail,
        Long missionId,
        String title,
        String destination,
        Instant occurredAt
) {
}
