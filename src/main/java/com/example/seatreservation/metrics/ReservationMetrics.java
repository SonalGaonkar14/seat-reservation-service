package com.example.seatreservation.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

@Component
public class ReservationMetrics {
    private final Counter confirmed;
    private final Counter seatTaken;
    private final Counter limit;
    private final Counter replay;
    private final Counter otherDeclined;

    public ReservationMetrics(MeterRegistry r) {
        confirmed = Counter.builder("reservations_confirmed_total").register(r);
        seatTaken = Counter.builder("reservations_declined_total").tag("reason", "seat-taken").register(r);
        limit = Counter.builder("reservations_declined_total").tag("reason", "per-user-limit").register(r);
        replay = Counter.builder("reservations_declined_total").tag("reason", "idempotent-replay").register(r);
        otherDeclined = Counter.builder("reservations_declined_total").tag("reason", "other").register(r);
    }

    public void confirmed() {
        confirmed.increment();
    }

    public void seatTaken() {
        seatTaken.increment();
    }

    public void limit() {
        limit.increment();
    }

    public void replay() {
        replay.increment();
    }

    public void other() {
        otherDeclined.increment();
    }
}
