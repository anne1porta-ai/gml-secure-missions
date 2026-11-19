package com.gml.shield.mapper;

import com.gml.shield.dto.CreateMissionRequest;
import com.gml.shield.dto.MissionResponse;
import com.gml.shield.model.Mission;
import com.gml.shield.model.User;
import org.springframework.stereotype.Component;

/**
 * Centralise les conversions entre contrats API et modèle de persistance Mission.
 *
 * Le mapper n'effectue aucun accès aux données et ne porte aucune règle métier.
 */
@Component
public class MissionMapper {

    public Mission toEntity(CreateMissionRequest request,
                            String encryptedSecret,
                            User driver,
                            User dispatcher) {
        Mission mission = new Mission(
                request.title(),
                request.destination(),
                encryptedSecret,
                driver,
                dispatcher
        );
        mission.setDescription(blankToNull(request.description()));
        mission.setScheduledAt(request.scheduledAt());
        return mission;
    }

    public MissionResponse toResponse(Mission mission) {
        return new MissionResponse(
                mission.getId(),
                mission.getTitle(),
                mission.getDestination(),
                mission.getDescription(),
                mission.getScheduledAt(),
                mission.getStatus(),
                mission.getDriver().getEmail(),
                mission.getDispatcher().getEmail(),
                mission.getCreatedAt()
        );
    }

    // Une description vide n'apporte rien : elle est enregistrée comme absente.
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
