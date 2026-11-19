package com.gml.shield.controller;

import com.gml.shield.dto.CreateMissionRequest;
import com.gml.shield.dto.DriverResponse;
import com.gml.shield.dto.MissionResponse;
import com.gml.shield.dto.MissionStatusUpdateRequest;
import com.gml.shield.dto.ReassignMissionRequest;
import com.gml.shield.dto.SecretCodeResponse;
import com.gml.shield.dto.UpdateMissionRequest;
import com.gml.shield.model.User;
import com.gml.shield.security.AppUserDetails;
import com.gml.shield.service.MissionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Routes REST des missions.
 *
 * <p>@PreAuthorize ne vérifie que le rôle : l'appartenance de la mission au bon utilisateur est
 * contrôlée ensuite dans MissionService.
 */
@RestController
@RequestMapping("/api/missions")
public class MissionController {

  private final MissionService missionService;

  public MissionController(MissionService missionService) {
    this.missionService = missionService;
  }

  @PostMapping
  @PreAuthorize("hasRole('DISPATCHER')")
  public ResponseEntity<MissionResponse> createMission(
      @Valid @RequestBody CreateMissionRequest request,
      @AuthenticationPrincipal AppUserDetails userDetails) {
    User dispatcher = userDetails.getUser();
    return ResponseEntity.ok(missionService.createMission(request, dispatcher));
  }

  @PatchMapping("/{id}/cancel")
  @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
  public ResponseEntity<MissionResponse> cancelMission(
      @PathVariable Long id, @AuthenticationPrincipal AppUserDetails userDetails) {
    return ResponseEntity.ok(missionService.cancelMission(id, userDetails.getUser()));
  }

  @PatchMapping("/{id}/driver")
  @PreAuthorize("hasRole('DISPATCHER')")
  public ResponseEntity<MissionResponse> reassignMission(
      @PathVariable Long id,
      @Valid @RequestBody ReassignMissionRequest request,
      @AuthenticationPrincipal AppUserDetails userDetails) {
    return ResponseEntity.ok(missionService.reassignMission(id, request, userDetails.getUser()));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('DISPATCHER')")
  public ResponseEntity<MissionResponse> updateMission(
      @PathVariable Long id,
      @Valid @RequestBody UpdateMissionRequest request,
      @AuthenticationPrincipal AppUserDetails userDetails) {
    return ResponseEntity.ok(missionService.updateMission(id, request, userDetails.getUser()));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('DISPATCHER', 'DRIVER')")
  public ResponseEntity<List<MissionResponse>> listMissions(
      @AuthenticationPrincipal AppUserDetails userDetails) {
    User user = userDetails.getUser();
    return ResponseEntity.ok(missionService.listMissions(user));
  }

  /** Chauffeurs actifs auxquels le dispatcher peut assigner une mission. */
  @GetMapping("/assignable-drivers")
  @PreAuthorize("hasRole('DISPATCHER')")
  public ResponseEntity<List<DriverResponse>> listAssignableDrivers() {
    return ResponseEntity.ok(missionService.listAssignableDrivers());
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasRole('DRIVER')")
  public ResponseEntity<MissionResponse> updateStatus(
      @PathVariable Long id,
      @Valid @RequestBody MissionStatusUpdateRequest request,
      @AuthenticationPrincipal AppUserDetails userDetails) {
    User driver = userDetails.getUser();
    return ResponseEntity.ok(missionService.updateStatus(id, request.status(), driver));
  }

  /**
   * Révèle le code d'accès à la demande (RG2) : chauffeur assigné, mission en cours. Chaque appel,
   * accepté ou refusé, est tracé par l'intercepteur d'audit.
   */
  @GetMapping("/{id}/secret-code")
  @PreAuthorize("hasRole('DRIVER')")
  public ResponseEntity<SecretCodeResponse> revealSecretCode(
      @PathVariable Long id, @AuthenticationPrincipal AppUserDetails userDetails) {
    User driver = userDetails.getUser();
    // no-store : le code ne doit être conservé par aucun cache (navigateur ou proxy).
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(missionService.revealSecretCode(id, driver));
  }
}
