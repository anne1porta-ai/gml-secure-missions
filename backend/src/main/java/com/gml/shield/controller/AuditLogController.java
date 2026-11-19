package com.gml.shield.controller;

import com.gml.shield.dto.AuditLogResponse;
import com.gml.shield.model.AuditLog;
import com.gml.shield.repository.AuditLogRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Permet à l'administrateur de consulter le journal d'audit.
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditLogResponse> list() {
        return auditLogRepository.findRecentAuditLogs().stream()
                .map(AuditLogController::toResponse)
                .toList();
    }

    private static AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getUserId(),
                auditLog.getEmail(),
                auditLog.getAction(),
                auditLog.getIpAddress(),
                auditLog.getStatusCode(),
                auditLog.getTimestamp()
        );
    }
}
