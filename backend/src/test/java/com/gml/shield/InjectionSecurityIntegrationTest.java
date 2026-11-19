package com.gml.shield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gml.shield.enums.Role;
import com.gml.shield.model.Mission;
import com.gml.shield.model.User;
import com.gml.shield.repository.MissionRepository;
import com.gml.shield.repository.UserRepository;
import com.gml.shield.security.JwtAuthenticationFilter;
import com.gml.shield.security.JwtService;
import java.util.Map;
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

/** Vérifie que les entrées ressemblant à des requêtes SQL restent des données. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InjectionSecurityIntegrationTest extends PostgreSqlIntegrationTestBase {

  private static final String DISPATCHER_EMAIL = "sql-test-dispatcher@gml.local";
  private static final String DRIVER_EMAIL = "sql-test-driver@gml.local";

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private UserRepository userRepository;
  @Autowired private MissionRepository missionRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JsonMapper jsonMapper;

  @BeforeEach
  void createTestAccounts() {
    createUserIfMissing(DISPATCHER_EMAIL, Role.DISPATCHER);
    createUserIfMissing(DRIVER_EMAIL, Role.DRIVER);
  }

  @Test
  void sqlInjectionInEmailIsRejectedByValidation() throws Exception {
    String requestBody =
        jsonMapper.writeValueAsString(
            Map.of("email", "' OR '1'='1' --", "password", "wrong-password"));

    mockMvc
        .perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void sqlInjectionInPasswordIsTreatedAsWrongPassword() throws Exception {
    String requestBody =
        jsonMapper.writeValueAsString(Map.of("email", DISPATCHER_EMAIL, "password", "' OR 1=1 --"));

    mockMvc
        .perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
  }

  @Test
  void sqlInjectionInMissionTitleIsStoredAsPlainText() throws Exception {
    String title = "x'); DROP TABLE mission; --";
    String requestBody =
        jsonMapper.writeValueAsString(
            Map.of(
                "title",
                title,
                "destination",
                "Lyon",
                "secretCode",
                "TEST-1234",
                "driverEmail",
                DRIVER_EMAIL));

    MvcResult response =
        mockMvc
            .perform(
                post("/api/missions")
                    .with(csrf())
                    .cookie(dispatcherCookie())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value(title))
            .andReturn();

    long missionId =
        jsonMapper.readTree(response.getResponse().getContentAsString()).get("id").asLong();
    Mission mission = missionRepository.findById(missionId).orElseThrow();

    assertEquals(title, mission.getTitle());
    assertTrue(missionRepository.count() > 0);
  }

  @Test
  void sqlInjectionInMissionIdIsRejectedAsInvalidNumber() throws Exception {
    mockMvc
        .perform(get("/api/missions/{id}/secret-code", "1' OR '1'='1").cookie(driverCookie()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  private MockCookie dispatcherCookie() {
    String jwt = jwtService.generateToken(DISPATCHER_EMAIL, Role.DISPATCHER.name());
    return new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt);
  }

  private MockCookie driverCookie() {
    String jwt = jwtService.generateToken(DRIVER_EMAIL, Role.DRIVER.name());
    return new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt);
  }

  private void createUserIfMissing(String email, Role role) {
    if (userRepository.findByEmail(email).isEmpty()) {
      String passwordHash = passwordEncoder.encode("local-test-password");
      userRepository.save(new User(email, passwordHash, role));
    }
  }
}
