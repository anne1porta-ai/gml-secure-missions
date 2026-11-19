package com.gml.shield.service;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationServiceTest {

  @Test
  void notificationToUnknownUserIsIgnored() {
    NotificationService service = new NotificationService();

    service.sendNotificationToUser("nobody@gml.local", "NEW_MISSION", "payload");

    assertThat(emitterMap(service)).isEmpty();
  }

  @Test
  void notificationToUserWithEmptyEmitterSetIsIgnored() {
    NotificationService service = new NotificationService();
    emitterMap(service).put("driver@gml.local", ConcurrentHashMap.newKeySet());

    service.sendNotificationToUser("driver@gml.local", "NEW_MISSION", "payload");

    assertThat(emitterMap(service)).containsKey("driver@gml.local");
  }

  @Test
  void notificationIsSentToActiveEmitter() throws Exception {
    NotificationService service = new NotificationService();
    SseEmitter emitter = mock(SseEmitter.class);
    Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    emitters.add(emitter);
    emitterMap(service).put("driver@gml.local", emitters);

    service.sendNotificationToUser("driver@gml.local", "NEW_MISSION", "payload");

    verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    assertThat(emitterMap(service).get("driver@gml.local")).containsExactly(emitter);
  }

  @Test
  void failingOnlyEmitterIsRemoved() throws Exception {
    NotificationService service = new NotificationService();
    SseEmitter emitter = mock(SseEmitter.class);
    Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    emitters.add(emitter);
    emitterMap(service).put("driver@gml.local", emitters);
    doThrow(new IOException("connection closed"))
        .when(emitter)
        .send(any(SseEmitter.SseEventBuilder.class));

    service.sendNotificationToUser("driver@gml.local", "NEW_MISSION", "payload");

    verify(emitter).complete();
    assertThat(emitterMap(service)).doesNotContainKey("driver@gml.local");
  }

  @Test
  void failingEmitterDoesNotRemoveOtherActiveEmitter() throws Exception {
    NotificationService service = new NotificationService();
    SseEmitter failingEmitter = mock(SseEmitter.class);
    SseEmitter activeEmitter = mock(SseEmitter.class);
    Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    emitters.add(failingEmitter);
    emitters.add(activeEmitter);
    emitterMap(service).put("driver@gml.local", emitters);
    doThrow(new IllegalStateException("already completed"))
        .when(failingEmitter)
        .send(any(SseEmitter.SseEventBuilder.class));

    service.sendNotificationToUser("driver@gml.local", "NEW_MISSION", "payload");

    verify(failingEmitter).complete();
    verify(activeEmitter).send(any(SseEmitter.SseEventBuilder.class));
    assertThat(emitterMap(service).get("driver@gml.local")).containsExactly(activeEmitter);
  }

  @Test
  void removeEmitterReturnsSafelyWhenUserMapDoesNotExist() {
    NotificationService service = new NotificationService();
    SseEmitter emitter = mock(SseEmitter.class);

    ReflectionTestUtils.invokeMethod(service, "removeEmitter", "missing@gml.local", emitter);

    assertThat(emitterMap(service)).isEmpty();
  }

  @SuppressWarnings("unchecked")
  private static ConcurrentHashMap<String, Set<SseEmitter>> emitterMap(NotificationService service) {
    return (ConcurrentHashMap<String, Set<SseEmitter>>)
        ReflectionTestUtils.getField(service, "emittersByEmail");
  }
}
