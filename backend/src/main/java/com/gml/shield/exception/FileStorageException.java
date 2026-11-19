package com.gml.shield.exception;

/**
 * Signale un échec technique pendant l'enregistrement d'un fichier.
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(Throwable cause) {
        super(cause);
    }
}
