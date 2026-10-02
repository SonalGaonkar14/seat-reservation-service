package com.example.seatreservation.service;

import com.example.seatreservation.dto.*;
import com.example.seatreservation.entity.*;
import com.example.seatreservation.exception.DomainException;
import com.example.seatreservation.metrics.ReservationMetrics;
import com.example.seatreservation.repository.*;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.*;

@Service
public class ReservationService {
    private final ShowRepository shows;
    private final SeatRepository seats;
    private final ReservationRepository reservations;
    private final UserShowLockRepository userLocks;
    private final ReservationMetrics metrics;
    private final AtomicLong availableTotal = new AtomicLong();
    private final int userLimit;

    public ReservationService(ShowRepository shows, SeatRepository seats, ReservationRepository reservations, UserShowLockRepository userLocks, ReservationMetrics metrics, MeterRegistry registry, org.springframework.core.env.Environment env) {
        this.shows = shows;
        this.seats = seats;
        this.reservations = reservations;
        this.userLocks = userLocks;
        this.metrics = metrics;
        this.userLimit = Integer.parseInt(env.getProperty("app.per-user-limit", "4"));
        Gauge.builder("seats_available", availableTotal, AtomicLong::get).description("Currently available seats across all shows").register(registry);
    }

    @PostConstruct
    void initGauge() {
        availableTotal.set(seats.countByStatus(SeatStatus.AVAILABLE));
    }

    @Transactional
    public ShowResponse createShow(CreateShowRequest req) {
        List<String> normalized = req.seats().stream().map(String::trim).filter(s -> !s.isBlank()).map(String::toUpperCase).distinct().sorted().toList();
        if (normalized.size() != req.seats().size())
            throw new DomainException(400, "invalid_seats", "Seat names must be unique and non-blank");
        Show show = shows.save(new Show(req.name(), req.pricePaise(), normalized.size()));
        List<Seat> toSave = normalized.stream().map(s -> new Seat(show, s)).toList();
        seats.saveAll(toSave);
        availableTotal.addAndGet(normalized.size());
        return getShow(show.getId());
    }

    @Transactional
    public ReservationResponse reserve(UUID showId, String userId, ReserveRequest req) {
        List<String> wanted = normalize(req.seats());
        // Create/lock one row per (show,user). This serializes concurrent bookings by the same user only.
        userLocks.insertIfMissing(showId, userId);
        userLocks.findForUpdate(showId, userId).orElseThrow(() -> new DomainException(500, "lock_error", "Could not acquire user lock"));
        Optional<Reservation> existing = reservations.findByShowIdAndUserIdAndIdempotencyKey(showId, userId, req.idempotencyKey());
        if (existing.isPresent()) {
            Reservation r = existing.get();
            List<String> old = csvToList(r.getSeatsCsv());
            if (!old.equals(wanted)) {
                metrics.other();
                throw new DomainException(409, "idempotency_conflict", "Same idempotency key was already used with different seats");
            }
            metrics.replay();
            return toResponse(r);
        }
        Show show = shows.findById(showId).orElseThrow(() -> new DomainException(404, "show_not_found", "Show not found"));
        if (wanted.size() > userLimit || reservations.findByShowIdAndUserIdAndStatus(showId, userId, ReservationStatus.CONFIRMED).stream().mapToInt(Reservation::getSeatCount).sum() + wanted.size() > userLimit) {
            metrics.limit();
            throw new DomainException(409, "per-user-limit", "Per-user seat limit exceeded");
        }
        List<Seat> locked = seats.findForUpdate(showId, wanted);
        if (locked.size() != wanted.size()) {
            metrics.other();
            throw new DomainException(409, "seat-not-found", "One or more requested seats do not exist");
        }
        Map<String, Seat> byName = locked.stream().collect(Collectors.toMap(Seat::getSeatNumber, s -> s));
        for (String name : wanted) {
            Seat s = byName.get(name);
            if (s.getStatus() != SeatStatus.AVAILABLE) {
                metrics.seatTaken();
                throw new DomainException(409, "seat-taken", "Seat already taken: " + name);
            }
        }
        wanted.forEach(n -> byName.get(n).confirm());
        Reservation saved = reservations.save(new Reservation(show, userId, req.idempotencyKey(), Math.multiplyExact(show.getPricePaise(), wanted.size()), String.join(",", wanted)));
        availableTotal.addAndGet(-wanted.size());
        metrics.confirmed();
        return toResponse(saved);
    }

    @Transactional
    public CancelResponse cancel(UUID reservationId, String userId) {
        Reservation r = reservations.findByIdForUpdate(reservationId).orElseThrow(() -> new DomainException(404, "reservation_not_found", "Reservation not found"));
        if (!r.getUserId().equals(userId))
            throw new DomainException(403, "forbidden", "Only the reservation owner can cancel it");
        if (r.getStatus() == ReservationStatus.CANCELLED) return new CancelResponse(r.getId().toString(), "cancelled");
        List<String> names = csvToList(r.getSeatsCsv());
        List<Seat> locked = seats.findForUpdate(r.getShow().getId(), names);
        Map<String, Seat> byName = locked.stream().collect(Collectors.toMap(Seat::getSeatNumber, s -> s));
        for (String n : names) {
            Seat s = byName.get(n);
            if (s != null && s.getStatus() == SeatStatus.CONFIRMED) s.release();
        }
        r.cancel();
        availableTotal.addAndGet(names.size());
        return new CancelResponse(r.getId().toString(), "cancelled");
    }

    @Transactional(readOnly = true)
    public ShowResponse getShow(UUID showId) {
        Show show = shows.findById(showId).orElseThrow(() -> new DomainException(404, "show_not_found", "Show not found"));
        List<Seat> all = seats.findByShowIdOrderBySeatNumber(showId);
        long available = all.stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count();
        long confirmed = all.size() - available;
        return new ShowResponse(show.getId(), show.getName(), show.getPricePaise(), show.getTotalSeats(), available, 0, confirmed, all.stream().map(s -> new SeatView(s.getSeatNumber(), s.getStatus().name().toLowerCase())).toList());
    }

    private List<String> normalize(List<String> input) {
        if (input == null || input.isEmpty())
            throw new DomainException(400, "invalid_seats", "At least one seat is required");
        List<String> x = input.stream().map(s -> s.trim().toUpperCase()).sorted().toList();
        if (x.stream().distinct().count() != x.size())
            throw new DomainException(400, "duplicate_seat", "A seat may appear only once in a request");
        return x;
    }

    private List<String> csvToList(String csv) {
        return Arrays.asList(csv.split(","));
    }

    private ReservationResponse toResponse(Reservation r) {
        return new ReservationResponse(r.getId(), r.getShow().getId(), r.getUserId(), csvToList(r.getSeatsCsv()), r.getAmountPaise(), r.getStatus().name().toLowerCase());
    }
}
