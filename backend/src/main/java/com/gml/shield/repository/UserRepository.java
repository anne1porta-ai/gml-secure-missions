package com.gml.shield.repository;

import com.gml.shield.enums.Role;
import com.gml.shield.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux utilisateurs enregistrés en base.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    // Utilisé au démarrage pour savoir s'il faut créer le compte administrateur initial.
    boolean existsByRole(Role role);

    // Chauffeurs proposés au dispatcher : actifs uniquement, triés par e-mail.
    List<User> findByRoleAndActiveTrueOrderByEmailAsc(Role role);
}
