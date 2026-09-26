package com.example.ghostbusters.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "trips", schema = "transit")
@Data
public class ScheduledTrip {
    @Id
    @Column(name = "trip_id")
    private String tripId;

    @Column(name = "route_id")
    private String routeId;

    @Column(name = "service_id")
    private String serviceId;

    @Column(name = "trip_headsign")
    private String tripHeadsign;

    @Column(name = "direction_id")
    private Integer directionId;

    @Column(name = "shape_id")
    private String shapeId;
}