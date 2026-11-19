package com.gml.shield.exception;

/**
 * Signale qu'un document n'est pas accessible pour l'utilisateur courant.
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String message) {
        super(message);
    }
}
