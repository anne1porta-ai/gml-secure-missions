package com.gml.shield.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gml.shield.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/** Vérifie quelles requêtes alimentent le journal d'audit. */
class WriteRequestAuditInterceptorTest {

  private AuditLogService auditLogService;
  private WriteRequestAuditInterceptor interceptor;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    auditLogService = mock(AuditLogService.class);
    interceptor = new WriteRequestAuditInterceptor(auditLogService);
  }

  @Test
  void revealedSecretCodeIsAudited() {
    complete("GET", "/api/missions/5/secret-code", 200);

    verify(auditLogService)
        .recordAction(null, null, "GET /api/missions/5/secret-code", "127.0.0.1", 200);
  }

  @Test
  void refusedSecretCodeReadIsAuditedWithItsStatus() {
    complete("GET", "/api/missions/5/secret-code", 409);

    verify(auditLogService)
        .recordAction(null, null, "GET /api/missions/5/secret-code", "127.0.0.1", 409);
  }

  @Test
  void ordinaryReadIsNotAudited() {
    complete("GET", "/api/missions", 200);

    verifyNoInteractions(auditLogService);
  }

  @Test
  void mutatingRequestIsStillAudited() {
    complete("PATCH", "/api/missions/5/status", 200);

    verify(auditLogService)
        .recordAction(null, null, "PATCH /api/missions/5/status", "127.0.0.1", 200);
  }

  private void complete(String method, String uri, int status) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(status);

    interceptor.afterCompletion(request, response, new Object(), null);
  }
}
