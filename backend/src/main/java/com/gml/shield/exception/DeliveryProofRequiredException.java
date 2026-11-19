package com.gml.shield.exception;

/** Signale qu'une mission ne peut pas être terminée sans preuve de livraison. */
public class DeliveryProofRequiredException extends RuntimeException {

  public DeliveryProofRequiredException(String message) {
    super(message);
  }
}
