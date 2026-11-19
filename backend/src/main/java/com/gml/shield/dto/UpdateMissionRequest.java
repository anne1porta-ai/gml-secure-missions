package com.gml.shield.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Champs modifiables d'une mission en attente. */
public record UpdateMissionRequest(
    @NotBlank @Size(max = 150) String title,
    @NotBlank @Size(max = 500) String destination,
    @Size(max = 1000) String description,
    LocalDateTime scheduledAt) {}
