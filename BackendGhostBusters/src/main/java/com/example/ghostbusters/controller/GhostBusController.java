package com.example.ghostbusters.controller;

import com.example.ghostbusters.entity.GhostCountSettings;
import com.example.ghostbusters.repository.GhostCountSettingsRepository;
import com.example.ghostbusters.repository.StopVisitRepository;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/ghost-buses")
public class GhostBusController {

    private final StopVisitRepository stopVisitRepository;
    private final GhostCountSettingsRepository settingsRepository;

    public GhostBusController(StopVisitRepository stopVisitRepository,
                              GhostCountSettingsRepository settingsRepository) {
        this.stopVisitRepository = stopVisitRepository;
        this.settingsRepository = settingsRepository;
    }

    @GetMapping("/count")
    public Map<String, Long> getGhostBusCount() {
        LocalDateTime since = getOrCreateCountingSince();
        long count = stopVisitRepository.countDistinctGhostTrips(since);
        return Map.of("ghostBusCount", count);
    }

    @PostMapping("/reset")
    public Map<String, String> resetGhostBusCount() {
        GhostCountSettings settings = settingsRepository.findById(1).orElse(new GhostCountSettings());
        settings.setId(1);
        settings.setCountingSince(LocalDateTime.now(Clock.systemUTC()));
        settingsRepository.save(settings);
        return Map.of("status", "reset", "countingSince", settings.getCountingSince().toString());
    }

    private LocalDateTime getOrCreateCountingSince() {
        return settingsRepository.findById(1)
                .map(GhostCountSettings::getCountingSince)
                .orElseGet(() -> {
                    GhostCountSettings settings = new GhostCountSettings();
                    settings.setId(1);
                    settings.setCountingSince(LocalDateTime.now(Clock.systemUTC()));
                    settingsRepository.save(settings);
                    return settings.getCountingSince();
                });
    }
}