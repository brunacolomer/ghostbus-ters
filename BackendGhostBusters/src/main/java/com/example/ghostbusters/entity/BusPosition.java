package com.example.ghostbusters.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "bus_positions", schema = "transit")
public class BusPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long busId;
    private String routeId;
    private String tripHeadsign;
    private Double latitude;
    private Double longitude;
    private LocalDateTime recordedAt;
}