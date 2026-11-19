package com.gml.shield.dto;

import jakarta.validation.constraints.NotNull;

/** Activation ou désactivation d'un compte. */
public record UserStatusUpdateRequest(@NotNull Boolean active) {}
