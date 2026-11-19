package com.gml.shield.controller;

import com.gml.shield.config.JwtProperties;
import com.gml.shield.dto.ApiErrorResponse;
import com.gml.shield.dto.LoginRequest;
import com.gml.shield.dto.LoginResponse;
import com.gml.shield.model.RefreshToken;
import com.gml.shield.model.User;
import com.gml.shield.security.AppUserDetails;
import com.gml.shield.security.AuthCookieService;
import com.gml.shield.security.JwtService;
import com.gml.shield.security.LoginAttemptService;
import com.gml.shield.security.RefreshTokenException;
import com.gml.shield.security.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gère la connexion, le renouvellement du token et la déconnexion. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;
  private final LoginAttemptService loginAttemptService;
  private final AuthCookieService authCookieService;
  private final long accessExpirationMs;
  private final Clock clock;

  public AuthController(
      AuthenticationManager authenticationManager,
      JwtService jwtService,
      RefreshTokenService refreshTokenService,
      LoginAttemptService loginAttemptService,
      AuthCookieService authCookieService,
      JwtProperties jwtProperties,
      Clock clock) {
    this.authenticationManager = authenticationManager;
    this.jwtService = jwtService;
    this.refreshTokenService = refreshTokenService;
    this.loginAttemptService = loginAttemptService;
    this.authCookieService = authCookieService;
    this.accessExpirationMs = jwtProperties.expirationMs();
    this.clock = clock;
  }

  @GetMapping("/csrf")
  public ResponseEntity<Map<String, String>> csrf(CsrfToken csrfToken) {
    return ResponseEntity.ok(Map.of("token", csrfToken.getToken()));
  }

  /**
   * Connecte un utilisateur et dépose ses jetons dans des cookies HttpOnly.
   *
   * <p>Le blocage des tentatives est vérifié avant le mot de passe, pour freiner les essais en
   * série.
   *
   * <p>Le message d'erreur est le même que l'e-mail existe ou non, pour ne pas révéler les comptes.
   */
  @PostMapping("/login")
  public ResponseEntity<?> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse response) {
    String ipAddress = httpRequest.getRemoteAddr();

    // Le blocage est contrôlé avant le mot de passe pour limiter
    // les tentatives répétées, notamment sur un compte existant.

    if (loginAttemptService.isBlocked(request.email(), ipAddress)) {
      return lockedResponse(request.email(), ipAddress, response);
    }

    Authentication authentication;

    try {
      authentication = authenticate(request);
    } catch (AuthenticationException exception) {
      loginAttemptService.loginFailed(request.email(), ipAddress);

      if (loginAttemptService.isBlocked(request.email(), ipAddress)) {
        return lockedResponse(request.email(), ipAddress, response);
      }

      // Une erreur uniforme ne confirme pas si cette adresse e-mail existe.
      return errorResponse(401, "AUTH_INVALID_CREDENTIALS", "Identifiants invalides");
    }

    loginAttemptService.loginSucceeded(request.email(), ipAddress);

    AppUserDetails userDetails = (AppUserDetails) authentication.getPrincipal();
    User user = userDetails.getUser();

    issueTokens(response, user);
    return ResponseEntity.ok(toLoginResponse(user));
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
    String value = authCookieService.getRefreshToken(request);

    if (value == null) {
      return errorResponse(401, "REFRESH_TOKEN_MISSING", "Refresh token absent");
    }

    try {
      RefreshToken refreshToken = refreshTokenService.requireValid(value);
      User user = refreshToken.getUser();

      issueTokens(response, user);
      return ResponseEntity.ok(toLoginResponse(user));
    } catch (RefreshTokenException exception) {
      authCookieService.clearAuthenticationCookies(response);
      return errorResponse(401, "REFRESH_TOKEN_INVALID", exception.getMessage());
    }
  }

  /**
   * Révoque le refresh token et efface les cookies.
   *
   * <p>Le JWT déjà émis reste valable jusqu'à son expiration : c'est une limite connue, compensée
   * par sa durée courte et par la désactivation possible du compte.
   */
  @PostMapping("/logout")
  public ResponseEntity<Map<String, String>> logout(
      HttpServletRequest request, HttpServletResponse response) {
    String value = authCookieService.getRefreshToken(request);

    // La déconnexion révoque le refresh token et efface les cookies.
    // Un JWT déjà émis peut rester valide jusqu'à son échéance, sauf désactivation du compte.
    if (value != null) {
      refreshTokenService.revoke(value);
    }

    authCookieService.clearAuthenticationCookies(response);

    return ResponseEntity.ok(Map.of("status", "logged out"));
  }

  @GetMapping("/me")
  public ResponseEntity<?> me(@AuthenticationPrincipal AppUserDetails userDetails) {
    if (userDetails == null || !userDetails.isEnabled()) {
      return errorResponse(401, "UNAUTHENTICATED", "Non authentifié");
    }

    return ResponseEntity.ok(toLoginResponse(userDetails.getUser()));
  }

  private Authentication authenticate(LoginRequest request) {
    UsernamePasswordAuthenticationToken loginToken =
        new UsernamePasswordAuthenticationToken(request.email(), request.password());
    return authenticationManager.authenticate(loginToken);
  }

  private LoginResponse toLoginResponse(User user) {
    return new LoginResponse(user.getEmail(), user.getRole().name());
  }

  private ResponseEntity<ApiErrorResponse> lockedResponse(
      String email, String ipAddress, HttpServletResponse response) {
    long remainingSeconds = loginAttemptService.getRemainingLockSeconds(email, ipAddress);
    response.setHeader("Retry-After", Long.toString(remainingSeconds));
    return errorResponse(
        429, "AUTH_LOCKED", "Trop de tentatives. Réessayez dans " + remainingSeconds + " secondes");
  }

  /**
   * Émet un JWT court (15 min) et un refresh token enregistré en base.
   *
   * <p>Le JWT ne peut pas être révoqué, d'où sa durée courte. Le refresh token, lui, peut l'être :
   * c'est ce qui permet de couper une session à la déconnexion.
   */
  private void issueTokens(HttpServletResponse response, User user) {
    String jwt = jwtService.generateToken(user.getEmail(), user.getRole().name());
    authCookieService.setAccessToken(response, jwt, Duration.ofMillis(accessExpirationMs));

    // Chaque émission remplace le refresh token précédent : après renouvellement,
    // l'ancien jeton ne doit pas pouvoir prolonger une session.
    RefreshToken refreshToken = refreshTokenService.createFor(user);
    Duration refreshMaxAge = Duration.between(clock.instant(), refreshToken.getExpiryDate());
    authCookieService.setRefreshToken(response, refreshToken.getToken(), refreshMaxAge);
  }

  private ResponseEntity<ApiErrorResponse> errorResponse(int status, String code, String message) {
    return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message));
  }
}
