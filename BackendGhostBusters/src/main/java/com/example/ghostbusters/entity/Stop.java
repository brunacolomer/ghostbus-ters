package com.example.ghostbusters.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "stops", schema = "transit")
@Data
public class Stop {
    @Id
    @Column(name = "stop_id")
    private String stopId;

    @Column(name = "stop_name")
    private String stopName;

    @Column(name = "stop_lat")
    private Double stopLat;

    @Column(name = "stop_lon")
    private Double stopLon;
}