package com.example.seatreservation.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "reservations", uniqueConstraints = @UniqueConstraint(name = "uk_idempotency", columnNames = {"show_id", "user_id", "idempotency_key"}), indexes = @Index(name = "idx_res_show_user_status", columnList = "show_id,user_id,status"))
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;
    @Column(name = "idempotency_key", nullable = false, length = 150)
    private String idempotencyKey;
    @Column(nullable = false)
    private long amountPaise;
    @Column(nullable = false)
    private int seatCount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;
    @Column(name = "seats_csv", nullable = false, length = 4000)
    private String seatsCsv;
    @Column(nullable = false)
    private Instant createdAt;

    protected Reservation() {
    }

    public Reservation(Show show, String userId, String key, long amount, String seatsCsv) {
        this.show = show;
        this.userId = userId;
        this.idempotencyKey = key;
        this.amountPaise = amount;
        this.seatCount = seatsCsv.split(",", -1).length;
        this.seatsCsv = seatsCsv;
        this.status = ReservationStatus.CONFIRMED;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Show getShow() {
        return show;
    }

    public String getUserId() {
        return userId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public int getSeatCount() {
        return seatCount;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public String getSeatsCsv() {
        return seatsCsv;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void cancel() {
        status = ReservationStatus.CANCELLED;
    }
}
