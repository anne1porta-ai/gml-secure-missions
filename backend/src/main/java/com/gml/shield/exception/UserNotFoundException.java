package com.gml.shield.exception;

/** Signale qu'aucun compte ne correspond à l'identifiant demandé. */
public class UserNotFoundException extends RuntimeException {

  public UserNotFoundException(String message) {
    super(message);
  }
}
