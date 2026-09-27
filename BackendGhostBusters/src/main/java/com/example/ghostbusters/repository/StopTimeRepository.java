package com.example.ghostbusters.repository;

import com.example.ghostbusters.entity.StopTime;
import com.example.ghostbusters.entity.StopTimeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface StopTimeRepository extends JpaRepository<StopTime, StopTimeId> {
    List<StopTime> findByTripIdOrderByStopSequenceAsc(String tripId);
    List<StopTime> findByTripIdInOrderByStopSequenceAsc(Collection<String> tripIds);

    @Query(value = """
        SELECT DISTINCT t.trip_id
        FROM transit.buses b
        JOIN transit.routes r ON b.route_id = r.route_short_name
        JOIN transit.trips t ON t.route_id = r.route_id
          AND TRIM(UPPER(b.trip_headsign)) = TRIM(UPPER(t.trip_headsign))
        """, nativeQuery = true)
    Set<String> findActiveTripIds();

    @Query(value = """
        SELECT * FROM transit.stop_times st
        WHERE (
          CASE WHEN :windowStartSeconds <= :windowEndSeconds THEN
            (
              (SPLIT_PART(st.arrival_time, ':', 1)::int % 24) * 3600
              + SPLIT_PART(st.arrival_time, ':', 2)::int * 60
              + SPLIT_PART(st.arrival_time, ':', 3)::int
            ) BETWEEN :windowStartSeconds AND :windowEndSeconds
          ELSE
            (
              (SPLIT_PART(st.arrival_time, ':', 1)::int % 24) * 3600
              + SPLIT_PART(st.arrival_time, ':', 2)::int * 60
              + SPLIT_PART(st.arrival_time, ':', 3)::int
            ) >= :windowStartSeconds
            OR
            (
              (SPLIT_PART(st.arrival_time, ':', 1)::int % 24) * 3600
              + SPLIT_PART(st.arrival_time, ':', 2)::int * 60
              + SPLIT_PART(st.arrival_time, ':', 3)::int
            ) <= :windowEndSeconds
          END
        )
        """, nativeQuery = true)
    List<StopTime> findDueInWindow(@Param("windowStartSeconds") int windowStartSeconds,
                                   @Param("windowEndSeconds") int windowEndSeconds);
}