package com.gml.shield.exception;

/**
 * Signale une règle métier invalide lors du traitement d'une mission.
 */
public class MissionBadRequestException extends RuntimeException {

    public MissionBadRequestException(String message) {
        super(message);
    }
}
