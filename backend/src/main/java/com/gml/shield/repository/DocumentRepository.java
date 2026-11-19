package com.gml.shield.repository;

import com.gml.shield.model.Document;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Accès aux documents enregistrés en base. */
public interface DocumentRepository extends JpaRepository<Document, Long> {

  boolean existsByMission_Id(Long missionId);

  @Query("SELECT d FROM Document d WHERE d.mission.id = :missionId ORDER BY d.uploadedAt DESC")
  List<Document> findDocumentsForMission(@Param("missionId") Long missionId);

  // Le contrôle d'appartenance est fait dans la requête pour qu'un identifiant de
  // document deviné ne permette jamais de télécharger le fichier d'un autre dispatcher.
  @Query(
      "SELECT d FROM Document d WHERE d.id = :id AND d.mission.id = :missionId "
          + "AND d.mission.dispatcher.email = :email")
  Optional<Document> findDocumentForDispatcher(
      @Param("id") Long id, @Param("missionId") Long missionId, @Param("email") String email);
}
