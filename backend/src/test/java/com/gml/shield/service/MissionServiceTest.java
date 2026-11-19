package com.gml.shield.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-18T12:00:00Z");

  @Mock private MissionRepository missionRepository;

  @Mock private DocumentRepository documentRepository;

  @Mock private UserRepository userRepository;

  @Mock private ApplicationEventPublisher eventPublisher;

  @Mock private SecretEncryptionService secretEncryptionService;

  @Mock private MissionMapper missionMapper;

  private MissionService missionService;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    missionService =
        new MissionService(
            missionRepository,
            documentRepository,
            userRepository,
            eventPublisher,
            secretEncryptionService,
            missionMapper,
            clock);
  }

  @Test
  void dispatcherCreatesMissionForActiveDriverAndPublishesEvent() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    CreateMissionRequest request =
        new CreateMissionRequest("Livraison", "Paris", "secret", driver.getEmail(), null, null);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);
    MissionResponse expectedResponse = response(MissionStatus.PENDING);

    when(userRepository.findByEmail(driver.getEmail())).thenReturn(Optional.of(driver));
    when(secretEncryptionService.encrypt("secret")).thenReturn("encrypted");
    when(missionMapper.toEntity(request, "encrypted", driver, dispatcher)).thenReturn(mission);
    when(missionRepository.save(mission)).thenReturn(mission);
    when(missionMapper.toResponse(mission)).thenReturn(expectedResponse);

    MissionResponse result = missionService.createMission(request, dispatcher);

    assertThat(result).isEqualTo(expectedResponse);
    verify(secretEncryptionService).encrypt("secret");
    verify(missionRepository).save(mission);

    ArgumentCaptor<MissionCreatedEvent> eventCaptor =
        ArgumentCaptor.forClass(MissionCreatedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().driverEmail()).isEqualTo(driver.getEmail());
    assertThat(eventCaptor.getValue().occurredAt()).isEqualTo(NOW);
  }

  @Test
  void dispatcherCanEditOwnPendingMission() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);
    UpdateMissionRequest request =
        new UpdateMissionRequest("Nouveau titre", "Lyon", "Quai 2", null);

    when(missionRepository.findMissionForDispatcher(7L, dispatcher.getEmail()))
        .thenReturn(Optional.of(mission));
    when(missionRepository.save(mission)).thenReturn(mission);

    missionService.updateMission(7L, request, dispatcher);

    assertThat(mission.getTitle()).isEqualTo("Nouveau titre");
    assertThat(mission.getDestination()).isEqualTo("Lyon");
    assertThat(mission.getDescription()).isEqualTo("Quai 2");
    verify(secretEncryptionService, never()).decrypt(any());
    verify(missionRepository).save(mission);
    verify(eventPublisher)
        .publishEvent(
            new MissionChangedEvent("MISSION_UPDATED", driver.getEmail(), null, "Nouveau titre"));
  }

  @Test
  void dispatcherCannotEditStartedMission() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);
    mission.setStatus(MissionStatus.IN_PROGRESS);

    when(missionRepository.findMissionForDispatcher(7L, dispatcher.getEmail()))
        .thenReturn(Optional.of(mission));

    assertThatThrownBy(
            () ->
                missionService.updateMission(
                    7L, new UpdateMissionRequest("Autre", "Lyon", null, null), dispatcher))
        .isInstanceOf(MissionChangeNotAllowedException.class);

    assertThat(mission.getTitle()).isEqualTo("Livraison");
    verify(missionRepository, never()).save(any(Mission.class));
  }

  @Test
  void anotherDispatcherCannotEditMission() {
    User dispatcher = user("other@gml.local", Role.DISPATCHER);
    when(missionRepository.findMissionForDispatcher(7L, dispatcher.getEmail()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                missionService.updateMission(
                    7L, new UpdateMissionRequest("Autre", "Lyon", null, null), dispatcher))
        .isInstanceOf(MissionNotFoundException.class);
  }

  @Test
  void dispatcherCanReassignPendingMissionToActiveDriver() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User newDriver = user("new-driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);

    when(missionRepository.findMissionForDispatcher(7L, dispatcher.getEmail()))
        .thenReturn(Optional.of(mission));
    when(userRepository.findByEmail(newDriver.getEmail())).thenReturn(Optional.of(newDriver));
    when(missionRepository.save(mission)).thenReturn(mission);

    missionService.reassignMission(
        7L, new ReassignMissionRequest(newDriver.getEmail()), dispatcher);

    assertThat(mission.getDriver().getEmail()).isEqualTo(newDriver.getEmail());
    verify(missionRepository).save(mission);
    verify(eventPublisher).publishEvent(any(MissionCreatedEvent.class));
    verify(eventPublisher)
        .publishEvent(
            new MissionChangedEvent("MISSION_REASSIGNED", driver.getEmail(), null, "Livraison"));
  }

  @Test
  void dispatcherCannotReassignStartedMission() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);
    mission.setStatus(MissionStatus.IN_PROGRESS);

    when(missionRepository.findMissionForDispatcher(7L, dispatcher.getEmail()))
        .thenReturn(Optional.of(mission));

    assertThatThrownBy(
            () ->
                missionService.reassignMission(
                    7L, new ReassignMissionRequest("other@gml.local"), dispatcher))
        .isInstanceOf(MissionChangeNotAllowedException.class);

    verify(missionRepository, never()).save(any(Mission.class));
  }

  @Test
  void dispatcherCannotReassignToInactiveDriver() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User inactive = user("inactive@gml.local", Role.DRIVER);
    inactive.setActive(false);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);

    when(missionRepository.findMissionForDispatcher(7L, dispatcher.getEmail()))
        .thenReturn(Optional.of(mission));
    when(userRepository.findByEmail(inactive.getEmail())).thenReturn(Optional.of(inactive));

    assertThatThrownBy(
            () ->
                missionService.reassignMission(
                    7L, new ReassignMissionRequest(inactive.getEmail()), dispatcher))
        .isInstanceOf(MissionBadRequestException.class);

    verify(missionRepository, never()).save(any(Mission.class));
  }

  @Test
  void dispatcherCanCancelPendingMission() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);

    when(missionRepository.findMissionForDispatcher(7L, dispatcher.getEmail()))
        .thenReturn(Optional.of(mission));
    when(missionRepository.save(mission)).thenReturn(mission);

    missionService.cancelMission(7L, dispatcher);

    assertThat(mission.getStatus()).isEqualTo(MissionStatus.CANCELLED);
    verify(missionRepository).save(mission);
    verify(eventPublisher)
        .publishEvent(
            new MissionChangedEvent("MISSION_CANCELLED", driver.getEmail(), null, "Livraison"));
  }

  @Test
  void adminCanCancelStartedMission() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    User admin = user("admin@gml.local", Role.ADMIN);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);
    mission.setStatus(MissionStatus.IN_PROGRESS);

    when(missionRepository.findById(7L)).thenReturn(Optional.of(mission));
    when(missionRepository.save(mission)).thenReturn(mission);

    missionService.cancelMission(7L, admin);

    assertThat(mission.getStatus()).isEqualTo(MissionStatus.CANCELLED);
    verify(missionRepository).save(mission);
    verify(eventPublisher)
        .publishEvent(
            new MissionChangedEvent("MISSION_CANCELLED", driver.getEmail(), null, "Livraison"));
  }

  @Test
  void completedMissionCannotBeCancelled() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);
    mission.setStatus(MissionStatus.COMPLETED);

    when(missionRepository.findMissionForDispatcher(7L, dispatcher.getEmail()))
        .thenReturn(Optional.of(mission));

    assertThatThrownBy(() -> missionService.cancelMission(7L, dispatcher))
        .isInstanceOf(MissionChangeNotAllowedException.class);
    verify(missionRepository, never()).save(any(Mission.class));
  }

  @Test
  void driverCannotCancelMission() {
    User driver = user("driver@gml.local", Role.DRIVER);
    assertThatThrownBy(() -> missionService.cancelMission(7L, driver))
        .isInstanceOf(MissionNotFoundException.class);
    verify(missionRepository, never()).save(any(Mission.class));
  }

  @Test
  void driverCanMoveMissionFromPendingToInProgress() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);
    MissionResponse expectedResponse = response(MissionStatus.IN_PROGRESS);

    when(missionRepository.findMissionForDriver(1L, driver.getEmail()))
        .thenReturn(Optional.of(mission));
    when(missionMapper.toResponse(mission)).thenReturn(expectedResponse);

    MissionResponse result = missionService.updateStatus(1L, MissionStatus.IN_PROGRESS, driver);

    assertThat(mission.getStatus()).isEqualTo(MissionStatus.IN_PROGRESS);
    assertThat(result).isEqualTo(expectedResponse);
    verify(missionRepository).save(mission);
    verify(secretEncryptionService, never()).decrypt(any());
  }

  @Test
  void driverCanMoveMissionFromInProgressToCompleted() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);
    mission.setStatus(MissionStatus.IN_PROGRESS);
    MissionResponse expectedResponse = response(MissionStatus.COMPLETED);

    when(missionRepository.findMissionForDriver(1L, driver.getEmail()))
        .thenReturn(Optional.of(mission));
    when(documentRepository.existsByMission_Id(1L)).thenReturn(true);
    when(missionMapper.toResponse(mission)).thenReturn(expectedResponse);

    MissionResponse result = missionService.updateStatus(1L, MissionStatus.COMPLETED, driver);

    assertThat(mission.getStatus()).isEqualTo(MissionStatus.COMPLETED);
    assertThat(result).isEqualTo(expectedResponse);
    verify(missionRepository).save(mission);
  }

  @Test
  void driverCannotCompleteMissionWithoutProof() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);
    mission.setStatus(MissionStatus.IN_PROGRESS);

    when(missionRepository.findMissionForDriver(1L, driver.getEmail()))
        .thenReturn(Optional.of(mission));

    assertThatThrownBy(() -> missionService.updateStatus(1L, MissionStatus.COMPLETED, driver))
        .isInstanceOf(DeliveryProofRequiredException.class)
        .hasMessage("Une preuve de livraison est requise pour clôturer la mission");

    assertThat(mission.getStatus()).isEqualTo(MissionStatus.IN_PROGRESS);
    verify(missionRepository, never()).save(mission);
  }

  @Test
  void driverCannotSkipDirectlyFromPendingToCompleted() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);

    when(missionRepository.findMissionForDriver(1L, driver.getEmail()))
        .thenReturn(Optional.of(mission));

    assertThatThrownBy(() -> missionService.updateStatus(1L, MissionStatus.COMPLETED, driver))
        .isInstanceOf(MissionBadRequestException.class)
        .hasMessageContaining("Transition invalide");

    verify(missionRepository, never()).save(mission);
  }

  @Test
  void completedMissionCannotMoveBackToInProgress() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);
    mission.setStatus(MissionStatus.COMPLETED);

    when(missionRepository.findMissionForDriver(1L, driver.getEmail()))
        .thenReturn(Optional.of(mission));

    assertThatThrownBy(() -> missionService.updateStatus(1L, MissionStatus.IN_PROGRESS, driver))
        .isInstanceOf(MissionBadRequestException.class)
        .hasMessageContaining("Transition invalide");

    verify(missionRepository, never()).save(mission);
  }

  @Test
  void driverGetsOnlyMissionAssignedToHim() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);

    when(missionRepository.findMissionForDriver(7L, driver.getEmail()))
        .thenReturn(Optional.of(mission));

    Mission visibleMission = missionService.getVisibleMission(7L, driver);

    assertThat(visibleMission).isSameAs(mission);
    verify(missionRepository).findMissionForDriver(7L, driver.getEmail());
    verify(missionRepository, never()).findMissionForDispatcher(7L, driver.getEmail());
  }

  @Test
  void dispatcherGetsOnlyMissionCreatedByHim() {
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    User driver = user("driver@gml.local", Role.DRIVER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);

    when(missionRepository.findMissionForDispatcher(7L, dispatcher.getEmail()))
        .thenReturn(Optional.of(mission));

    Mission visibleMission = missionService.getVisibleMission(7L, dispatcher);

    assertThat(visibleMission).isSameAs(mission);
    verify(missionRepository).findMissionForDispatcher(7L, dispatcher.getEmail());
    verify(missionRepository, never()).findMissionForDriver(7L, dispatcher.getEmail());
  }

  @Test
  void adminCannotUseMissionVisibilityMethod() {
    User admin = user("admin@gml.local", Role.ADMIN);

    assertThatThrownBy(() -> missionService.getVisibleMission(7L, admin))
        .isInstanceOf(MissionNotFoundException.class)
        .hasMessage("Mission introuvable");

    verify(missionRepository, never()).findMissionForDriver(anyLong(), anyString());
    verify(missionRepository, never()).findMissionForDispatcher(anyLong(), anyString());
  }

  @Test
  void driverListNeverDecryptsSecret() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);
    mission.setStatus(MissionStatus.IN_PROGRESS);
    MissionResponse expected = response(MissionStatus.IN_PROGRESS);

    when(missionRepository.findMissionsForDriver(driver.getEmail())).thenReturn(List.of(mission));
    when(missionMapper.toResponse(mission)).thenReturn(expected);

    assertThat(missionService.listMissions(driver)).containsExactly(expected);
    verify(secretEncryptionService, never()).decrypt(any());
  }

  @Test
  void dispatcherListNeverDecryptsSecret() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);
    MissionResponse expected = response(MissionStatus.PENDING);

    when(missionRepository.findMissionsForDispatcher(dispatcher.getEmail()))
        .thenReturn(List.of(mission));
    when(missionMapper.toResponse(mission)).thenReturn(expected);

    assertThat(missionService.listMissions(dispatcher)).containsExactly(expected);
    verify(secretEncryptionService, never()).decrypt(any());
  }

  @Test
  void assignedDriverGetsSecretWhileMissionInProgress() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);
    mission.setStatus(MissionStatus.IN_PROGRESS);

    when(missionRepository.findMissionForDriver(1L, driver.getEmail()))
        .thenReturn(Optional.of(mission));
    when(secretEncryptionService.decrypt("encrypted")).thenReturn("secret");

    SecretCodeResponse result = missionService.revealSecretCode(1L, driver);

    assertThat(result.secretCode()).isEqualTo("secret");
  }

  @Test
  void secretIsNotRevealedBeforeMissionStarts() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);

    when(missionRepository.findMissionForDriver(1L, driver.getEmail()))
        .thenReturn(Optional.of(mission));

    assertThatThrownBy(() -> missionService.revealSecretCode(1L, driver))
        .isInstanceOf(SecretCodeUnavailableException.class);

    verify(secretEncryptionService, never()).decrypt(any());
  }

  @Test
  void secretIsNotRevealedAfterDelivery() {
    User driver = user("driver@gml.local", Role.DRIVER);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    Mission mission = new Mission("Livraison", "Paris", "encrypted", driver, dispatcher);
    mission.setStatus(MissionStatus.COMPLETED);

    when(missionRepository.findMissionForDriver(1L, driver.getEmail()))
        .thenReturn(Optional.of(mission));

    assertThatThrownBy(() -> missionService.revealSecretCode(1L, driver))
        .isInstanceOf(SecretCodeUnavailableException.class);

    verify(secretEncryptionService, never()).decrypt(any());
  }

  @Test
  void otherDriverCannotRevealSecret() {
    User otherDriver = user("other-driver@gml.local", Role.DRIVER);

    when(missionRepository.findMissionForDriver(1L, otherDriver.getEmail()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> missionService.revealSecretCode(1L, otherDriver))
        .isInstanceOf(MissionNotFoundException.class)
        .hasMessage("Mission introuvable");

    verify(secretEncryptionService, never()).decrypt(any());
  }

  @Test
  void assignableDriversAreTheActiveDriversOnly() {
    User driver = user("driver@gml.local", Role.DRIVER);

    when(userRepository.findByRoleAndActiveTrueOrderByEmailAsc(Role.DRIVER))
        .thenReturn(List.of(driver));

    assertThat(missionService.listAssignableDrivers())
        .containsExactly(new DriverResponse("driver@gml.local"));
  }

  @Test
  void inactiveDriverCannotReceiveNewMission() {
    User inactiveDriver = user("driver@gml.local", Role.DRIVER);
    inactiveDriver.setActive(false);
    User dispatcher = user("dispatcher@gml.local", Role.DISPATCHER);
    CreateMissionRequest request =
        new CreateMissionRequest(
            "Livraison", "Paris", "secret", inactiveDriver.getEmail(), null, null);

    when(userRepository.findByEmail(inactiveDriver.getEmail()))
        .thenReturn(Optional.of(inactiveDriver));

    assertThatThrownBy(() -> missionService.createMission(request, dispatcher))
        .isInstanceOf(MissionBadRequestException.class)
        .hasMessage("Chauffeur invalide ou inactif");

    verify(missionRepository, never()).save(any(Mission.class));
  }

  private User user(String email, Role role) {
    return new User(email, "hashed-password", role);
  }

  private MissionResponse response(MissionStatus status) {
    return new MissionResponse(
        1L,
        "Livraison",
        "Paris",
        null,
        null,
        status,
        "driver@gml.local",
        "dispatcher@gml.local",
        null);
  }
}
