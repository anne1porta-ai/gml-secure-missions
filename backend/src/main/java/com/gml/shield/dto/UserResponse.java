package com.gml.shield.dto;

import com.gml.shield.enums.Role;

/** Compte tel que l'administrateur le voit : jamais de mot de passe ni de hash. */
public record UserResponse(Long id, String email, Role role, boolean active) {}
