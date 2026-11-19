package com.gml.shield.exception;

/** Signale une opération d'administration des comptes refusée par une règle métier. */
public class UserManagementException extends RuntimeException {

  public UserManagementException(String message) {
    super(message);
  }
}
