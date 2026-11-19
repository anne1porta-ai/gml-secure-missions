package com.gml.shield.controller;

import com.gml.shield.dto.ApiErrorResponse;
import com.gml.shield.exception.DeliveryProofRequiredException;
import com.gml.shield.exception.DocumentNotFoundException;
import com.gml.shield.exception.DocumentUploadUnavailableException;
import com.gml.shield.exception.EmailAlreadyUsedException;
import com.gml.shield.exception.FileReadException;
import com.gml.shield.exception.FileRejectedException;
import com.gml.shield.exception.FileStorageException;
import com.gml.shield.exception.MissionBadRequestException;
import com.gml.shield.exception.MissionChangeNotAllowedException;
import com.gml.shield.exception.MissionNotFoundException;
import com.gml.shield.exception.SecretCodeUnavailableException;
import com.gml.shield.exception.UserManagementException;
import com.gml.shield.exception.UserNotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Centralise les erreurs REST pour fournir un contrat JSON prévisible.
 *
 * <p>Les détails techniques internes ne sont jamais renvoyés au client. Les erreurs de validation
 * exposent uniquement les champs concernés et des messages métier.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidation(
      MethodArgumentNotValidException exception) {
    List<String> details =
        exception.getBindingResult().getFieldErrors().stream().map(this::formatFieldError).toList();

    return ResponseEntity.badRequest().body(ApiErrorResponse.validation(details));
  }

  @ExceptionHandler(MissionBadRequestException.class)
  public ResponseEntity<ApiErrorResponse> handleMissionBadRequest(
      MissionBadRequestException exception) {
    return ResponseEntity.badRequest()
        .body(ApiErrorResponse.of("MISSION_BAD_REQUEST", exception.getMessage()));
  }

  @ExceptionHandler(DeliveryProofRequiredException.class)
  public ResponseEntity<ApiErrorResponse> handleDeliveryProofRequired(
      DeliveryProofRequiredException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiErrorResponse.of("DELIVERY_PROOF_REQUIRED", exception.getMessage()));
  }

  @ExceptionHandler(MissionChangeNotAllowedException.class)
  public ResponseEntity<ApiErrorResponse> handleMissionChangeNotAllowed(
      MissionChangeNotAllowedException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiErrorResponse.of("MISSION_CHANGE_NOT_ALLOWED", exception.getMessage()));
  }

  @ExceptionHandler(MissionNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleMissionNotFound(
      MissionNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiErrorResponse.of("MISSION_NOT_FOUND", exception.getMessage()));
  }

  @ExceptionHandler(SecretCodeUnavailableException.class)
  public ResponseEntity<ApiErrorResponse> handleSecretCodeUnavailable(
      SecretCodeUnavailableException exception) {
    // 409 : la demande est légitime mais incompatible avec l'état actuel de la mission.
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiErrorResponse.of("SECRET_CODE_UNAVAILABLE", exception.getMessage()));
  }

  @ExceptionHandler(UserNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleUserNotFound(UserNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiErrorResponse.of("USER_NOT_FOUND", exception.getMessage()));
  }

  @ExceptionHandler(EmailAlreadyUsedException.class)
  public ResponseEntity<ApiErrorResponse> handleEmailAlreadyUsed(
      EmailAlreadyUsedException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiErrorResponse.of("EMAIL_ALREADY_USED", exception.getMessage()));
  }

  @ExceptionHandler(UserManagementException.class)
  public ResponseEntity<ApiErrorResponse> handleUserManagement(UserManagementException exception) {
    return ResponseEntity.badRequest()
        .body(ApiErrorResponse.of("USER_MANAGEMENT_REJECTED", exception.getMessage()));
  }

  @ExceptionHandler(DocumentUploadUnavailableException.class)
  public ResponseEntity<ApiErrorResponse> handleDocumentUploadUnavailable(
      DocumentUploadUnavailableException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiErrorResponse.of("DOCUMENT_UPLOAD_UNAVAILABLE", exception.getMessage()));
  }

  @ExceptionHandler(DocumentNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleDocumentNotFound(
      DocumentNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiErrorResponse.of("DOCUMENT_NOT_FOUND", exception.getMessage()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiErrorResponse.of("FORBIDDEN", "Accès interdit"));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
      HttpMessageNotReadableException exception) {
    return ResponseEntity.badRequest()
        .body(ApiErrorResponse.of("MALFORMED_JSON", "Corps JSON invalide"));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiErrorResponse.of("ROUTE_NOT_FOUND", "Route introuvable"));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException exception) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(ApiErrorResponse.of("METHOD_NOT_ALLOWED", "Méthode HTTP non autorisée"));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(
      MaxUploadSizeExceededException exception) {
    return ResponseEntity.badRequest()
        .body(ApiErrorResponse.of("FILE_TOO_LARGE", "Fichier trop volumineux (max 10 Mo)"));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidPathParameter(
      MethodArgumentTypeMismatchException exception) {
    return ResponseEntity.badRequest()
        .body(ApiErrorResponse.of("BAD_REQUEST", "Paramètre de route invalide"));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
      IllegalArgumentException exception) {
    LOGGER.debug("Requête rejetée pour argument invalide", exception);
    return ResponseEntity.badRequest()
        .body(ApiErrorResponse.of("BAD_REQUEST", "La requête contient une valeur invalide"));
  }

  @ExceptionHandler(FileRejectedException.class)
  public ResponseEntity<ApiErrorResponse> handleFileRejected(FileRejectedException exception) {
    return ResponseEntity.badRequest()
        .body(ApiErrorResponse.of("FILE_REJECTED", exception.getMessage()));
  }

  @ExceptionHandler(FileStorageException.class)
  public ResponseEntity<ApiErrorResponse> handleFileStorage(FileStorageException exception) {
    LOGGER.error("Erreur pendant l'enregistrement d'un fichier", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiErrorResponse.of("FILE_STORAGE_ERROR", "Échec de l'enregistrement du fichier"));
  }

  @ExceptionHandler(FileReadException.class)
  public ResponseEntity<ApiErrorResponse> handleFileRead(FileReadException exception) {
    LOGGER.error("Erreur pendant la lecture d'un fichier", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiErrorResponse.of("FILE_READ_ERROR", "Échec de la lecture du fichier"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
    LOGGER.error("Erreur inattendue pendant le traitement REST", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiErrorResponse.of("INTERNAL_ERROR", "Erreur interne du serveur"));
  }

  private String formatFieldError(FieldError error) {
    return error.getField() + " : " + error.getDefaultMessage();
  }
}
