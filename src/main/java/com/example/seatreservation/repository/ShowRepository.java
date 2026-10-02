package com.example.seatreservation.repository;

import com.example.seatreservation.entity.Show;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface ShowRepository extends JpaRepository<Show, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Show s where s.id=:id")
    Optional<Show> findByIdForUpdate(UUID id);
}
