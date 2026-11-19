package com.gml.shield.service;

import com.gml.shield.exception.FileReadException;
import com.gml.shield.exception.FileRejectedException;
import com.gml.shield.exception.FileStorageException;
import jakarta.annotation.PostConstruct;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * Vérifie et stocke les fichiers envoyés comme preuves de livraison.
 */
@Service
public class FileStorageService {

    private static final Map<String, String> ALLOWED_FILE_TYPES = Map.of(
            "application/pdf", ".pdf",
            "image/jpeg", ".jpg",
            "image/png", ".png"
    );

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private final String storageDir;
    private Path storageRoot;
    private final Tika tika = new Tika();

    public FileStorageService(@Value("${app.storage.documents-dir}") String storageDir) {
        this.storageDir = storageDir;
    }

    @PostConstruct
    public void init() {
        storageRoot = Paths.get(storageDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(storageRoot);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Impossible de créer le répertoire de stockage: " + storageRoot,
                    exception
            );
        }
    }

    public record StoredFile(String storagePath, String detectedMimeType) {
    }

    public StoredFile storeFile(MultipartFile file) {
        validateFile(file);

        // La taille a été contrôlée AVANT cette lecture intégrale en mémoire :
        // sans plafond, une pièce jointe pourrait épuiser la RAM du serveur.
        byte[] fileContent = readUploadedFile(file);
        String detectedMimeType = detectMimeType(fileContent);
        validateMimeType(detectedMimeType);

        String storedFilename = createStoredFilename(detectedMimeType);
        Path destination = resolveDestination(storedFilename);

        writeFile(destination, fileContent);

        return new StoredFile(destination.toString(), detectedMimeType);
    }

    public byte[] readFile(String storagePath) {
        Path requestedPath = Paths.get(storagePath).toAbsolutePath().normalize();
        validateStoredPath(requestedPath);

        try {
            return Files.readAllBytes(requestedPath);
        } catch (IOException exception) {
            throw new FileReadException(exception);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new FileRejectedException("Fichier vide");
        }

        long fileSize = file.getSize();
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            throw new FileRejectedException("Fichier trop volumineux (max 10 Mo)");
        }
    }

    private byte[] readUploadedFile(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new FileStorageException(exception);
        }
    }

   private String detectMimeType(byte[] fileContent) {
    // Le navigateur peut déclarer un type MIME incorrect ou falsifié. Tika vérifie
    // donc le contenu réel avant d'accepter le fichier.
    // Cette reconnaissance de format ne remplace pas une analyse antivirus.
    return tika.detect(fileContent);
    }

    private void validateMimeType(String detectedMimeType) {
        if (!ALLOWED_FILE_TYPES.containsKey(detectedMimeType)) {
            throw new FileRejectedException(
                    "Type de fichier non autorisé: " + detectedMimeType
            );
        }
    }

    private String createStoredFilename(String detectedMimeType) {
        // Le nom fourni par l'utilisateur n'est jamais utilisé comme chemin de stockage :
        // un UUID évite collisions et tentatives d'injection de chemin dans le nom du fichier.
        String uniqueName = UUID.randomUUID().toString();
        String extension = ALLOWED_FILE_TYPES.get(detectedMimeType);
        return uniqueName + extension;
    }

    private Path resolveDestination(String storedFilename) {
        Path destination = storageRoot.resolve(storedFilename).normalize();
        validateStoredPath(destination);
        return destination;
    }

    private void validateStoredPath(Path path) {
        // Cette vérification bloque une valeur de chemin qui tenterait de sortir du
        // répertoire prévu, par exemple via une séquence de type ../.
        if (!path.startsWith(storageRoot)) {
            throw new SecurityException(
                    "Tentative d'accès hors du répertoire de stockage autorisé"
            );
        }
    }

    private void writeFile(Path destination, byte[] fileContent) {
        try {
            Files.write(destination, fileContent);
        } catch (IOException exception) {
            throw new FileStorageException(exception);
        }
    }
}
