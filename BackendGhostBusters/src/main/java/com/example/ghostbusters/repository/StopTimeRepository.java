package com.example.ghostbusters.repository;

import org.springframework.data.jpa.repository.Query;
import java.util.Set;
import com.example.ghostbusters.entity.StopTime;
import com.example.ghostbusters.entity.StopTimeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.time.LocalTime;

public interface StopTimeRepository extends JpaRepository<StopTime, StopTimeId> {
    // Match the detector's GTFS hour wrapping and ignore malformed arrival times.
    @Query(value = """
        SELECT * FROM transit.stop_times
        WHERE CASE WHEN arrival_time ~ '^[0-9]{1,9}:[0-5][0-9]:[0-5][0-9]$'
            THEN make_time(mod(CAST(split_part(arrival_time, ':', 1) AS integer), 24),
                           CAST(split_part(arrival_time, ':', 2) AS integer),
                           CAST(split_part(arrival_time, ':', 3) AS double precision))
            ELSE NULL END BETWEEN :windowStart AND :windowEnd
        """, nativeQuery = true)
    List<StopTime> findDueStopTimes(LocalTime windowStart, LocalTime windowEnd);

    List<StopTime> findByTripIdOrderByStopSequenceAsc(String tripId);
    List<StopTime> findByTripIdInOrderByStopSequenceAsc(Collection<String> tripIds);

    @Query(value = """
        SELECT DISTINCT ON (b.bus_id) t.trip_id
        FROM transit.buses b
        JOIN transit.routes r ON b.route_id = r.route_short_name
        JOIN transit.trips t ON t.route_id = r.route_id
          AND TRIM(UPPER(b.trip_headsign)) = TRIM(UPPER(t.trip_headsign))
        JOIN (
            SELECT trip_id, MIN(arrival_time) AS first_arrival
            FROM transit.stop_times
            GROUP BY trip_id
        ) st ON st.trip_id = t.trip_id
        ORDER BY b.bus_id,
          ABS(EXTRACT(EPOCH FROM (
            (st.first_arrival::interval) - (b.last_updated::time)::interval
          )))
        """, nativeQuery = true)
    Set<String> findActiveTripIds();
}
