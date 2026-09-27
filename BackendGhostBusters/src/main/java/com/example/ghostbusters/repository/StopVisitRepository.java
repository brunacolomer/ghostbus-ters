package com.example.ghostbusters.repository;

import com.example.ghostbusters.entity.StopVisit;
import com.example.ghostbusters.entity.StopVisitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StopVisitRepository extends JpaRepository<StopVisit, Long> {

    long countByStatus(StopVisitStatus status);

    long countByStatusAndCheckedAtAfter(StopVisitStatus status, LocalDateTime after);

    List<StopVisit> findByCheckedAtAfter(LocalDateTime after);

    @Query(value = """
        SELECT COUNT(*) FROM (
            SELECT trip_id
            FROM transit.stop_visits
            WHERE checked_at > :since
            GROUP BY trip_id
            HAVING COUNT(*) FILTER (WHERE status = 'VISITED') = 0
        ) AS truly_ghost
        """, nativeQuery = true)
    long countDistinctGhostTrips(@Param("since") LocalDateTime since);
}