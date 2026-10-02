package com.example.seatreservation.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "user_show_locks", uniqueConstraints = @UniqueConstraint(name = "uk_user_show_lock", columnNames = {"show_id", "user_id"}))
public class UserShowLock {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    protected UserShowLock() {
    }

    public UserShowLock(Show show, String userId) {
        this.show = show;
        this.userId = userId;
    }
}
