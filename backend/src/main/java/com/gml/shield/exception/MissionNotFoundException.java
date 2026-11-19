package com.gml.shield.exception;

/**
 * Signale qu'une mission n'est pas accessible pour l'utilisateur courant.
 */
public class MissionNotFoundException extends RuntimeException {

    public MissionNotFoundException(String message) {
        super(message);
    }
}
