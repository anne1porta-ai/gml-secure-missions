package com.gml.shield.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.gml.shield.enums.Role;
import com.gml.shield.model.User;
import com.gml.shield.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class DatabaseUserDetailsServiceTest {

  @Mock private UserRepository userRepository;

  @Test
  void knownEmailLoadsTheUser() {
    User user = new User("driver@gml.local", "hashed", Role.DRIVER);
    when(userRepository.findByEmail("driver@gml.local")).thenReturn(Optional.of(user));

    DatabaseUserDetailsService service = new DatabaseUserDetailsService(userRepository);
    UserDetails userDetails = service.loadUserByUsername("driver@gml.local");

    assertThat(userDetails.getUsername()).isEqualTo("driver@gml.local");
  }

  @Test
  void unknownEmailIsRejected() {
    when(userRepository.findByEmail("inconnu@gml.local")).thenReturn(Optional.empty());

    DatabaseUserDetailsService service = new DatabaseUserDetailsService(userRepository);

    assertThatThrownBy(() -> service.loadUserByUsername("inconnu@gml.local"))
        .isInstanceOf(UsernameNotFoundException.class)
        .hasMessageContaining("Utilisateur inconnu");
  }
}
