package com.gml.shield.service;

import com.gml.shield.dto.CreateMissionRequest;
import com.gml.shield.dto.DriverResponse;
import com.gml.shield.dto.MissionResponse;
import com.gml.shield.dto.ReassignMissionRequest;
import com.gml.shield.dto.SecretCodeResponse;
import com.gml.shield.dto.UpdateMissionRequest;
import com.gml.shield.enums.MissionStatus;
import com.gml.shield.enums.Role;
import com.gml.shield.event.MissionChangedEvent;
import com.gml.shield.event.MissionCreatedEvent;
import com.gml.shield.exception.DeliveryProofRequiredException;
import com.gml.shield.exception.MissionBadRequestException;
import com.gml.shield.exception.MissionChangeNotAllowedException;
import com.gml.shield.exception.MissionNotFoundException;
import com.gml.shield.exception.SecretCodeUnavailableException;
import com.gml.shield.mapper.MissionMapper;
import com.gml.shield.model.Mission;
import com.gml.shield.model.User;
import com.gml.shield.repository.DocumentRepository;
import com.gml.shield.repository.MissionRepository;
import com.gml.shield.repository.UserRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestion des missions. */
@Service
public class MissionService {

  private final MissionRepository missionRepository;
  private final DocumentRepository documentRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final SecretEncryptionService secretEncryptionService;
  private final MissionMapper missionMapper;
  private final Clock clock;

  public MissionService(
      MissionRepository missionRepository,
      DocumentRepository documentRepository,
      UserRepository userRepository,
      ApplicationEventPublisher eventPublisher,
      SecretEncryptionService secretEncryptionService,
      MissionMapper missionMapper,
      Clock clock) {
    this.missionRepository = missionRepository;
    this.documentRepository = documentRepository;
    this.userRepository = userRepository;
    this.eventPublisher = eventPublisher;
    this.secretEncryptionService = secretEncryptionService;
    this.missionMapper = missionMapper;
    this.clock = clock;
  }

  @Transactional
  public MissionResponse createMission(CreateMissionRequest request, User dispatcher) {
    User driver = findActiveDriver(request.driverEmail());

    // Le code de mission est chiffré avant persistance pour qu'une lecture directe
    // de la base ne révèle pas cette information sensible.
    String encryptedSecretCode = secretEncryptionService.encrypt(request.secretCode());

    Mission mission = missionMapper.toEntity(request, encryptedSecretCode, driver, dispatcher);

    Mission savedMission = missionRepository.save(mission);

    // On publie un événement plutôt que d'envoyer le SSE ici : le listener attendra
    // le commit de la transaction avant de prévenir le chauffeur.
    eventPublisher.publishEvent(buildMissionCreatedEvent(savedMission));

    return missionMapper.toResponse(savedMission);
  }

  @Transactional
  public MissionResponse updateMission(
      Long missionId, UpdateMissionRequest request, User dispatcher) {
    Mission mission = findDispatcherMission(missionId, dispatcher.getEmail());

    // Une mission démarrée ne doit plus recevoir de nouvelles instructions
    // par une simple édition du répartiteur pendant son exécution.
    if (mission.getStatus() != MissionStatus.PENDING) {
      throw new MissionChangeNotAllowedException("Seule une mission en attente peut être modifiée");
    }

    mission.setTitle(request.title().trim());
    mission.setDestination(request.destination().trim());
    String description = request.description();
    if (description != null) {
      description = description.trim();
      if (description.isEmpty()) {
        description = null;
      }
    }
    mission.setDescription(description);
    mission.setScheduledAt(request.scheduledAt());

    Mission savedMission = missionRepository.save(mission);
    notifyDriver(savedMission, "MISSION_UPDATED", savedMission.getDriver().getEmail());

    return missionMapper.toResponse(savedMission);
  }

