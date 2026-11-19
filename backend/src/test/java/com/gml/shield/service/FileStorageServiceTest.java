package com.gml.shield.service;

import com.gml.shield.exception.FileRejectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    private static final int MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

    @TempDir
    Path storageDirectory;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService(storageDirectory.toString());
        fileStorageService.init();
    }

    @Test
    void emptyFileIsRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        assertThatThrownBy(() -> fileStorageService.storeFile(file))
                .isInstanceOf(FileRejectedException.class)
                .hasMessage("Fichier vide");
    }

    @Test
    void unsupportedFileTypeIsRejectedFromItsContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.pdf",
                "application/pdf",
                "simple text file".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> fileStorageService.storeFile(file))
                .isInstanceOf(FileRejectedException.class)
                .hasMessageContaining("Type de fichier non autorisé");
    }

    @Test
    void fileExactlyAtSizeLimitIsAccepted() {
        byte[] pdfContent = pdfContent(MAX_FILE_SIZE_BYTES);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "limit.pdf",
                "application/octet-stream",
                pdfContent
        );

        FileStorageService.StoredFile storedFile = fileStorageService.storeFile(file);

        assertThat(storedFile.detectedMimeType()).isEqualTo("application/pdf");
        assertThat(Files.exists(Path.of(storedFile.storagePath()))).isTrue();
    }

    @Test
    void fileOneByteAboveSizeLimitIsRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "too-large.pdf",
                "application/pdf",
                new byte[MAX_FILE_SIZE_BYTES + 1]
        );

        assertThatThrownBy(() -> fileStorageService.storeFile(file))
                .isInstanceOf(FileRejectedException.class)
                .hasMessage("Fichier trop volumineux (max 10 Mo)");
    }

    @Test
    void validPdfIsStoredWithDetectedMimeType() throws Exception {
        byte[] pdfContent = pdfContent(128);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proof.pdf",
                "application/octet-stream",
                pdfContent
        );

        FileStorageService.StoredFile storedFile = fileStorageService.storeFile(file);

        Path storedPath = Path.of(storedFile.storagePath());
        assertThat(storedFile.detectedMimeType()).isEqualTo("application/pdf");
        assertThat(storedPath).startsWith(storageDirectory.toAbsolutePath());
        assertThat(Files.readAllBytes(storedPath)).isEqualTo(pdfContent);
    }

    @Test
    void readOutsideStorageDirectoryIsRejected() throws Exception {
        Path outsideFile = storageDirectory.getParent().resolve("outside.txt");
        Files.writeString(outsideFile, "outside", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> fileStorageService.readFile(outsideFile.toString()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("hors du répertoire");
    }

    private byte[] pdfContent(int size) {
        byte[] content = new byte[size];
        byte[] header = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        byte[] end = "\n%%EOF".getBytes(StandardCharsets.US_ASCII);

        System.arraycopy(header, 0, content, 0, header.length);
        System.arraycopy(end, 0, content, content.length - end.length, end.length);
        return content;
    }
}
