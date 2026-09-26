package com.example.ghostbusters.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "buses", schema = "transit")
@Data
public class Bus {
        @Id
        @Column(name = "bus_id")
        private Long busId;

        @Column(name = "route_id")
        private String routeId;

        @Column(name = "trip_id")
        private String tripId;

        @Column(name = "latitude")
        private Double latitude;

        @Column(name = "longitude")
        private Double longitude;

        @Column(name = "on_time")
        private Double onTime;

        @Column(name = "speed")
        private Double speed;

        @Column(name = "last_updated")
        private LocalDateTime lastUpdated;
}