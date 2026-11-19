package com.gml.shield.exception;

/**
 * Signale un échec technique pendant la lecture d'un fichier stocké.
 */
public class FileReadException extends RuntimeException {

    public FileReadException(Throwable cause) {
        super(cause);
    }
}
