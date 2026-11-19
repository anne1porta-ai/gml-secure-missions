package com.gml.shield;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gml.shield.dto.CreateMissionRequest;
import com.gml.shield.dto.ReassignMissionRequest;
import com.gml.shield.dto.UpdateMissionRequest;
import com.gml.shield.enums.MissionStatus;
import com.gml.shield.enums.Role;
import com.gml.shield.model.User;
import com.gml.shield.repository.MissionRepository;
import com.gml.shield.repository.UserRepository;
import com.gml.shield.service.MissionService;
import com.gml.shield.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Une notification ne part que lorsque l'enregistrement en base est confirmé. */
@SpringBootTest
@ActiveProfiles("test")
class MissionTransactionIntegrationTest extends PostgreSqlIntegrationTestBase {

  @Autowired private MissionService missionService;
  @Autowired private MissionRepository missionRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private NotificationService notificationService;

  private User driver;
  private User dispatcher;

  @BeforeEach
  void setUp() {
    reset(notificationService);
    driver = ensureUser("transaction-driver@gml.local", Role.DRIVER);
    dispatcher = ensureUser("transaction-dispatcher@gml.local", Role.DISPATCHER);
  }

  @Test
  void rolledBackMissionDoesNotSendSuccessNotification() {
    long missionsBefore = missionRepository.count();
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    transaction.executeWithoutResult(
        status -> {
          missionService.createMission(request("Rollback mission"), dispatcher);
          status.setRollbackOnly();
        });

    assertThat(missionRepository.count()).isEqualTo(missionsBefore);
    verifyNoInteractions(notificationService);
  }

  @Test
  void committedMissionSendsNotificationAfterCommit() {
    long missionsBefore = missionRepository.count();
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    transaction.executeWithoutResult(
        status -> missionService.createMission(request("Committed mission"), dispatcher));

    assertThat(missionRepository.count()).isEqualTo(missionsBefore + 1);
    verify(notificationService)
        .sendNotificationToUser(eq(driver.getEmail()), eq("NEW_MISSION"), any());
  }

  @Test
  void cancelledMissionNotifiesDriverAfterCommit() {
    Long missionId = missionService.createMission(request("Cancel mission"), dispatcher).id();
    reset(notificationService);

    missionService.cancelMission(missionId, dispatcher);

    assertThat(missionRepository.findById(missionId).orElseThrow().getStatus())
        .isEqualTo(MissionStatus.CANCELLED);
    verify(notificationService)
        .sendNotificationToUser(eq(driver.getEmail()), eq("MISSION_CANCELLED"), any());
  }

  @Test
  void rolledBackCancellationDoesNotNotifyDriver() {
    Long missionId = missionService.createMission(request("Cancel rollback"), dispatcher).id();
    reset(notificationService);
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    transaction.executeWithoutResult(
        status -> {
          missionService.cancelMission(missionId, dispatcher);
          status.setRollbackOnly();
        });

    assertThat(missionRepository.findById(missionId).orElseThrow().getStatus())
        .isEqualTo(MissionStatus.PENDING);
    verifyNoInteractions(notificationService);
  }

  @Test
  void modifiedMissionNotifiesCurrentDriver() {
    Long missionId = missionService.createMission(request("Update mission"), dispatcher).id();
    reset(notificationService);

    missionService.updateMission(
        missionId, new UpdateMissionRequest("Nouveau titre", "Lyon", null, null), dispatcher);

    verify(notificationService)
        .sendNotificationToUser(eq(driver.getEmail()), eq("MISSION_UPDATED"), any());
  }

  @Test
  void reassignmentNotifiesOldAndNewDrivers() {
    Long missionId = missionService.createMission(request("Transfer mission"), dispatcher).id();
    User newDriver = ensureUser("transaction-new-driver@gml.local", Role.DRIVER);
    reset(notificationService);

    missionService.reassignMission(
        missionId, new ReassignMissionRequest(newDriver.getEmail()), dispatcher);

    verify(notificationService)
        .sendNotificationToUser(eq(driver.getEmail()), eq("MISSION_REASSIGNED"), any());
    verify(notificationService)
        .sendNotificationToUser(eq(newDriver.getEmail()), eq("NEW_MISSION"), any());
  }

  private User ensureUser(String email, Role role) {
    return userRepository
        .findByEmail(email)
        .orElseGet(
            () -> {
              User user = new User(email, passwordEncoder.encode("test-password"), role);
              return userRepository.save(user);
            });
  }

  private CreateMissionRequest request(String title) {
    return new CreateMissionRequest(
        title, "Paris", "warehouse-secret", driver.getEmail(), null, null);
  }
}
