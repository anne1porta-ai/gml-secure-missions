package com.gml.shield.config;

import com.gml.shield.enums.Role;
import com.gml.shield.model.User;
import com.gml.shield.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crée les comptes de démonstration Dispatcher et Driver, pour le développement local uniquement
 * (désactivé par défaut et en production).
 *
 * Le compte administrateur n'en fait pas partie : il est créé depuis ADMIN_EMAIL et
 * ADMIN_PASSWORD par AdminAccountInitializer, sans mot de passe écrit dans le code.
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed-enabled", havingValue = "true")
public class DemoDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        createDemoUserIfMissing(
                "dispatcher1@gml.local",
                "dispatch123",
                Role.DISPATCHER
        );

        createDemoUserIfMissing(
                "driver1@gml.local",
                "driver123",
                Role.DRIVER
        );
    }

    private void createDemoUserIfMissing(String email, String plainPassword, Role role) {
        // Un compte existant n'est jamais écrasé ; le compte administrateur initial, créé
        // à part, n'empêche donc plus la création des comptes de démonstration.
        if (userRepository.findByEmail(email).isPresent()) {
            return;
        }

        String encodedPassword = passwordEncoder.encode(plainPassword);
        User user = new User(email, encodedPassword, role);
        userRepository.save(user);
    }
}
