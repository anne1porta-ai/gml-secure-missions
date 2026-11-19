package com.gml.shield.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Format d'erreur JSON commun aux endpoints REST.
 *
 * Le champ {@code error} est conservé volontairement pour rester compatible
 * avec le frontend existant, tandis que {@code code} et {@code details}
 * rendent les erreurs plus faciles à diagnostiquer et à documenter.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        String code,
        String error,
        List<String> details,
        String timestamp
) {
    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, List.of(), Instant.now().toString());
    }

    public static ApiErrorResponse validation(List<String> details) {
        return new ApiErrorResponse(
                "VALIDATION_ERROR",
                "Requête invalide",
                details,
                Instant.now().toString()
        );
    }
}
