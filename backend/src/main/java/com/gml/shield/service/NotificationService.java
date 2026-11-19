package com.gml.shield.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère les connexions SSE et les notifications.
 */
@Service
public class NotificationService {

    private static final long CONNECTION_TIMEOUT_MS = 30L * 60 * 1000;

    // Chaque e-mail pointe vers les connexions du chauffeur, pas vers une diffusion globale.
    // Cette liste vit en mémoire dans UNE instance : elle ne synchronise pas plusieurs serveurs.
    private final ConcurrentHashMap<String, Set<SseEmitter>> emittersByEmail =
            new ConcurrentHashMap<>();

    public SseEmitter openConnection(String email) {
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT_MS);
        Set<SseEmitter> userEmitters = getOrCreateUserEmitters(email);

        userEmitters.add(emitter);
        registerConnectionCleanup(email, emitter);
        sendConnectionConfirmation(email, emitter);

        return emitter;
    }

    public void sendNotificationToUser(String email, String eventName, Object data) {
        Set<SseEmitter> userEmitters = emittersByEmail.get(email);

        // Si le chauffeur est hors ligne, cet événement n'est pas mis en attente.
        // Le navigateur retrouvera l'état de la mission via REST à sa reconnexion.
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        // On parcourt une copie car un envoi en erreur peut supprimer un emitter pendant
        // la boucle sans perturber les autres connexions encore actives.
        Set<SseEmitter> emittersSnapshot = new HashSet<>(userEmitters);

        for (SseEmitter emitter : emittersSnapshot) {
            sendEvent(email, emitter, eventName, data);
        }
    }

    private Set<SseEmitter> getOrCreateUserEmitters(String email) {
        // Un utilisateur peut avoir plusieurs onglets ouverts ; chaque onglet possède son
        // propre flux SSE. La collection concurrente évite de verrouiller toutes les connexions.
        return emittersByEmail.computeIfAbsent(
                email,
                ignoredEmail -> ConcurrentHashMap.newKeySet()
        );
    }

    private void registerConnectionCleanup(String email, SseEmitter emitter) {
        // Retirer les flux fermés évite d'accumuler des connexions inutilisables
        // pour un chauffeur qui ouvre et ferme souvent son navigateur.
        emitter.onCompletion(() -> removeEmitter(email, emitter));
        emitter.onTimeout(() -> removeEmitter(email, emitter));
        emitter.onError(error -> removeEmitter(email, emitter));
    }

    private void sendConnectionConfirmation(String email, SseEmitter emitter) {
        try {
            SseEmitter.SseEventBuilder connectedEvent = SseEmitter.event()
                    .name("CONNECTED")
                    .data("ok");

            emitter.send(connectedEvent);
        } catch (IOException exception) {
            removeEmitter(email, emitter);
            emitter.completeWithError(exception);
        }
    }

    private void sendEvent(String email,
                           SseEmitter emitter,
                           String eventName,
                           Object data) {
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .name(eventName)
                    .data(data);

            emitter.send(event);
        } catch (IOException | IllegalStateException exception) {
            removeEmitter(email, emitter);
            emitter.complete();
        }
    }

    private void removeEmitter(String email, SseEmitter emitter) {
        Set<SseEmitter> userEmitters = emittersByEmail.get(email);

        if (userEmitters == null) {
            return;
        }

        userEmitters.remove(emitter);

        if (userEmitters.isEmpty()) {
            emittersByEmail.remove(email, userEmitters);
        }
    }
}
