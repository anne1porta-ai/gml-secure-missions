package com.gml.shield.dto;

import com.gml.shield.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Création d'un compte par l'administrateur.
 *
 * <p>Le mot de passe temporaire est transmis au collaborateur hors de l'application. Sa longueur
 * maximale reste sous la limite de 72 octets de BCrypt.
 */
public record CreateUserRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 12, max = 64) String temporaryPassword,
    @NotNull Role role) {}
