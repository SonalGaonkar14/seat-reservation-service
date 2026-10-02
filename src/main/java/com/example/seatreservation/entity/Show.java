package com.example.seatreservation.entity;

import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "shows")
public class Show {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true, length = 100)
    private String name;
    @Column(nullable = false)
    private long pricePaise;
    @Column(nullable = false)
    private int totalSeats;

    protected Show() {
    }

    public Show(String name, long pricePaise, int totalSeats) {
        this.name = name;
        this.pricePaise = pricePaise;
        this.totalSeats = totalSeats;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getPricePaise() {
        return pricePaise;
    }

    public int getTotalSeats() {
        return totalSeats;
    }
}
