package com.gml.shield.model;

import com.gml.shield.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Compte utilisateur de l'application.
 *
 * <p>Seule l'empreinte BCrypt du mot de passe est stockée (passwordHash) : ne jamais ajouter de
 * champ qui contiendrait le mot de passe en clair.
 *
 * <p>active permet de couper un accès sans supprimer le compte ni son historique d'audit.
 */
@Entity
@Table(name = "app_user")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  // Le mot de passe est conservé uniquement sous forme hachée en base.
  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  protected User() {
    // Constructeur utilisé par JPA.
  }

  public User(String email, String passwordHash, Role role) {
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
  }

  public Long getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
