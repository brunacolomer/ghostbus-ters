package com.example.ghostbusters.repository;

import com.example.ghostbusters.entity.BusPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BusPositionRepository extends JpaRepository<BusPosition, Long> {

    @Query(value = """
        SELECT DISTINCT st.trip_id AS tripId, st.stop_id AS stopId
        FROM transit.stop_times st
        JOIN transit.trips t ON t.trip_id = st.trip_id
        JOIN transit.routes r ON r.route_id = t.route_id
        JOIN transit.stops s ON s.stop_id = st.stop_id
        JOIN transit.bus_positions bp
          ON bp.route_id = r.route_short_name
         AND TRIM(UPPER(bp.trip_headsign)) = TRIM(UPPER(t.trip_headsign))
         AND bp.recorded_at > :since
        WHERE st.trip_id IN (:tripIds)
          AND ABS(EXTRACT(EPOCH FROM (st.arrival_time::interval - bp.recorded_at::time::interval))) <= :timeToleranceSeconds
          AND public.ST_DWithin(
                s.geom::public.geography,
                public.ST_SetSRID(public.ST_MakePoint(bp.longitude, bp.latitude), 4326)::public.geography,
                :thresholdMeters
              )
        """, nativeQuery = true)
    List<Object[]> findVisitedTripStopPairs(@Param("tripIds") Collection<String> tripIds,
                                            @Param("since") LocalDateTime since,
                                            @Param("thresholdMeters") double thresholdMeters,
                                            @Param("timeToleranceSeconds") double timeToleranceSeconds);
}