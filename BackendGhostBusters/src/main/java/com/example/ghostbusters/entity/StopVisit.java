package com.example.ghostbusters.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "stop_visits", schema = "transit")
@Data
public class StopVisit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id")
    private String tripId;

    @Column(name = "stop_id")
    private String stopId;

    @Column(name = "route_id")
    private String routeId;

    @Column(name = "scheduled_time")
    private String scheduledTime;

    @Enumerated(EnumType.STRING)
    private StopVisitStatus status;

    @Column(name = "matched_bus_id")
    private Long matchedBusId;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;
}