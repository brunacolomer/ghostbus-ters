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
    private Long tripId;

    @Column(name = "stop_id")
    private String stopId;

    @Column(name = "time_stamp")
    private LocalDateTime timeStamp;

    @Enumerated(EnumType.STRING)
    private StopVisitStatus status;
}