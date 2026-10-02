package com.example.seatreservation.repository;

import com.example.seatreservation.entity.Seat;
import com.example.seatreservation.entity.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByShowIdOrderBySeatNumber(UUID showId);

    long countByStatus(SeatStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
                SELECT s
                FROM Seat s
                WHERE s.show.id = :showId
                AND s.seatNumber IN :seatNumbers
                ORDER BY s.seatNumber
            """)
    List<Seat> findForUpdate(
            @Param("showId") UUID showId,
            @Param("seatNumbers") List<String> seatNumbers
    );
}