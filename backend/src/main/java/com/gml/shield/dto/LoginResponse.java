package com.gml.shield.dto;

/**
 * Informations utilisateur renvoyées après authentification.
 */
public record LoginResponse(String email, String role) {
}
