package com.gml.shield.exception;

/** Signale qu'un compte existe déjà avec cette adresse e-mail. */
public class EmailAlreadyUsedException extends RuntimeException {

  public EmailAlreadyUsedException(String message) {
    super(message);
  }
}
