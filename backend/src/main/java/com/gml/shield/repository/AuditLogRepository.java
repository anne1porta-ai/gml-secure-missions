package com.gml.shield.repository;

import com.gml.shield.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Accès aux données du journal d'audit.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // L'écran d'audit montre d'abord les actions récentes, celles qui sont les plus utiles
    // lors d'une analyse d'incident ou d'un contrôle après modification.
    @Query("SELECT a FROM AuditLog a ORDER BY a.timestamp DESC")
    List<AuditLog> findRecentAuditLogs();
}
