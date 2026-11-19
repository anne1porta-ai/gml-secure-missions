package com.gml.shield.dto;

import java.time.LocalDateTime;

/**
 * Contrat de sortie d'un document de preuve.
 */
public record DocumentResponse(
        Long id,
        String originalFilename,
        String mimeType,
        LocalDateTime uploadedAt
) {
}
