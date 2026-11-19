package com.gml.shield.event;

import com.gml.shield.service.NotificationService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Envoie les notifications au chauffeur quand une mission est enregistrée. */
@Component
public class MissionNotificationListener {

  private final NotificationService notificationService;

  public MissionNotificationListener(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  // Une nouvelle mission n'est annoncée que si elle est bien enregistrée en base.
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMissionCreated(MissionCreatedEvent event) {
    // Le message annonce une mission sans transporter son code d'entrepôt.
    // Le navigateur récupère ensuite les données autorisées via l'API REST.
    Map<String, Object> notification = new HashMap<>();
    notification.put("type", "NEW_MISSION");
    notification.put("missionId", event.missionId());
    notification.put("title", event.title());
    notification.put("destination", event.destination());
    notification.put("timestamp", event.occurredAt().toString());

    notificationService.sendNotificationToUser(event.driverEmail(), "NEW_MISSION", notification);
  }

  // Même principe pour une annulation, une modification ou une réaffectation.
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMissionChanged(MissionChangedEvent event) {
    // Le même canal sert au retrait d'une mission : l'ancien chauffeur
    // reçoit le changement et recharge sa liste, qui ne contient plus la mission.
    Map<String, Object> notification = new HashMap<>();
    notification.put("type", event.type());
    notification.put("missionId", event.missionId());
    notification.put("title", event.title());

    notificationService.sendNotificationToUser(event.driverEmail(), event.type(), notification);
  }
}