  @Transactional
  public MissionResponse reassignMission(
      Long missionId, ReassignMissionRequest request, User dispatcher) {
    Mission mission = findDispatcherMission(missionId, dispatcher.getEmail());

    if (mission.getStatus() != MissionStatus.PENDING) {
      throw new MissionChangeNotAllowedException(
          "Le chauffeur ne peut être changé que pour une mission en attente");
    }

    User newDriver = findActiveDriver(request.driverEmail());
    // Réaffecter au même chauffeur ne change rien : on évite un enregistrement
    // et des notifications inutiles pour une opération sans effet.
    if (mission.getDriver().getEmail().equals(newDriver.getEmail())) {
      return missionMapper.toResponse(mission);
    }

    // L'ancien chauffeur doit retirer cette mission de son écran.
    String previousDriverEmail = mission.getDriver().getEmail();
    mission.setDriver(newDriver);
    Mission savedMission = missionRepository.save(mission);

    notifyDriver(savedMission, "MISSION_REASSIGNED", previousDriverEmail);

    // Le nouveau chauffeur reçoit la notification habituelle de nouvelle mission.
    eventPublisher.publishEvent(buildMissionCreatedEvent(savedMission));
    return missionMapper.toResponse(savedMission);
  }

  @Transactional
  public MissionResponse cancelMission(Long missionId, User user) {
    Mission mission;

    if (user.getRole() == Role.ADMIN) {
      mission = missionRepository.findById(missionId).orElse(null);
      if (mission == null) {
        throw missionNotFound();
      }
    } else if (user.getRole() == Role.DISPATCHER) {
      mission = findDispatcherMission(missionId, user.getEmail());
    } else {
      throw missionNotFound();
    }

    if (mission.getStatus() != MissionStatus.PENDING
        && mission.getStatus() != MissionStatus.IN_PROGRESS) {
      throw new MissionChangeNotAllowedException(
          "Une mission terminée ou annulée ne peut pas être annulée");
    }

    mission.setStatus(MissionStatus.CANCELLED);
    Mission savedMission = missionRepository.save(mission);
    notifyDriver(savedMission, "MISSION_CANCELLED", savedMission.getDriver().getEmail());

    return missionMapper.toResponse(savedMission);
  }

  @Transactional(readOnly = true)
  public List<MissionResponse> listMissions(User user) {
    // Les listes ne contiennent jamais le code d'accès, quel que soit le rôle : il n'est
    // déchiffré qu'à la demande explicite du chauffeur (voir revealSecretCode).
    List<Mission> missions = findVisibleMissions(user);
    List<MissionResponse> results = new ArrayList<>();

    for (Mission mission : missions) {
      MissionResponse response = missionMapper.toResponse(mission);
      results.add(response);
    }

    return results;
  }

  /**
   * Change le statut d'une mission du chauffeur connecté.
   *
   * <p>Le cycle de vie (RG3) et l'obligation de preuve sont vérifiés ici et non dans React, car
   * l'API peut être appelée directement sans passer par l'interface.
   */
  @Transactional
  public MissionResponse updateStatus(Long missionId, MissionStatus requestedStatus, User driver) {
    // Le changement de statut est une action chauffeur : le service vérifie lui-même
    // que la mission appartient au chauffeur, même si le contrôleur est déjà protégé par rôle.
    Mission mission = getDriverMission(missionId, driver);

    validateStatusTransition(mission.getStatus(), requestedStatus);

    // Même si React masque la clôture sans justificatif, l'API doit imposer
    // cette preuve en base : une requête HTTP directe contournerait le bouton.
    if (requestedStatus == MissionStatus.COMPLETED
        && !documentRepository.existsByMission_Id(missionId)) {
      throw new DeliveryProofRequiredException(
          "Une preuve de livraison est requise pour clôturer la mission");
    }

    mission.setStatus(requestedStatus);
    missionRepository.save(mission);

    return missionMapper.toResponse(mission);
  }

  /**
   * Révèle le code d'accès au chauffeur assigné (RG2).
   *
   * <p>Une mission d'un autre chauffeur reste introuvable, sans révéler son existence. Le code
   * n'est utile qu'une fois la mission démarrée et ne l'est plus après la livraison : il n'est
   * restitué que pendant IN_PROGRESS (moindre privilège dans le temps).
   */
  @Transactional(readOnly = true)
  public SecretCodeResponse revealSecretCode(Long missionId, User driver) {
    Mission mission = getDriverMission(missionId, driver);

    if (mission.getStatus() != MissionStatus.IN_PROGRESS) {
      throw new SecretCodeUnavailableException(
          "Code d'accès disponible uniquement pendant la mission");
    }

    // Déchiffrer uniquement après la vérification du propriétaire et du statut.
    // Ni la liste des missions ni une notification SSE ne transportent ce secret.
    return new SecretCodeResponse(secretEncryptionService.decrypt(mission.getSecretCode()));
  }

