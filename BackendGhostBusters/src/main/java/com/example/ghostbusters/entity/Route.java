package com.example.ghostbusters.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "routes", schema = "transit")
@Data
public class Route {
    @Id
    @Column(name = "route_id")
    private String routeId;

    @Column(name = "route_short_name")
    private String routeShortName;

    @Column(name = "route_long_name")
    private String routeLongName;

    @Column(name = "route_type")
    private Integer routeType;

    @Column(name = "route_color")
    private String routeColor;
}