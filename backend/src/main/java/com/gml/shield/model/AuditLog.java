package com.gml.shield.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Représente une ligne du journal d'audit.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email")
    private String email;

    @Column(nullable = false)
    private String action;

    @Column(name = "ip_address", nullable = false)
    private String ipAddress;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    protected AuditLog() {
        // Constructeur utilisé par JPA.
    }

    public AuditLog(Long userId, String email, String action, String ipAddress, int statusCode) {
        this.userId = userId;
        this.email = email;
        this.action = action;
        this.ipAddress = ipAddress;
        this.statusCode = statusCode;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getAction() { return action; }
    public String getIpAddress() { return ipAddress; }
    public int getStatusCode() { return statusCode; }
    public Instant getTimestamp() { return timestamp; }
}
