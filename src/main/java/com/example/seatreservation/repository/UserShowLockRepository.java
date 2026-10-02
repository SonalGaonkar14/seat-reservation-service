package com.example.seatreservation.repository;

import com.example.seatreservation.entity.UserShowLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface UserShowLockRepository extends JpaRepository<UserShowLock, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from UserShowLock l where l.show.id=:showId and l.userId=:userId")
    Optional<UserShowLock> findForUpdate(@Param("showId") UUID showId, @Param("userId") String userId);

    @Modifying
    @Query(value = "insert ignore into user_show_locks(id,show_id,user_id) values (UUID_TO_BIN(UUID()),:showId,:userId)", nativeQuery = true)
    int insertIfMissing(@Param("showId") UUID showId, @Param("userId") String userId);
}
