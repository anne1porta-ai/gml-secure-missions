package com.gml.shield.security;

import com.gml.shield.config.CookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

import java.time.Duration;

/**
 * Centralise uniquement la lecture et l'écriture des cookies d'authentification.
 */
@Service
public class AuthCookieService {

    public static final String REFRESH_COOKIE = "gml_refresh_token";
    // Le refresh token n'est envoyé qu'aux endpoints d'authentification afin de réduire
    // son exposition inutile sur le reste de l'API.
    private static final String REFRESH_PATH = "/api/auth";

    private final boolean secureCookie;

    public AuthCookieService(CookieProperties properties) {
        this.secureCookie = properties.secure();
    }

    public void setAccessToken(HttpServletResponse response, String token, Duration maxAge) {
        setCookie(response, JwtAuthenticationFilter.COOKIE_NAME, token, "/", maxAge);
    }

    public void setRefreshToken(HttpServletResponse response, String token, Duration maxAge) {
        setCookie(response, REFRESH_COOKIE, token, REFRESH_PATH, maxAge);
    }

    public String getRefreshToken(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, REFRESH_COOKIE);
        return cookie != null ? cookie.getValue() : null;
    }

    public void clearAuthenticationCookies(HttpServletResponse response) {
        setAccessToken(response, "", Duration.ZERO);
        setRefreshToken(response, "", Duration.ZERO);
    }

    private void setCookie(HttpServletResponse response,
                           String name,
                           String value,
                           String path,
                           Duration maxAge) {
        // HttpOnly empêche le JavaScript du navigateur de lire les tokens en cas de XSS.
        // SameSite limite certains envois entre sites ; l'attribut Secure est
        // configurable pour distinguer les essais en HTTP local de la production HTTPS.
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secureCookie)
                .path(path)
                .maxAge(maxAge)
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
