package com.example.ghostbusters.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@IdClass(StopTimeId.class)
@Table(name = "stop_times", schema = "transit")
@Data
public class StopTime {
    @Id
    @Column(name = "trip_id")
    private String tripId;

    @Id
    @Column(name = "stop_sequence")
    private Integer stopSequence;

    @Column(name = "stop_id")
    private String stopId;

    @Column(name = "arrival_time")
    private String arrivalTime;

    @Column(name = "departure_time")
    private String departureTime;
}