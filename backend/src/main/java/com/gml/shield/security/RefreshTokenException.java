package com.gml.shield.security;
/**
 * Erreur utilisée lorsqu'un refresh token n'est plus valide.
 */

public class RefreshTokenException extends RuntimeException {

    public RefreshTokenException(String message) {
        super(message);
    }
}
