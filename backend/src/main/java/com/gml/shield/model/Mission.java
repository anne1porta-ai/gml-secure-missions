package com.gml.shield.model;

import com.gml.shield.enums.MissionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Mission confiée à un chauffeur par un répartiteur.
 *
 * <p>driver et dispatcher servent au cloisonnement (RG1) : les requêtes filtrent sur eux.
 *
 * <p>secretCode est toujours stocké chiffré (RG2) : ne jamais y écrire une valeur en clair.
 */
@Entity
@Table(name = "mission")
public class Mission {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, length = 500)
  private String destination;

  // La valeur stockée ici est déjà chiffrée par SecretEncryptionService avant la sauvegarde.
  @Column(name = "secret_code", nullable = false, columnDefinition = "TEXT")
  private String secretCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MissionStatus status = MissionStatus.PENDING;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "driver_id", nullable = false)
  private User driver;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dispatcher_id", nullable = false)
  private User dispatcher;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  // Détails facultatifs saisis par le dispatcher (migration V2).
  @Column(length = 1000)
  private String description;

  @Column(name = "scheduled_at")
  private LocalDateTime scheduledAt;

  protected Mission() {
    // Constructeur utilisé par JPA.
  }

  public Mission(
      String title, String destination, String secretCode, User driver, User dispatcher) {
    this.title = title;
    this.destination = destination;
    this.secretCode = secretCode;
    this.driver = driver;
    this.dispatcher = dispatcher;
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getDestination() {
    return destination;
  }

  public String getSecretCode() {
    return secretCode;
  }

  public MissionStatus getStatus() {
    return status;
  }

  public User getDriver() {
    return driver;
  }

  public User getDispatcher() {
    return dispatcher;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public String getDescription() {
    return description;
  }

  public LocalDateTime getScheduledAt() {
    return scheduledAt;
  }

  public void setDriver(User driver) {
    this.driver = driver;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public void setDestination(String destination) {
    this.destination = destination;
  }

  public void setStatus(MissionStatus status) {
    this.status = status;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setScheduledAt(LocalDateTime scheduledAt) {
    this.scheduledAt = scheduledAt;
  }
}
