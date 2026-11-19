package com.gml.shield.exception;

/** Signale un dépôt de preuve hors de la période autorisée. */
public class DocumentUploadUnavailableException extends RuntimeException {

  public DocumentUploadUnavailableException(String message) {
    super(message);
  }
}
