package com.gml.shield.service;

import com.gml.shield.model.AuditLog;
import com.gml.shield.repository.AuditLogRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Enregistre les actions dans le journal d'audit.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // L'écriture du journal se fait en arrière-plan pour ne pas ralentir la requête métier.
    // Ce mode asynchrone ne bloque pas l'opération métier, mais ne garantit
    // pas la conservation d'une trace si le processus s'arrête avant l'écriture.
    // PostgreSQL ne rend pas non plus ces journaux inaltérables.
    @Async
    public void recordAction(Long userId,
                             String email,
                             String action,
                             String ipAddress,
                             int statusCode) {
        AuditLog auditLog = new AuditLog(
                userId,
                email,
                action,
                ipAddress,
                statusCode
        );

        auditLogRepository.save(auditLog);
    }
}
