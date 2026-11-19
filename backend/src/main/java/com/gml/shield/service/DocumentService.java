package com.gml.shield.service;

import com.gml.shield.dto.DocumentResponse;
import com.gml.shield.enums.MissionStatus;
import com.gml.shield.exception.DocumentNotFoundException;
import com.gml.shield.exception.DocumentUploadUnavailableException;
import com.gml.shield.model.Document;
import com.gml.shield.model.Mission;
import com.gml.shield.model.User;
import com.gml.shield.repository.DocumentRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Gestion des documents associés aux missions. */
@Service
public class DocumentService {

  private final DocumentRepository documentRepository;
  private final MissionService missionService;
  private final FileStorageService fileStorageService;

  public DocumentService(
      DocumentRepository documentRepository,
      MissionService missionService,
      FileStorageService fileStorageService) {
    this.documentRepository = documentRepository;
    this.missionService = missionService;
    this.fileStorageService = fileStorageService;
  }

  @Transactional
  public DocumentResponse upload(Long missionId, MultipartFile file, User driver) {
    // Le chauffeur doit être affecté à la mission et celle-ci doit être en cours.
    Mission mission = missionService.getDriverMission(missionId, driver);

    if (mission.getStatus() != MissionStatus.IN_PROGRESS) {
      throw new DocumentUploadUnavailableException(
          "Le dépôt de preuve est autorisé uniquement pendant une mission en cours");
    }

    // Vérifier le chauffeur et l'état de la mission AVANT d'écrire sur le disque :
    // une requête non autorisée ne doit pas laisser de fichier derrière elle.
    FileStorageService.StoredFile storedFile = fileStorageService.storeFile(file);
    Document document =
        new Document(
            mission,
            storedFile.storagePath(),
            file.getOriginalFilename(),
            storedFile.detectedMimeType());

    return toResponse(documentRepository.save(document));
  }

  @Transactional(readOnly = true)
  public List<DocumentResponse> list(Long missionId, User user) {
    // L'accès à la liste des preuves dépend d'abord de la mission elle-même :
    // connaître son identifiant ne suffit pas à consulter ses justificatifs.
    missionService.getVisibleMission(missionId, user);

    List<Document> documents = documentRepository.findDocumentsForMission(missionId);
    List<DocumentResponse> responses = new ArrayList<>();

    for (Document document : documents) {
      responses.add(toResponse(document));
    }

    return responses;
  }

  @Transactional(readOnly = true)
  public DownloadedDocument download(Long missionId, Long documentId, User dispatcher) {
    // La propriété du répartiteur est aussi vérifiée dans la requête au document :
    // un identifiant de fichier deviné ne permet pas le téléchargement.
    Document document =
        documentRepository
            .findDocumentForDispatcher(documentId, missionId, dispatcher.getEmail())
            .orElse(null);

    if (document == null) {
      throw new DocumentNotFoundException("Document introuvable");
    }

    byte[] content = fileStorageService.readFile(document.getFilePath());
    return new DownloadedDocument(content, document.getOriginalFilename(), document.getMimeType());
  }

  private static DocumentResponse toResponse(Document document) {
    return new DocumentResponse(
        document.getId(),
        document.getOriginalFilename(),
        document.getMimeType(),
        document.getUploadedAt());
  }

  public record DownloadedDocument(byte[] content, String originalFilename, String mimeType) {}
}
