package com.gml.shield.repository;

import com.gml.shield.model.Mission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux missions enregistrées en base.
 */
public interface MissionRepository extends JpaRepository<Mission, Long> {

    // Filtrer directement en base évite de charger les missions des autres
    // chauffeurs en mémoire avant de décider de les masquer côté Java.
    @Query("SELECT m FROM Mission m WHERE m.driver.email = :email ORDER BY m.createdAt DESC")
    List<Mission> findMissionsForDriver(@Param("email") String email);

    @Query("SELECT m FROM Mission m WHERE m.dispatcher.email = :email ORDER BY m.createdAt DESC")
    List<Mission> findMissionsForDispatcher(@Param("email") String email);

    // L'identifiant seul ne suffit pas : l'email fait partie de la requête pour empêcher
    // l'accès à une mission d'un autre utilisateur par simple changement d'URL.
    @Query("SELECT m FROM Mission m WHERE m.id = :id AND m.driver.email = :email")
    Optional<Mission> findMissionForDriver(@Param("id") Long id, @Param("email") String email);

    // Le même contrôle de propriété s'applique aux répartiteurs : connaître
    // un identifiant de mission ne donne pas le droit de la modifier.
    @Query("SELECT m FROM Mission m WHERE m.id = :id AND m.dispatcher.email = :email")
    Optional<Mission> findMissionForDispatcher(@Param("id") Long id, @Param("email") String email);
}
