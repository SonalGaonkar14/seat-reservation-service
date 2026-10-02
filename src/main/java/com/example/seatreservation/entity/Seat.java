package com.example.seatreservation.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "seats", uniqueConstraints = @UniqueConstraint(name = "uk_show_seat", columnNames = {"show_id", "seat_number"}), indexes = @Index(name = "idx_seat_show_status", columnList = "show_id,status"))
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;
    @Column(name = "seat_number", nullable = false, length = 50)
    private String seatNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status = SeatStatus.AVAILABLE;

    protected Seat() {
    }

    public Seat(Show show, String seatNumber) {
        this.show = show;
        this.seatNumber = seatNumber;
    }

    public UUID getId() {
        return id;
    }

    public Show getShow() {
        return show;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public void confirm() {
        status = SeatStatus.CONFIRMED;
    }

    public void release() {
        status = SeatStatus.AVAILABLE;
    }
}
