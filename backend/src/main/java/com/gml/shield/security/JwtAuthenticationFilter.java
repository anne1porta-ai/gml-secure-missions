package com.gml.shield.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;

/**
 * Vérifie le JWT reçu dans le cookie et charge l'utilisateur connecté.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "gml_token";

    private final JwtService jwtService;
    private final DatabaseUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, DatabaseUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        // Le jeton vient d'un cookie HttpOnly plutôt que d'un paramètre d'URL,
        // ce qui évite de l'exposer dans l'historique ou les journaux des URLs.
        String token = extractTokenFromCookies(request);

        if (token != null) {
            boolean validToken = jwtService.isValid(token);
            boolean alreadyAuthenticated =
                    SecurityContextHolder.getContext().getAuthentication() != null;

            // Un JWT falsifié ou expiré ne crée aucune identité authentifiée.
            // Une route protégée renverra alors une erreur d'authentification.
            if (validToken && !alreadyAuthenticated) {
                try {
                    String email = jwtService.extractEmail(token);

                    // Même avec un JWT valide, l'utilisateur est relu en base afin qu'un
                    // compte désactivé ou un changement de droits soit pris en compte
                    // immédiatement.
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                    if (userDetails.isEnabled()) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());

                        WebAuthenticationDetailsSource detailsSource =
                                new WebAuthenticationDetailsSource();
                        authentication.setDetails(detailsSource.buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (UsernameNotFoundException ignored) {
                    // On laisse la chaîne Spring poursuivre sans authentification afin que
                    // le point d'entrée de sécurité renvoie une réponse 401 uniforme.
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromCookies(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, COOKIE_NAME);
        return cookie != null ? cookie.getValue() : null;
    }
}
