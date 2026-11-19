package com.gml.shield.event;

/** Prévenir un chauffeur quand sa mission change. Aucun code secret n'est envoyé. */
public record MissionChangedEvent(String type, String driverEmail, Long missionId, String title) {}
