package com.gml.shield.config;

import com.gml.shield.security.AppUserDetails;
import com.gml.shield.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Pattern;

/**
 * Enregistre dans le journal d'audit les requêtes qui modifient des données,
 * ainsi que chaque lecture du code d'accès d'une mission.
 */
@Component
public class WriteRequestAuditInterceptor implements HandlerInterceptor {

    // Lecture sensible : chaque demande de code d'accès est tracée, qu'elle soit
    // acceptée (200) ou refusée (403, 404, 409).
    private static final Pattern SECRET_CODE_READ =
            Pattern.compile("^/api/missions/\\d+/secret-code$");

    private final AuditLogService auditLogService;

    public WriteRequestAuditInterceptor(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        // Les lectures ne sont pas journalisées afin de garder un audit centré sur les actions
        // qui modifient l'état métier ; seule la lecture du code d'accès fait exception.
        if (!isMutatingMethod(method) && !isSecretCodeRead(method, uri)) {
            return;
        }

        // La trace décrit l'opération HTTP et sa cible, sans copier le corps de
        // la requête : le code secret ne doit pas se retrouver dans les journaux.
        String action = method + " " + uri;
        // Nginx réécrit l'adresse transmise et Spring la normalise dans remoteAddr.
        // Login, rate limiting et audit utilisent ainsi exactement la même source.
        String ipAddress = request.getRemoteAddr();

        Long userId = null;
        String email = null;

        // L'interception a lieu après le traitement HTTP : on peut ainsi enregistrer
        // l'utilisateur réellement authentifié et le statut final renvoyé au client.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AppUserDetails) {
            AppUserDetails userDetails = (AppUserDetails) authentication.getPrincipal();
            userId = userDetails.getUser().getId();
            email = userDetails.getUser().getEmail();
        }

        // Conserver aussi le statut HTTP permet de distinguer un refus d'une réussite.
        // Ce journal est ciblé : il ne couvre pas toutes les lectures ni tous les rejets en amont.
        auditLogService.recordAction(userId, email, action, ipAddress, response.getStatus());
    }

    private static boolean isMutatingMethod(String method) {
        return method.equals("POST")
                || method.equals("PUT")
                || method.equals("PATCH")
                || method.equals("DELETE");
    }

    private static boolean isSecretCodeRead(String method, String uri) {
        return method.equals("GET") && SECRET_CODE_READ.matcher(uri).matches();
    }
}
