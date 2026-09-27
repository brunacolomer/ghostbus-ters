package com.example.ghostbusters.controller;

import com.example.ghostbusters.repository.StopVisitRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/routes")
public class RouteReliabilityController {

    private final StopVisitRepository stopVisitRepository;

    public RouteReliabilityController(StopVisitRepository stopVisitRepository) {
        this.stopVisitRepository = stopVisitRepository;
    }

    @GetMapping("/reliability")
    public List<Map<String, Object>> getRouteReliability() {
        return stopVisitRepository.findRouteReliability(LocalDateTime.now().minusHours(1));
    }
}