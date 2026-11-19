package com.gml.shield.exception;

/** Signale qu'une modification n'est pas autorisée dans l'état actuel de la mission. */
public class MissionChangeNotAllowedException extends RuntimeException {

  public MissionChangeNotAllowedException(String message) {
    super(message);
  }
}
