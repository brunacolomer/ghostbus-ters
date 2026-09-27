package com.example.ghostbusters.repository;

import com.example.ghostbusters.entity.ScheduledTrip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ScheduledTripRepository extends JpaRepository<ScheduledTrip, String> {
    @Query(value = "SELECT DISTINCT ON (route_id) * FROM transit.trips ORDER BY route_id", nativeQuery = true)
    List<ScheduledTrip> findRepresentativeTrips();

    ScheduledTrip findFirstByRouteId(String routeId);
}
