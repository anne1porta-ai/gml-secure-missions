package com.gml.shield.controller;

import com.gml.shield.config.JwtProperties;
import com.gml.shield.dto.LoginRequest;
import com.gml.shield.dto.LoginResponse;
import com.gml.shield.enums.Role;
import com.gml.shield.model.RefreshToken;
import com.gml.shield.model.User;
import com.gml.shield.security.AppUserDetails;
import com.gml.shield.security.AuthCookieService;
import com.gml.shield.security.JwtService;
import com.gml.shield.security.LoginAttemptService;
import com.gml.shield.security.RefreshTokenException;
import com.gml.shield.security.RefreshTokenService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

  private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");
  private static final long ACCESS_EXPIRATION_MS = 60_000L;
  private static final String EMAIL = "driver1@gml.local";
  private static final String IP_ADDRESS = "192.0.2.10";

  private AuthenticationManager authenticationManager;
  private JwtService jwtService;
  private RefreshTokenService refreshTokenService;
  private LoginAttemptService loginAttemptService;
  private AuthCookieService authCookieService;
  private AuthController controller;

  @BeforeEach
  void setUp() {
    authenticationManager = mock(AuthenticationManager.class);
    jwtService = mock(JwtService.class);
    refreshTokenService = mock(RefreshTokenService.class);
    loginAttemptService = mock(LoginAttemptService.class);
    authCookieService = mock(AuthCookieService.class);

    controller =
        new AuthController(
            authenticationManager,
            jwtService,
            refreshTokenService,
            loginAttemptService,
            authCookieService,
            new JwtProperties("not-used-by-this-unit-test", ACCESS_EXPIRATION_MS, 604_800_000L),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void loginReturnsLockedResponseWhenIdentityIsAlreadyBlocked() {
    LoginRequest loginRequest = new LoginRequest(EMAIL, "password");
    MockHttpServletRequest request = requestFrom(IP_ADDRESS);
    MockHttpServletResponse response = new MockHttpServletResponse();

    when(loginAttemptService.isBlocked(EMAIL, IP_ADDRESS)).thenReturn(true);
    when(loginAttemptService.getRemainingLockSeconds(EMAIL, IP_ADDRESS)).thenReturn(42L);

    ResponseEntity<?> result = controller.login(loginRequest, request, response);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getHeader("Retry-After")).isEqualTo("42");
    verify(authenticationManager, never()).authenticate(any());
  }

  @Test
  void loginReturnsUnauthorizedWhenCredentialsAreInvalidButIdentityIsNotBlocked() {
    LoginRequest loginRequest = new LoginRequest(EMAIL, "bad-password");
    MockHttpServletRequest request = requestFrom(IP_ADDRESS);
    MockHttpServletResponse response = new MockHttpServletResponse();

    when(loginAttemptService.isBlocked(EMAIL, IP_ADDRESS)).thenReturn(false, false);
    when(authenticationManager.authenticate(any()))
        .thenThrow(new BadCredentialsException("invalid credentials"));

    ResponseEntity<?> result = controller.login(loginRequest, request, response);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verify(loginAttemptService).loginFailed(EMAIL, IP_ADDRESS);
  }

  @Test
  void loginReturnsLockedResponseWhenFailedAttemptReachesBlockingThreshold() {
    LoginRequest loginRequest = new LoginRequest(EMAIL, "bad-password");
    MockHttpServletRequest request = requestFrom(IP_ADDRESS);
    MockHttpServletResponse response = new MockHttpServletResponse();

    when(loginAttemptService.isBlocked(EMAIL, IP_ADDRESS)).thenReturn(false, true);
    when(loginAttemptService.getRemainingLockSeconds(EMAIL, IP_ADDRESS)).thenReturn(60L);
    when(authenticationManager.authenticate(any()))
        .thenThrow(new BadCredentialsException("invalid credentials"));

    ResponseEntity<?> result = controller.login(loginRequest, request, response);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getHeader("Retry-After")).isEqualTo("60");
    verify(loginAttemptService).loginFailed(EMAIL, IP_ADDRESS);
  }

  @Test
  void successfulLoginClearsFailuresAndIssuesBothTokens() {
    LoginRequest loginRequest = new LoginRequest(EMAIL, "password");
    MockHttpServletRequest request = requestFrom(IP_ADDRESS);
    MockHttpServletResponse response = new MockHttpServletResponse();
    User user = new User(EMAIL, "hash", Role.DRIVER);
    AppUserDetails userDetails = new AppUserDetails(user);
    Authentication authentication = mock(Authentication.class);
    RefreshToken refreshToken = new RefreshToken("refresh-1", user, NOW.plusSeconds(600));

    when(loginAttemptService.isBlocked(EMAIL, IP_ADDRESS)).thenReturn(false);
    when(authenticationManager.authenticate(any())).thenReturn(authentication);
    when(authentication.getPrincipal()).thenReturn(userDetails);
    when(jwtService.generateToken(EMAIL, "DRIVER")).thenReturn("access-token");
    when(refreshTokenService.createFor(user)).thenReturn(refreshToken);

    ResponseEntity<?> result = controller.login(loginRequest, request, response);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isEqualTo(new LoginResponse(EMAIL, "DRIVER"));
    verify(loginAttemptService).loginSucceeded(EMAIL, IP_ADDRESS);
    verify(authCookieService)
        .setAccessToken(response, "access-token", Duration.ofMillis(ACCESS_EXPIRATION_MS));
    verify(authCookieService)
        .setRefreshToken(response, "refresh-1", Duration.ofSeconds(600));
  }

  @Test
  void refreshWithoutCookieReturnsUnauthorized() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(authCookieService.getRefreshToken(request)).thenReturn(null);

    ResponseEntity<?> result = controller.refresh(request, response);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verify(refreshTokenService, never()).requireValid(any());
  }

  @Test
  void invalidRefreshTokenClearsAuthenticationCookies() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(authCookieService.getRefreshToken(request)).thenReturn("invalid-refresh");
    when(refreshTokenService.requireValid("invalid-refresh"))
        .thenThrow(new RefreshTokenException("Refresh token invalide"));

    ResponseEntity<?> result = controller.refresh(request, response);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verify(authCookieService).clearAuthenticationCookies(response);
  }

  @Test
  void validRefreshTokenIsRotatedAndReturnsUser() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    User user = new User(EMAIL, "hash", Role.DRIVER);
    RefreshToken currentToken = new RefreshToken("refresh-old", user, NOW.plusSeconds(300));
    RefreshToken rotatedToken = new RefreshToken("refresh-new", user, NOW.plusSeconds(900));

    when(authCookieService.getRefreshToken(request)).thenReturn("refresh-old");
    when(refreshTokenService.requireValid("refresh-old")).thenReturn(currentToken);
    when(jwtService.generateToken(EMAIL, "DRIVER")).thenReturn("new-access-token");
    when(refreshTokenService.createFor(user)).thenReturn(rotatedToken);

    ResponseEntity<?> result = controller.refresh(request, response);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isEqualTo(new LoginResponse(EMAIL, "DRIVER"));
    verify(authCookieService)
        .setRefreshToken(response, "refresh-new", Duration.ofSeconds(900));
  }

  @Test
  void logoutWithoutRefreshCookieOnlyClearsCookies() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(authCookieService.getRefreshToken(request)).thenReturn(null);

    ResponseEntity<?> result = controller.logout(request, response);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(refreshTokenService, never()).revoke(any());
    verify(authCookieService).clearAuthenticationCookies(response);
  }

  @Test
  void logoutWithRefreshCookieRevokesToken() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(authCookieService.getRefreshToken(request)).thenReturn("refresh-token");

    ResponseEntity<?> result = controller.logout(request, response);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(refreshTokenService).revoke("refresh-token");
    verify(authCookieService).clearAuthenticationCookies(response);
  }

  @Test
  void meRejectsMissingPrincipal() {
    ResponseEntity<?> result = controller.me(null);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void meRejectsDisabledPrincipal() {
    User user = new User(EMAIL, "hash", Role.DRIVER);
    user.setActive(false);

    ResponseEntity<?> result = controller.me(new AppUserDetails(user));

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void meReturnsAuthenticatedUser() {
    User user = new User(EMAIL, "hash", Role.DRIVER);

    ResponseEntity<?> result = controller.me(new AppUserDetails(user));

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isEqualTo(new LoginResponse(EMAIL, "DRIVER"));
  }

  private static MockHttpServletRequest requestFrom(String ipAddress) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(ipAddress);
    return request;
  }
}
