package com.example.ghostbusters.entity;

import lombok.EqualsAndHashCode;
import java.io.Serializable;

@EqualsAndHashCode
public class StopTimeId implements Serializable {
    private String tripId;
    private Integer stopSequence;
}