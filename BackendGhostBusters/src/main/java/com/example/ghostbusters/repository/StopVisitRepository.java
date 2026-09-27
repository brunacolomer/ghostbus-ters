package com.example.ghostbusters.repository;

import com.example.ghostbusters.entity.StopVisit;
import com.example.ghostbusters.entity.StopVisitStatus;
import org.springframework.data.domain.Pageable;
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

    @Query(value = """
        SELECT sv.* FROM transit.stop_visits sv
        JOIN transit.trips t ON t.trip_id = sv.trip_id
        WHERE t.route_id = :routeId
          AND sv.status = :#{#status.name()}
          AND sv.checked_at > :since
        ORDER BY sv.checked_at DESC
        """, nativeQuery = true)
    List<StopVisit> findRouteActivity(@Param("routeId") String routeId,
                                      @Param("status") StopVisitStatus status,
                                      @Param("since") LocalDateTime since,
                                      Pageable pageable);
}