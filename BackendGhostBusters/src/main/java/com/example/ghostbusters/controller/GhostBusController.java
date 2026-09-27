package com.example.ghostbusters.controller;

import com.example.ghostbusters.entity.StopVisitStatus;
import com.example.ghostbusters.repository.StopVisitRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/ghost-buses")
public class GhostBusController {

    private final StopVisitRepository stopVisitRepository;

    public GhostBusController(StopVisitRepository stopVisitRepository) {
        this.stopVisitRepository = stopVisitRepository;
    }

    @GetMapping("/count")
    public Map<String, Long> getGhostBusCount() {
        long count = stopVisitRepository.countByStatusAndCheckedAtAfter(
                StopVisitStatus.MISSED,
                LocalDateTime.now().minusHours(1)
        );
        return Map.of("ghostBusCount", count);
    }
}