  @Transactional(readOnly = true)
  public List<DriverResponse> listAssignableDrivers() {
    // Même règle qu'à la création (findActiveDriver) : seuls les chauffeurs actifs
    // peuvent recevoir une mission.
    List<User> drivers = userRepository.findByRoleAndActiveTrueOrderByEmailAsc(Role.DRIVER);
    List<DriverResponse> results = new ArrayList<>();

    for (User driver : drivers) {
      results.add(new DriverResponse(driver.getEmail()));
    }

    return results;
  }

  @Transactional(readOnly = true)
  public Mission getVisibleMission(Long missionId, User user) {
    if (user.getRole() == Role.DRIVER) {
      return getDriverMission(missionId, user);
    }

    if (user.getRole() == Role.DISPATCHER) {
      return findDispatcherMission(missionId, user.getEmail());
    }

    throw missionNotFound();
  }

  @Transactional(readOnly = true)
  public Mission getDriverMission(Long missionId, User driver) {
    Mission mission =
        missionRepository.findMissionForDriver(missionId, driver.getEmail()).orElse(null);
    if (mission == null) {
      throw missionNotFound();
    }
    return mission;
  }

  private Mission findDispatcherMission(Long missionId, String dispatcherEmail) {
    Mission mission =
        missionRepository.findMissionForDispatcher(missionId, dispatcherEmail).orElse(null);
    if (mission == null) {
      throw missionNotFound();
    }
    return mission;
  }

  private User findActiveDriver(String driverEmail) {
    User driver = userRepository.findByEmail(driverEmail).orElse(null);

    if (driver == null) {
      throw new MissionBadRequestException("Chauffeur invalide ou inactif");
    }
    if (driver.getRole() != Role.DRIVER || !driver.isActive()) {
      throw new MissionBadRequestException("Chauffeur invalide ou inactif");
    }

    return driver;
  }

  private List<Mission> findVisibleMissions(User user) {
    // Les requêtes filtrent directement par utilisateur connecté : on évite de charger
    // des missions non autorisées puis de compter uniquement sur un filtrage Java.
    if (user.getRole() == Role.DISPATCHER) {
      return missionRepository.findMissionsForDispatcher(user.getEmail());
    }

    if (user.getRole() == Role.DRIVER) {
      return missionRepository.findMissionsForDriver(user.getEmail());
    }

    return List.of();
  }

  private void notifyDriver(Mission mission, String type, String driverEmail) {
    // Le listener attend la fin de la transaction avant de prévenir le chauffeur.
    eventPublisher.publishEvent(
        new MissionChangedEvent(type, driverEmail, mission.getId(), mission.getTitle()));
  }

  private MissionCreatedEvent buildMissionCreatedEvent(Mission mission) {
    return new MissionCreatedEvent(
        mission.getDriver().getEmail(),
        mission.getId(),
        mission.getTitle(),
        mission.getDestination(),
        clock.instant());
  }

  private void validateStatusTransition(
      MissionStatus currentStatus, MissionStatus requestedStatus) {
    // Refus par défaut : seules les deux étapes prévues sont possibles ici.
    // L'annulation est volontairement gérée par une opération distincte.
    boolean transitionAllowed = false;

    if (currentStatus == MissionStatus.PENDING && requestedStatus == MissionStatus.IN_PROGRESS) {
      transitionAllowed = true;
    } else if (currentStatus == MissionStatus.IN_PROGRESS
        && requestedStatus == MissionStatus.COMPLETED) {
      transitionAllowed = true;
    }

    if (!transitionAllowed) {
      throw new MissionBadRequestException(
          "Transition invalide: " + currentStatus + " -> " + requestedStatus);
    }
  }

  private MissionNotFoundException missionNotFound() {
    return new MissionNotFoundException("Mission introuvable");
  }
}
