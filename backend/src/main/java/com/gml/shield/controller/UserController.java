package com.gml.shield.controller;

import com.gml.shield.dto.CreateUserRequest;
import com.gml.shield.dto.UserResponse;
import com.gml.shield.dto.UserStatusUpdateRequest;
import com.gml.shield.service.UserManagementService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration des comptes, réservée au rôle ADMIN.
 *
 * <p>Chaque création ou changement d'état est tracé par l'intercepteur d'audit.
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

  private final UserManagementService userManagementService;

  public UserController(UserManagementService userManagementService) {
    this.userManagementService = userManagementService;
  }

  @GetMapping
  public List<UserResponse> listUsers() {
    return userManagementService.listUsers();
  }

  @PostMapping
  public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(userManagementService.createUser(request));
  }

  @PatchMapping("/{id}/active")
  public UserResponse updateActive(
      @PathVariable Long id, @Valid @RequestBody UserStatusUpdateRequest request) {
    return userManagementService.updateActive(id, request.active());
  }
}
