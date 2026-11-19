package com.gml.shield.controller;

import com.gml.shield.dto.DocumentResponse;
import com.gml.shield.security.AppUserDetails;
import com.gml.shield.service.DocumentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * API des documents de mission.
 */
@RestController
@RequestMapping("/api/missions/{missionId}/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DocumentResponse> upload(
            @PathVariable Long missionId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AppUserDetails userDetails) {
        DocumentResponse document = documentService.upload(missionId, file, userDetails.getUser());
        return ResponseEntity.ok(document);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DISPATCHER', 'DRIVER')")
    public ResponseEntity<List<DocumentResponse>> list(
            @PathVariable Long missionId,
            @AuthenticationPrincipal AppUserDetails userDetails) {
        List<DocumentResponse> documents = documentService.list(missionId, userDetails.getUser());
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/{docId}")
    @PreAuthorize("hasRole('DISPATCHER')")
    public ResponseEntity<byte[]> download(
            @PathVariable Long missionId,
            @PathVariable Long docId,
            @AuthenticationPrincipal AppUserDetails userDetails) {
        DocumentService.DownloadedDocument document = documentService.download(
                missionId,
                docId,
                userDetails.getUser()
        );

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + sanitizeForHeader(document.originalFilename())
                                + "\""
                )
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(document.mimeType()))
                .body(document.content());
    }

    private String sanitizeForHeader(String filename) {
        if (filename == null) {
            return "document";
        }
        return filename.replaceAll("[\\r\\n\"]", "_");
    }
}
