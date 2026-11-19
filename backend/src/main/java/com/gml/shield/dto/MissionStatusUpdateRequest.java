package com.gml.shield.dto;

import com.gml.shield.enums.MissionStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Nouveau statut demandé pour une mission.
 */
public record MissionStatusUpdateRequest(@NotNull MissionStatus status) {
}
