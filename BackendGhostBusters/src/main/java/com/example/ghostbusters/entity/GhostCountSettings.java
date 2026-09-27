package com.example.ghostbusters.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ghost_count_settings", schema = "transit")
public class GhostCountSettings {

    @Id
    private Integer id;

    private LocalDateTime countingSince;
}