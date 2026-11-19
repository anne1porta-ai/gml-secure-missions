package com.gml.shield.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Horloge contrôlable utilisée pour tester les règles dépendantes du temps sans attente réelle.
 */
public final class MutableClock extends Clock {

    private Instant currentInstant;
    private final ZoneId zone;

    public MutableClock(Instant initialInstant) {
        this(initialInstant, ZoneOffset.UTC);
    }

    private MutableClock(Instant initialInstant, ZoneId zone) {
        this.currentInstant = Objects.requireNonNull(initialInstant);
        this.zone = Objects.requireNonNull(zone);
    }

    public void advance(Duration duration) {
        currentInstant = currentInstant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(currentInstant, newZone);
    }

    @Override
    public Instant instant() {
        return currentInstant;
    }
}
