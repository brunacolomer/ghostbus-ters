package com.example.ghostbusters.repository;

import com.example.ghostbusters.entity.GhostCountSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GhostCountSettingsRepository extends JpaRepository<GhostCountSettings, Integer> {
}