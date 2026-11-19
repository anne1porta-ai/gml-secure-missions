package com.gml.shield.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * Gère le token CSRF utilisé entre le frontend et l'API.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain =
            new CsrfTokenRequestAttributeHandler();

    private final CsrfTokenRequestHandler xor =
            new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            Supplier<CsrfToken> csrfToken
    ) {
        xor.handle(request, response, csrfToken);

        // L'accès au token force sa création dès la première requête afin que la SPA
        // dispose immédiatement du cookie CSRF à renvoyer sur les requêtes d'écriture.
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(
            HttpServletRequest request,
            CsrfToken csrfToken
    ) {
        String headerValue =
                request.getHeader(csrfToken.getHeaderName());

        CsrfTokenRequestHandler handler;

        // Le frontend renvoie la valeur brute du cookie dans le header. Sans header,
        // on conserve le mécanisme XOR standard de Spring pour les autres usages.
        if (StringUtils.hasText(headerValue)) {
            handler = plain;
        } else {
            handler = xor;
        }

        return handler.resolveCsrfTokenValue(request, csrfToken);
    }
}