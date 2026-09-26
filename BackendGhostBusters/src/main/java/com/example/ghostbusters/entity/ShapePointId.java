package com.example.ghostbusters.entity;

import lombok.EqualsAndHashCode;
import java.io.Serializable;

@EqualsAndHashCode
public class ShapePointId implements Serializable {
    private String shapeId;
    private Integer shapePtSequence;
}