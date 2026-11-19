package com.gml.shield.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Chauffeur choisi pour une mission en attente. */
public record ReassignMissionRequest(@NotBlank @Email @Size(max = 254) String driverEmail) {}
