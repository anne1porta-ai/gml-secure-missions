package com.gml.shield;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gml.shield.enums.MissionStatus;
import com.gml.shield.enums.Role;
import com.gml.shield.model.Mission;
import com.gml.shield.model.User;
import com.gml.shield.repository.MissionRepository;
import com.gml.shield.repository.UserRepository;
import com.gml.shield.security.JwtAuthenticationFilter;
import com.gml.shield.security.JwtService;
import com.gml.shield.service.SecretEncryptionService;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/** Vérifie les principaux scénarios d'authentification, de session et de RBAC. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest extends PostgreSqlIntegrationTestBase {

  private static final String REFRESH_COOKIE = "gml_refresh_token";

  @Autowired private MockMvc mockMvc;

  @Autowired private JwtService jwtService;

  @Autowired private DataSource dataSource;

  @Autowired private Flyway flyway;

  @Autowired private UserRepository userRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private MissionRepository missionRepository;

  @Autowired private JsonMapper jsonMapper;

  @Autowired private SecretEncryptionService secretEncryptionService;

  @BeforeEach
  void ensureReferenceUsersExist() {
    ensureUser("dispatcher1@gml.local", "dispatch123", Role.DISPATCHER);
    ensureUser("driver1@gml.local", "driver123", Role.DRIVER);
    ensureUser("driver2@gml.local", "driver456", Role.DRIVER);
    ensureUser("admin1@gml.local", "admin-password-123", Role.ADMIN);
  }

  @Test
  void integrationSuiteUsesPostgresqlAndFlyway() throws Exception {
    try (var connection = dataSource.getConnection()) {
      assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
    }

    var currentMigration = flyway.info().current();
    assertNotNull(currentMigration);
    assertEquals("2", currentMigration.getVersion().getVersion());
  }

  @Test
  void loginCreatesHttpOnlyAccessAndRefreshCookies() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"email":"dispatcher1@gml.local","password":"dispatch123"}
                                """))
        .andExpect(status().isOk())
        .andExpect(cookie().httpOnly(JwtAuthenticationFilter.COOKIE_NAME, true))
        .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
        .andExpect(cookie().path(REFRESH_COOKIE, "/api/auth"));
  }

  @Test
  void loginWithoutCsrfTokenIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"email":"dispatcher1@gml.local","password":"dispatch123"}
                                """))
        .andExpect(status().isForbidden());
  }

  @Test
  void invalidPasswordIsRejectedWithoutTechnicalDetail() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"email":"dispatcher1@gml.local","password":"incorrect"}
                                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.error").value("Identifiants invalides"));
  }

  @Test
  void protectedRouteWithoutSessionIsRejected() throws Exception {
    mockMvc
        .perform(get("/api/missions"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void driverCannotCreateMissionReservedToDispatcher() throws Exception {
    String jwt = jwtService.generateToken("driver1@gml.local", "DRIVER");

    mockMvc
        .perform(
            post("/api/missions")
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "title":"Livraison",
                                  "destination":"Lyon",
                                  "secretCode":"ABC123",
                                  "driverEmail":"driver1@gml.local"
                                }
                                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void dispatcherCanCreateMission() throws Exception {
    String jwt = jwtService.generateToken("dispatcher1@gml.local", "DISPATCHER");

    mockMvc
        .perform(
            post("/api/missions")
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "title":"Livraison",
                                  "destination":"Lyon",
                                  "secretCode":"ABC123",
                                  "driverEmail":"driver1@gml.local"
                                }
                                """))
        .andExpect(status().isOk());
  }

  @Test
  void createdMissionStoresSecretEncryptedAndDoesNotExposeItToDispatcher() throws Exception {
    String jwt = jwtService.generateToken("dispatcher1@gml.local", "DISPATCHER");
    String testValue = "test-value";

    String requestBody =
        jsonMapper.writeValueAsString(
            Map.of(
                "title", "Secret storage check",
                "destination", "Lyon",
                "secretCode", testValue,
                "driverEmail", "driver1@gml.local"));

    MvcResult result =
        mockMvc
            .perform(
                post("/api/missions")
                    .with(csrf())
                    .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.secretCode").doesNotExist())
            .andReturn();

    long missionId =
        jsonMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

    String storedSecret = missionRepository.findById(missionId).orElseThrow().getSecretCode();

    org.assertj.core.api.Assertions.assertThat(storedSecret).isNotEqualTo(testValue).isNotBlank();
  }

  @Test
  void missionListNeverContainsSecretEvenForAssignedDriver() throws Exception {
    saveMissionForDriver1("list-check", MissionStatus.IN_PROGRESS);
    String jwt = jwtService.generateToken("driver1@gml.local", "DRIVER");

    mockMvc
        .perform(
            get("/api/missions").cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].secretCode").isEmpty());
  }

  @Test
  void assignedDriverRevealsSecretWhileMissionInProgress() throws Exception {
    Mission mission = saveMissionForDriver1("QX-2085", MissionStatus.IN_PROGRESS);
    String jwt = jwtService.generateToken("driver1@gml.local", "DRIVER");

    mockMvc
        .perform(
            get("/api/missions/{id}/secret-code", mission.getId())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", containsString("no-store")))
        .andExpect(jsonPath("$.secretCode").value("QX-2085"));
  }

  @Test
  void secretIsUnavailableBeforeMissionStarts() throws Exception {
    Mission mission = saveMissionForDriver1("not-yet", MissionStatus.PENDING);
    String jwt = jwtService.generateToken("driver1@gml.local", "DRIVER");

    mockMvc
        .perform(
            get("/api/missions/{id}/secret-code", mission.getId())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SECRET_CODE_UNAVAILABLE"));
  }

  @Test
  void dispatcherCannotRevealSecret() throws Exception {
    Mission mission = saveMissionForDriver1("forbidden", MissionStatus.IN_PROGRESS);
    String jwt = jwtService.generateToken("dispatcher1@gml.local", "DISPATCHER");

    mockMvc
        .perform(
            get("/api/missions/{id}/secret-code", mission.getId())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void otherDriverCannotRevealSecret() throws Exception {
    Mission mission = saveMissionForDriver1("not-yours", MissionStatus.IN_PROGRESS);
    String jwt = jwtService.generateToken("driver2@gml.local", "DRIVER");

    mockMvc
        .perform(
            get("/api/missions/{id}/secret-code", mission.getId())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MISSION_NOT_FOUND"));
  }

  @Test
  void dispatcherCannotListAccounts() throws Exception {
    String jwt = jwtService.generateToken("dispatcher1@gml.local", "DISPATCHER");

    mockMvc
        .perform(get("/api/users").cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void adminCreatesDriverWhoCanThenLogIn() throws Exception {
    String jwt = jwtService.generateToken("admin1@gml.local", "ADMIN");

    mockMvc
        .perform(
            post("/api/users")
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email":"new-driver@gml.local",
                                  "temporaryPassword":"provisoire-provisoire",
                                  "role":"DRIVER"
                                }
                                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("DRIVER"))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.temporaryPassword").doesNotExist());

    mockMvc
        .perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"email":"new-driver@gml.local","password":"provisoire-provisoire"}
                                """))
        .andExpect(status().isOk());
  }

  @Test
  void adminCannotCreateAnotherAdministrator() throws Exception {
    String jwt = jwtService.generateToken("admin1@gml.local", "ADMIN");

    mockMvc
        .perform(
            post("/api/users")
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email":"second-admin@gml.local",
                                  "temporaryPassword":"provisoire-provisoire",
                                  "role":"ADMIN"
                                }
                                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("USER_MANAGEMENT_REJECTED"));
  }

  @Test
  void deactivatedDriverCanNoLongerLogIn() throws Exception {
    ensureUser("driver3@gml.local", "driver-password-3", Role.DRIVER);
    Long driverId = userRepository.findByEmail("driver3@gml.local").orElseThrow().getId();
    String jwt = jwtService.generateToken("admin1@gml.local", "ADMIN");

    mockMvc
        .perform(
            patch("/api/users/{id}/active", driverId)
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"active":false}
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    mockMvc
        .perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"email":"driver3@gml.local","password":"driver-password-3"}
                                """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void dispatcherCreatesMissionWithOptionalDetailsAndLongDestination() throws Exception {
    String jwt = jwtService.generateToken("dispatcher1@gml.local", "DISPATCHER");
    String longDestination = "Entrepôt ".repeat(40);

    String requestBody =
        jsonMapper.writeValueAsString(
            Map.of(
                "title", "Mission détaillée",
                "destination", longDestination,
                "secretCode", "DETAIL-1",
                "driverEmail", "driver1@gml.local",
                "description", "Quai 4, sonner à l'accueil",
                "scheduledAt", "2026-11-02T08:30"));

    mockMvc
        .perform(
            post("/api/missions")
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Quai 4, sonner à l'accueil"))
        .andExpect(jsonPath("$.scheduledAt").value(containsString("2026-11-02T08:30")))
        .andExpect(jsonPath("$.destination").value(longDestination));
  }

  @Test
  void owningDispatcherCanEditPendingMission() throws Exception {
    Mission mission = saveMissionForDriver1("secret-edit", MissionStatus.PENDING);
    String jwt = jwtService.generateToken("dispatcher1@gml.local", "DISPATCHER");

    mockMvc
        .perform(
            patch("/api/missions/{id}", mission.getId())
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"Livraison corrigée","destination":"Nantes","description":"Quai 3"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Livraison corrigée"))
        .andExpect(jsonPath("$.destination").value("Nantes"))
        .andExpect(jsonPath("$.secretCode").doesNotExist());

    Mission updated = missionRepository.findById(mission.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(updated.getTitle()).isEqualTo("Livraison corrigée");
  }

  @Test
  void dispatcherCannotEditStartedMission() throws Exception {
    Mission mission = saveMissionForDriver1("secret-edit", MissionStatus.IN_PROGRESS);
    String jwt = jwtService.generateToken("dispatcher1@gml.local", "DISPATCHER");

    mockMvc
        .perform(
            patch("/api/missions/{id}", mission.getId())
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"Interdit","destination":"Nantes"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MISSION_CHANGE_NOT_ALLOWED"));
  }

  @Test
  void driverCannotEditMission() throws Exception {
    Mission mission = saveMissionForDriver1("secret-edit", MissionStatus.PENDING);
    String jwt = jwtService.generateToken("driver1@gml.local", "DRIVER");

    mockMvc
        .perform(
            patch("/api/missions/{id}", mission.getId())
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"Interdit","destination":"Nantes"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void owningDispatcherCanReassignPendingMission() throws Exception {
    Mission mission = saveMissionForDriver1("secret-assign", MissionStatus.PENDING);
    String jwt = jwtService.generateToken("dispatcher1@gml.local", "DISPATCHER");

    mockMvc
        .perform(
            patch("/api/missions/{id}/driver", mission.getId())
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"driverEmail":"driver2@gml.local"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.driverEmail").value("driver2@gml.local"));

    org.assertj.core.api.Assertions.assertThat(
            missionRepository.findMissionForDriver(mission.getId(), "driver2@gml.local"))
        .isPresent();
    org.assertj.core.api.Assertions.assertThat(
            missionRepository.findMissionForDriver(mission.getId(), "driver1@gml.local"))
        .isEmpty();

    mockMvc
        .perform(
            get("/api/missions/{id}/secret-code", mission.getId())
                .cookie(
                    new MockCookie(
                        JwtAuthenticationFilter.COOKIE_NAME,
                        jwtService.generateToken("driver1@gml.local", "DRIVER"))))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            get("/api/missions/{id}/secret-code", mission.getId())
                .cookie(
                    new MockCookie(
                        JwtAuthenticationFilter.COOKIE_NAME,
                        jwtService.generateToken("driver2@gml.local", "DRIVER"))))
        .andExpect(status().isConflict());
  }

  @Test
  void dispatcherCannotReassignStartedMission() throws Exception {
    Mission mission = saveMissionForDriver1("secret-assign", MissionStatus.IN_PROGRESS);
    String jwt = jwtService.generateToken("dispatcher1@gml.local", "DISPATCHER");

    mockMvc
        .perform(
            patch("/api/missions/{id}/driver", mission.getId())
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"driverEmail":"driver2@gml.local"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MISSION_CHANGE_NOT_ALLOWED"));
  }

  @Test
  void driverCannotReassignMission() throws Exception {
    Mission mission = saveMissionForDriver1("secret-assign", MissionStatus.PENDING);
    String jwt = jwtService.generateToken("driver1@gml.local", "DRIVER");

    mockMvc
        .perform(
            patch("/api/missions/{id}/driver", mission.getId())
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"driverEmail":"driver2@gml.local"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void dispatcherCanCancelOwnPendingMission() throws Exception {
    Mission mission = saveMissionForDriver1("secret-cancel", MissionStatus.PENDING);
    String jwt = jwtService.generateToken("dispatcher1@gml.local", "DISPATCHER");

    mockMvc
        .perform(
            patch("/api/missions/{id}/cancel", mission.getId())
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    org.assertj.core.api.Assertions.assertThat(
            missionRepository.findById(mission.getId()).orElseThrow().getStatus())
        .isEqualTo(MissionStatus.CANCELLED);
  }

  @Test
  void adminCanCancelStartedMission() throws Exception {
    Mission mission = saveMissionForDriver1("secret-cancel", MissionStatus.IN_PROGRESS);
    String jwt = jwtService.generateToken("admin1@gml.local", "ADMIN");

    mockMvc
        .perform(
            patch("/api/missions/{id}/cancel", mission.getId())
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void driverCannotCancelMission() throws Exception {
    Mission mission = saveMissionForDriver1("secret-cancel", MissionStatus.PENDING);
    String jwt = jwtService.generateToken("driver1@gml.local", "DRIVER");

    mockMvc
        .perform(
            patch("/api/missions/{id}/cancel", mission.getId())
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt)))
        .andExpect(status().isForbidden());
  }

  @Test
  void completedMissionCannotBeCancelled() throws Exception {
    Mission mission = saveMissionForDriver1("secret-cancel", MissionStatus.COMPLETED);
    String jwt = jwtService.generateToken("dispatcher1@gml.local", "DISPATCHER");

    mockMvc
        .perform(
            patch("/api/missions/{id}/cancel", mission.getId())
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MISSION_CHANGE_NOT_ALLOWED"));
  }

  @Test
  void dispatcherListsOnlyActiveDrivers() throws Exception {
    String jwt = jwtService.generateToken("dispatcher1@gml.local", "DISPATCHER");

    mockMvc
        .perform(
            get("/api/missions/assignable-drivers")
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].email", hasItem("driver1@gml.local")))
        .andExpect(jsonPath("$[*].email", not(hasItem("dispatcher1@gml.local"))))
        .andExpect(jsonPath("$[*].email", not(hasItem("admin1@gml.local"))));
  }

  @Test
  void driverCannotListAssignableDrivers() throws Exception {
    String jwt = jwtService.generateToken("driver1@gml.local", "DRIVER");

    mockMvc
        .perform(
            get("/api/missions/assignable-drivers")
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void refreshTokenIsRotated() throws Exception {
    MvcResult login =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {"email":"dispatcher1@gml.local","password":"dispatch123"}
                                """))
            .andExpect(status().isOk())
            .andReturn();

    Cookie first = login.getResponse().getCookie(REFRESH_COOKIE);
    assertNotNull(first);

    MvcResult refreshed =
        mockMvc
            .perform(post("/api/auth/refresh").with(csrf()).cookie(first))
            .andExpect(status().isOk())
            .andExpect(cookie().exists(JwtAuthenticationFilter.COOKIE_NAME))
            .andReturn();

    Cookie rotated = refreshed.getResponse().getCookie(REFRESH_COOKIE);
    assertNotNull(rotated);
    assertNotEquals(first.getValue(), rotated.getValue());
  }

  @Test
  void refreshWithoutTokenIsRejected() throws Exception {
    mockMvc
        .perform(post("/api/auth/refresh").with(csrf()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_MISSING"));
  }

  @Test
  void logoutRevokesRefreshToken() throws Exception {
    MvcResult login =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {"email":"dispatcher1@gml.local","password":"dispatch123"}
                                """))
            .andExpect(status().isOk())
            .andReturn();

    Cookie refreshCookie = login.getResponse().getCookie(REFRESH_COOKIE);
    assertNotNull(refreshCookie);

    mockMvc
        .perform(post("/api/auth/logout").with(csrf()).cookie(refreshCookie))
        .andExpect(status().isOk());

    mockMvc
        .perform(post("/api/auth/refresh").with(csrf()).cookie(refreshCookie))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
  }

  @Test
  void disabledUserWithOtherwiseValidJwtIsRejected() throws Exception {
    User driver = userRepository.findByEmail("driver1@gml.local").orElseThrow();
    driver.setActive(false);
    userRepository.save(driver);

    try {
      String jwt = jwtService.generateToken(driver.getEmail(), driver.getRole().name());

      mockMvc
          .perform(
              get("/api/missions").cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt)))
          .andExpect(status().isUnauthorized());
    } finally {
      driver.setActive(true);
      userRepository.save(driver);
    }
  }

  private Mission saveMissionForDriver1(String secret, MissionStatus status) {
    User driver = userRepository.findByEmail("driver1@gml.local").orElseThrow();
    User dispatcher = userRepository.findByEmail("dispatcher1@gml.local").orElseThrow();

    Mission mission =
        new Mission(
            "Mission de test", "Lyon", secretEncryptionService.encrypt(secret), driver, dispatcher);
    mission.setStatus(status);
    return missionRepository.save(mission);
  }

  private void ensureUser(String email, String password, Role role) {
    if (userRepository.findByEmail(email).isPresent()) {
      return;
    }

    User user = new User(email, passwordEncoder.encode(password), role);

    userRepository.save(user);
  }
}
