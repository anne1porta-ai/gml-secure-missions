package com.gml.shield.service;

import com.gml.shield.dto.CreateUserRequest;
import com.gml.shield.dto.UserResponse;
import com.gml.shield.enums.Role;
import com.gml.shield.exception.EmailAlreadyUsedException;
import com.gml.shield.exception.UserManagementException;
import com.gml.shield.exception.UserNotFoundException;
import com.gml.shield.model.User;
import com.gml.shield.repository.UserRepository;
import com.gml.shield.security.RefreshTokenService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion des comptes par l'administrateur.
 *
 * <p>Seuls les comptes Dispatcher et Driver sont gérés ici : le compte administrateur est créé au
 * démarrage depuis la configuration et ne peut être ni créé ni désactivé depuis l'interface.
 */
@Service
public class UserManagementService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final RefreshTokenService refreshTokenService;

  public UserManagementService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      RefreshTokenService refreshTokenService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.refreshTokenService = refreshTokenService;
  }

  @Transactional(readOnly = true)
  public List<UserResponse> listUsers() {
    List<User> users = userRepository.findAll(Sort.by("email"));
    List<UserResponse> responses = new ArrayList<>();

    for (User user : users) {
      responses.add(toResponse(user));
    }

    return responses;
  }

  @Transactional
  public UserResponse createUser(CreateUserRequest request) {
    // Refuser la création d'administrateurs via cette API évite qu'un gestionnaire
    // de comptes puisse se donner des privilèges de sécurité supplémentaires.
    if (request.role() == Role.ADMIN) {
      throw new UserManagementException(
          "Un compte administrateur ne peut pas être créé depuis l'interface");
    }

    // Adresse normalisée : une même personne ne doit pas obtenir deux comptes par la casse.
    String email = request.email().trim().toLowerCase(Locale.ROOT);

    if (userRepository.findByEmail(email).isPresent()) {
      throw new EmailAlreadyUsedException("Adresse e-mail déjà utilisée");
    }

    // BCrypt fournit une empreinte non réversible : seule cette valeur rejoint JPA.
    // Le mot de passe temporaire ne doit jamais être conservé dans User.
    String passwordHash = passwordEncoder.encode(request.temporaryPassword());
    User user = userRepository.save(new User(email, passwordHash, request.role()));

    return toResponse(user);
  }

  @Transactional
  public UserResponse updateActive(Long userId, boolean active) {
    User user = userRepository.findById(userId).orElse(null);

    if (user == null) {
      throw new UserNotFoundException("Compte introuvable");
    }

    // Un administrateur ne peut pas être bloqué depuis l'interface : on évite ainsi que
    // l'administrateur se coupe lui-même l'accès à l'application.
    if (user.getRole() == Role.ADMIN) {
      throw new UserManagementException(
          "Un compte administrateur ne peut pas être désactivé depuis l'interface");
    }

    user.setActive(active);
    userRepository.save(user);

    // Le JWT d'un compte désactivé est déjà refusé ; on supprime aussi sa session longue
    // pour qu'une réactivation impose une nouvelle connexion.
    if (!active) {
      refreshTokenService.revokeAllFor(user);
    }

    return toResponse(user);
  }

  private static UserResponse toResponse(User user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.isActive());
  }
}
