package com.gml.shield.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gml.shield.exception.FileReadException;
import com.gml.shield.exception.FileStorageException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class FileStorageServiceErrorTest {

  private static final byte[] PDF_CONTENT =
      "%PDF-1.4\ncontenu de test\n%%EOF".getBytes(StandardCharsets.US_ASCII);

  @TempDir Path tempDirectory;

  private Path storageDirectory;
  private FileStorageService fileStorageService;

  @BeforeEach
  void setUp() {
    storageDirectory = tempDirectory.resolve("documents");
    fileStorageService = new FileStorageService(storageDirectory.toString());
    fileStorageService.init();
  }

  @Test
  void storedFileCanBeReadAgain() {
    MockMultipartFile file =
        new MockMultipartFile("file", "bon.pdf", "application/pdf", PDF_CONTENT);

    FileStorageService.StoredFile storedFile = fileStorageService.storeFile(file);

    assertThat(fileStorageService.readFile(storedFile.storagePath())).isEqualTo(PDF_CONTENT);
  }

  @Test
  void missingFileInStorageIsReportedAsReadError() {
    Path missingFile = storageDirectory.resolve("absent.pdf");

    assertThatThrownBy(() -> fileStorageService.readFile(missingFile.toString()))
        .isInstanceOf(FileReadException.class);
  }

  @Test
  void storageDirectoryThatCannotBeCreatedStopsStartup() throws IOException {
    // Un fichier existe déjà à l'emplacement prévu pour le répertoire.
    Path existingFile = Files.writeString(tempDirectory.resolve("pas-un-dossier"), "x");
    FileStorageService service = new FileStorageService(existingFile.toString());

    assertThatThrownBy(() -> service.init())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Impossible de créer le répertoire de stockage");
  }

  @Test
  void uploadedFileThatCannotBeReadIsRejected() throws IOException {
    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getSize()).thenReturn(100L);
    when(file.getBytes()).thenThrow(new IOException("lecture impossible"));

    assertThatThrownBy(() -> fileStorageService.storeFile(file))
        .isInstanceOf(FileStorageException.class);
  }

  @Test
  void fileThatCannotBeWrittenIsRejected() throws IOException {
    // Le répertoire de stockage a disparu après le démarrage : l'écriture échoue.
    Files.delete(storageDirectory);
    MockMultipartFile file =
        new MockMultipartFile("file", "bon.pdf", "application/pdf", PDF_CONTENT);

    assertThatThrownBy(() -> fileStorageService.storeFile(file))
        .isInstanceOf(FileStorageException.class);
  }
}
