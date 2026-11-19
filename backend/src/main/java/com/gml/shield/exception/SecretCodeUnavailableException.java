package com.gml.shield.exception;

/**
 * Signale que le code d'accès ne peut pas être révélé dans l'état actuel de la mission : avant son
 * démarrage ou après la livraison.
 */
public class SecretCodeUnavailableException extends RuntimeException {

  public SecretCodeUnavailableException(String message) {
    super(message);
  }
}
