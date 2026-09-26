package com.example.ghostbusters.repository;

import com.example.ghostbusters.entity.ScheduledTrip;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledTripRepository extends JpaRepository<ScheduledTrip, String> {
    ScheduledTrip findFirstByRouteId(String routeId);
}