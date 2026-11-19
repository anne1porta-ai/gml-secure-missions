package com.gml.shield;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gml.shield.enums.MissionStatus;
import com.gml.shield.enums.Role;
import com.gml.shield.model.Document;
import com.gml.shield.model.Mission;
import com.gml.shield.model.User;
import com.gml.shield.repository.DocumentRepository;
import com.gml.shield.repository.MissionRepository;
import com.gml.shield.repository.UserRepository;
import com.gml.shield.security.JwtAuthenticationFilter;
import com.gml.shield.security.JwtService;
import com.gml.shield.service.FileStorageService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Vérifie l'ownership et les refus d'upload sur la frontière HTTP + PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentSecurityIntegrationTest extends PostgreSqlIntegrationTestBase {

  private static final int MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

  @Autowired private MockMvc mockMvc;

  @Autowired private JwtService jwtService;

  @Autowired private UserRepository userRepository;

  @Autowired private MissionRepository missionRepository;

  @Autowired private DocumentRepository documentRepository;

  @Autowired private FileStorageService fileStorageService;

  @Autowired private PasswordEncoder passwordEncoder;

  private User driverOne;
  private User driverTwo;
  private User dispatcherOne;
  private User dispatcherTwo;

  @BeforeEach
  void setUpUsers() {
    driverOne = ensureUser("driver-one@gml.local", Role.DRIVER);
    driverTwo = ensureUser("driver-two@gml.local", Role.DRIVER);
    dispatcherOne = ensureUser("dispatcher-one@gml.local", Role.DISPATCHER);
    dispatcherTwo = ensureUser("dispatcher-two@gml.local", Role.DISPATCHER);
  }

  @Test
  void assignedDriverCanUploadValidPdf() throws Exception {
    Mission mission = createStartedMission(driverOne, dispatcherOne);
    MockMultipartFile file = pdfFile("proof.pdf", smallPdf());
    String jwt = tokenFor(driverOne);

    mockMvc
        .perform(
            multipart("/api/missions/{missionId}/documents", mission.getId())
                .file(file)
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.originalFilename").value("proof.pdf"))
        .andExpect(jsonPath("$.mimeType").value("application/pdf"));

    assertThat(documentRepository.findDocumentsForMission(mission.getId())).hasSize(1);
  }

  @Test
  void driverCannotUploadProofBeforeMissionStarts() throws Exception {
    Mission mission = createMission(driverOne, dispatcherOne);

    mockMvc
        .perform(
            multipart("/api/missions/{missionId}/documents", mission.getId())
                .file(pdfFile("preuve.pdf", smallPdf()))
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, tokenFor(driverOne))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOCUMENT_UPLOAD_UNAVAILABLE"));

    assertThat(documentRepository.findDocumentsForMission(mission.getId())).isEmpty();
  }

  @Test
  void driverCannotUploadProofAfterMissionEnds() throws Exception {
    Mission mission = createMission(driverOne, dispatcherOne);
    mission.setStatus(MissionStatus.COMPLETED);
    missionRepository.save(mission);

    mockMvc
        .perform(
            multipart("/api/missions/{missionId}/documents", mission.getId())
                .file(pdfFile("preuve.pdf", smallPdf()))
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, tokenFor(driverOne))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOCUMENT_UPLOAD_UNAVAILABLE"));

    assertThat(documentRepository.findDocumentsForMission(mission.getId())).isEmpty();
  }

  @Test
  void driverCanStillListDocumentsAfterMissionEnds() throws Exception {
    Mission mission = createStartedMission(driverOne, dispatcherOne);
    createDocument(mission, "preuve.pdf");
    mission.setStatus(MissionStatus.COMPLETED);
    missionRepository.save(mission);

    mockMvc
        .perform(
            get("/api/missions/{missionId}/documents", mission.getId())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, tokenFor(driverOne))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].originalFilename").value("preuve.pdf"));
  }

  @Test
  void driverCannotUploadDocumentForAnotherDriversMission() throws Exception {
    Mission mission = createMission(driverOne, dispatcherOne);
    long documentsBefore = documentRepository.count();
    String jwt = tokenFor(driverTwo);

    mockMvc
        .perform(
            multipart("/api/missions/{missionId}/documents", mission.getId())
                .file(pdfFile("proof.pdf", smallPdf()))
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MISSION_NOT_FOUND"));

    assertThat(documentRepository.count()).isEqualTo(documentsBefore);
  }

  @Test
  void driverCannotChangeStatusOfAnotherDriversMission() throws Exception {
    Mission mission = createMission(driverOne, dispatcherOne);

    mockMvc
        .perform(
            patch("/api/missions/{missionId}/status", mission.getId())
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, tokenFor(driverTwo)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"status":"IN_PROGRESS"}
                                """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MISSION_NOT_FOUND"));

    Mission unchangedMission = missionRepository.findById(mission.getId()).orElseThrow();
    assertThat(unchangedMission.getStatus()).isEqualTo(MissionStatus.PENDING);
  }

  @Test
  void missionCannotBeCompletedWithoutProof() throws Exception {
    Mission mission = createMission(driverOne, dispatcherOne);
    mission.setStatus(MissionStatus.IN_PROGRESS);
    missionRepository.save(mission);

    mockMvc
        .perform(
            patch("/api/missions/{missionId}/status", mission.getId())
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, tokenFor(driverOne)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DELIVERY_PROOF_REQUIRED"));

    Mission unchanged = missionRepository.findById(mission.getId()).orElseThrow();
    assertThat(unchanged.getStatus()).isEqualTo(MissionStatus.IN_PROGRESS);
  }

  @Test
  void missionCanBeCompletedAfterProofUpload() throws Exception {
    Mission mission = createMission(driverOne, dispatcherOne);
    mission.setStatus(MissionStatus.IN_PROGRESS);
    missionRepository.save(mission);
    String jwt = tokenFor(driverOne);

    mockMvc
        .perform(
            multipart("/api/missions/{missionId}/documents", mission.getId())
                .file(pdfFile("preuve.pdf", smallPdf()))
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/missions/{missionId}/status", mission.getId())
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  @Test
  void disguisedTextFileIsRejectedEvenWhenClientDeclaresPdf() throws Exception {
    Mission mission = createStartedMission(driverOne, dispatcherOne);
    MockMultipartFile fakePdf =
        new MockMultipartFile(
            "file", "fake.pdf", "application/pdf", "not a pdf".getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/missions/{missionId}/documents", mission.getId())
                .file(fakePdf)
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, tokenFor(driverOne))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("FILE_REJECTED"));
  }

  @Test
  void fileExactlyAtBusinessLimitIsAccepted() throws Exception {
    Mission mission = createStartedMission(driverOne, dispatcherOne);
    MockMultipartFile limitFile = pdfFile("limit.pdf", pdfContent(MAX_FILE_SIZE_BYTES));

    mockMvc
        .perform(
            multipart("/api/missions/{missionId}/documents", mission.getId())
                .file(limitFile)
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, tokenFor(driverOne))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mimeType").value("application/pdf"));
  }

  @Test
  void fileOneByteAboveBusinessLimitIsRejected() throws Exception {
    Mission mission = createStartedMission(driverOne, dispatcherOne);
    MockMultipartFile tooLarge =
        new MockMultipartFile(
            "file", "too-large.pdf", "application/pdf", new byte[MAX_FILE_SIZE_BYTES + 1]);

    mockMvc
        .perform(
            multipart("/api/missions/{missionId}/documents", mission.getId())
                .file(tooLarge)
                .with(csrf())
                .cookie(new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, tokenFor(driverOne))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void dispatcherCannotDownloadDocumentFromAnotherDispatchersMission() throws Exception {
    Mission mission = createMission(driverOne, dispatcherOne);
    Document document = createDocument(mission, "proof.pdf");

    mockMvc
        .perform(
            get(
                    "/api/missions/{missionId}/documents/{documentId}",
                    mission.getId(),
                    document.getId())
                .cookie(
                    new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, tokenFor(dispatcherTwo))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
  }

  @Test
  void owningDispatcherCanDownloadDocument() throws Exception {
    Mission mission = createMission(driverOne, dispatcherOne);
    byte[] pdf = smallPdf();
    Document document = createDocument(mission, "proof.pdf", pdf);

    mockMvc
        .perform(
            get(
                    "/api/missions/{missionId}/documents/{documentId}",
                    mission.getId(),
                    document.getId())
                .cookie(
                    new MockCookie(JwtAuthenticationFilter.COOKIE_NAME, tokenFor(dispatcherOne))))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/pdf"))
        .andExpect(content().bytes(pdf));
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

  private Mission createMission(User driver, User dispatcher) {
    Mission mission =
        new Mission("Livraison test", "Paris", "encrypted-secret-for-test", driver, dispatcher);
    return missionRepository.save(mission);
  }

  private Mission createStartedMission(User driver, User dispatcher) {
    Mission mission = createMission(driver, dispatcher);
    mission.setStatus(MissionStatus.IN_PROGRESS);
    return missionRepository.save(mission);
  }

  private Document createDocument(Mission mission, String filename) {
    return createDocument(mission, filename, smallPdf());
  }

  private Document createDocument(Mission mission, String filename, byte[] content) {
    FileStorageService.StoredFile stored = fileStorageService.storeFile(pdfFile(filename, content));
    Document document =
        new Document(mission, stored.storagePath(), filename, stored.detectedMimeType());
    return documentRepository.save(document);
  }

  private String tokenFor(User user) {
    return jwtService.generateToken(user.getEmail(), user.getRole().name());
  }

  private MockMultipartFile pdfFile(String filename, byte[] content) {
    return new MockMultipartFile("file", filename, "application/pdf", content);
  }

  private byte[] smallPdf() {
    return "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
  }

  private byte[] pdfContent(int size) {
    byte[] content = new byte[size];
    byte[] header = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
    byte[] end = "\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    System.arraycopy(header, 0, content, 0, header.length);
    System.arraycopy(end, 0, content, content.length - end.length, end.length);
    return content;
  }
}
