package com.gml.shield.controller;

import com.gml.shield.security.AppUserDetails;
import com.gml.shield.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Ouvre le flux SSE utilisé pour envoyer les notifications au chauffeur.
 *
 * L'identité provient exclusivement du contexte Spring Security alimenté par
 * le cookie JWT HttpOnly. Aucun JWT, refresh token ou autre secret
 * d'authentification ne doit être transmis dans l'URL du flux SSE.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('DRIVER')")
    public SseEmitter stream(@AuthenticationPrincipal AppUserDetails userDetails) {
        return notificationService.openConnection(userDetails.getUser().getEmail());
    }
}
