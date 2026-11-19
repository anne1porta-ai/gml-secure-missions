package com.gml.shield;

import com.gml.shield.event.MissionCreatedEvent;
import com.gml.shield.event.MissionNotificationListener;
import com.gml.shield.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MissionNotificationListenerTest {

    @Test
    void listenerIsExplicitlyConfiguredAfterCommit() throws Exception {
        Method method = MissionNotificationListener.class
                .getMethod("onMissionCreated", MissionCreatedEvent.class);

        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase());
    }

    @Test
    void listenerSendsOnlyToAssignedDriver() {
        NotificationService notificationService = mock(NotificationService.class);
        MissionNotificationListener listener =
                new MissionNotificationListener(notificationService);

        MissionCreatedEvent event = new MissionCreatedEvent(
                "driver1@gml.local",
                42L,
                "Livraison",
                "Lyon",
                Instant.parse("2026-09-05T12:00:00Z")
        );

        listener.onMissionCreated(event);

        verify(notificationService).sendNotificationToUser(
                eq("driver1@gml.local"),
                eq("NEW_MISSION"),
                any()
        );
    }
}
