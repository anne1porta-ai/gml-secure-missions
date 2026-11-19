package com.gml.shield.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gml.shield.dto.CreateUserRequest;
import com.gml.shield.dto.UserResponse;
import com.gml.shield.enums.Role;
import com.gml.shield.exception.EmailAlreadyUsedException;
import com.gml.shield.exception.UserManagementException;
import com.gml.shield.exception.UserNotFoundException;
import com.gml.shield.model.User;
import com.gml.shield.repository.UserRepository;
import com.gml.shield.security.RefreshTokenService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

  @Mock private UserRepository userRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private RefreshTokenService refreshTokenService;

  private UserManagementService userManagementService;

  @BeforeEach
  void setUp() {
    userManagementService =
        new UserManagementService(userRepository, passwordEncoder, refreshTokenService);
  }

  @Test
  void createsDriverWithNormalizedEmailAndHashedTemporaryPassword() {
    CreateUserRequest request =
        new CreateUserRequest(" Driver2@GML.local ", "temporaire-123456", Role.DRIVER);

    when(userRepository.findByEmail("driver2@gml.local")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("temporaire-123456")).thenReturn("hashed");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    UserResponse result = userManagementService.createUser(request);

    ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(savedUser.capture());
    assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("hashed");
    assertThat(result.email()).isEqualTo("driver2@gml.local");
    assertThat(result.role()).isEqualTo(Role.DRIVER);
    assertThat(result.active()).isTrue();
  }

  @Test
  void administratorCannotBeCreatedFromTheInterface() {
    CreateUserRequest request =
        new CreateUserRequest("new-admin@gml.local", "temporaire-123456", Role.ADMIN);

    assertThatThrownBy(() -> userManagementService.createUser(request))
        .isInstanceOf(UserManagementException.class);

    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void emailAlreadyUsedIsRejected() {
    CreateUserRequest request =
        new CreateUserRequest("driver1@gml.local", "temporaire-123456", Role.DRIVER);

    when(userRepository.findByEmail("driver1@gml.local"))
        .thenReturn(Optional.of(user("driver1@gml.local", Role.DRIVER)));

    assertThatThrownBy(() -> userManagementService.createUser(request))
        .isInstanceOf(EmailAlreadyUsedException.class);

    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void deactivatingDriverAlsoRevokesItsSessions() {
    User driver = user("driver1@gml.local", Role.DRIVER);
    when(userRepository.findById(5L)).thenReturn(Optional.of(driver));

    UserResponse result = userManagementService.updateActive(5L, false);

    assertThat(driver.isActive()).isFalse();
    assertThat(result.active()).isFalse();
    verify(userRepository).save(driver);
    verify(refreshTokenService).revokeAllFor(driver);
  }

  @Test
  void reactivationKeepsSessionsUntouched() {
    User driver = user("driver1@gml.local", Role.DRIVER);
    driver.setActive(false);
    when(userRepository.findById(5L)).thenReturn(Optional.of(driver));

    UserResponse result = userManagementService.updateActive(5L, true);

    assertThat(result.active()).isTrue();
    verify(refreshTokenService, never()).revokeAllFor(any(User.class));
  }

  @Test
  void administratorCannotBeDeactivated() {
    User admin = user("admin1@gml.local", Role.ADMIN);
    when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

    assertThatThrownBy(() -> userManagementService.updateActive(1L, false))
        .isInstanceOf(UserManagementException.class);

    assertThat(admin.isActive()).isTrue();
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void unknownAccountIsReported() {
    when(userRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userManagementService.updateActive(99L, false))
        .isInstanceOf(UserNotFoundException.class);
  }

  private User user(String email, Role role) {
    return new User(email, "hashed-password", role);
  }
}
