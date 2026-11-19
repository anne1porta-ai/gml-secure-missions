package com.gml.shield.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gml.shield.enums.Role;
import com.gml.shield.model.User;
import com.gml.shield.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

  private static final String PASSWORD = "mot-de-passe-admin-123";

  @Mock private UserRepository userRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @Test
  void createsAdministratorWhenNoneExists() {
    when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
    when(userRepository.findByEmail("admin@gml.local")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");

    initializer(" Admin@GML.local ", PASSWORD).run(null);

    ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(savedUser.capture());
    assertThat(savedUser.getValue().getEmail()).isEqualTo("admin@gml.local");
    assertThat(savedUser.getValue().getRole()).isEqualTo(Role.ADMIN);
    assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("hashed");
  }

  @Test
  void doesNothingWhenAnAdministratorAlreadyExists() {
    when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

    initializer("admin@gml.local", PASSWORD).run(null);

    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void doesNothingWhenNotConfigured() {
    when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

    initializer("", "").run(null);

    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void refusesTooShortPassword() {
    when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

    assertThatThrownBy(() -> initializer("admin@gml.local", "court").run(null))
        .isInstanceOf(IllegalStateException.class);

    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void neverPromotesAnExistingAccount() {
    when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
    when(userRepository.findByEmail("driver1@gml.local"))
        .thenReturn(Optional.of(new User("driver1@gml.local", "hash", Role.DRIVER)));

    initializer("driver1@gml.local", PASSWORD).run(null);

    verify(userRepository, never()).save(any(User.class));
  }

  private AdminAccountInitializer initializer(String email, String password) {
    return new AdminAccountInitializer(userRepository, passwordEncoder, email, password);
  }
}
