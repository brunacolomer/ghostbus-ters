package com.example.ghostbusters.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@IdClass(ShapePointId.class)
@Table(name = "shapes", schema = "transit")
@Data
public class Shape {
    @Id
    @Column(name = "shape_id")
    private String shapeId;

    @Id
    @Column(name = "shape_pt_sequence")
    private Integer shapePtSequence;

    @Column(name = "shape_pt_lat")
    private Double shapePtLat;

    @Column(name = "shape_pt_lon")
    private Double shapePtLon;
}
