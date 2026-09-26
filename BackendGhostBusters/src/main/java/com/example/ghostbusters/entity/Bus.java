package com.example.ghostbusters.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "buses", schema = "transit")
@Data
public class Bus {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "current_route_id")
        private String currentRouteId;

        @Column(name = "current_latitude")
        private Double currentLatitude;

        @Column(name = "current_longitude")
        private Double currentLongitude;

        @Column(name = "start_time")
        private LocalDateTime startTime;

        @Column(name = "end_time")
        private LocalDateTime endTime;
}