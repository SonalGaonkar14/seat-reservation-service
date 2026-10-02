package com.example.seatreservation.repository;

import com.example.seatreservation.entity.Reservation;
import com.example.seatreservation.entity.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByShowIdAndUserIdAndIdempotencyKey(
            UUID showId,
            String userId,
            String idempotencyKey
    );

    List<Reservation> findByShowIdAndUserIdAndStatus(
            UUID showId,
            String userId,
            ReservationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
                SELECT r
                FROM Reservation r
                WHERE r.id = :reservationId
            """)
    Optional<Reservation> findByIdForUpdate(
            @Param("reservationId") UUID reservationId
    );
}