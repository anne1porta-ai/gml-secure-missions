package com.gml.shield.exception;

/**
 * Signale qu'un fichier ne respecte pas les règles d'upload.
 */
public class FileRejectedException extends RuntimeException {

    public FileRejectedException(String message) {
        super(message);
    }
}
