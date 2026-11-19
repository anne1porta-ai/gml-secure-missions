package com.gml.shield.config;

import com.gml.shield.enums.Role;
import com.gml.shield.model.User;
import com.gml.shield.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crée le compte administrateur initial au démarrage, à partir de ADMIN_EMAIL et ADMIN_PASSWORD.
 *
 * <p>Aucun mot de passe n'est écrit dans le code. Le compte n'est créé que si aucun administrateur
 * n'existe : une fois l'application initialisée, ces variables peuvent être retirées.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(AdminAccountInitializer.class);
  private static final int MIN_PASSWORD_LENGTH = 12;
  private static final int BCRYPT_MAX_BYTES = 72;

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final String adminEmail;
  private final String adminPassword;

  public AdminAccountInitializer(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      @Value("${app.bootstrap.admin-email:}") String adminEmail,
      @Value("${app.bootstrap.admin-password:}") String adminPassword) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.adminEmail = adminEmail;
    this.adminPassword = adminPassword;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (userRepository.existsByRole(Role.ADMIN)) {
      return;
    }

    if (adminEmail.isBlank() || adminPassword.isBlank()) {
      LOGGER.warn(
          "Aucun administrateur : définir ADMIN_EMAIL et ADMIN_PASSWORD pour en créer un au"
              + " démarrage.");
      return;
    }

    // Configuration incohérente : on bloque le démarrage plutôt que de créer un compte faible.
    if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalStateException("ADMIN_PASSWORD doit contenir au moins 12 caractères");
    }

    if (adminPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
      throw new IllegalStateException("ADMIN_PASSWORD ne doit pas dépasser 72 octets");
    }

    String email = adminEmail.trim().toLowerCase(Locale.ROOT);

    // Un compte existant n'est jamais promu administrateur : ce serait une élévation de
    // privilèges silencieuse.
    if (userRepository.findByEmail(email).isPresent()) {
      LOGGER.warn("ADMIN_EMAIL correspond à un compte existant : aucun administrateur créé.");
      return;
    }

    userRepository.save(new User(email, passwordEncoder.encode(adminPassword), Role.ADMIN));
    LOGGER.info("Compte administrateur initial créé : {}", email);
  }
}
