package com.example.ghostbusters.controller;

import com.example.ghostbusters.repository.RouteRepository;
import com.example.ghostbusters.service.RouteScheduleService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/routes")
public class RouteScheduleController {
    private final RouteRepository routes;
    private final RouteScheduleService schedules;

    public RouteScheduleController(RouteRepository routes, RouteScheduleService schedules) {
        this.routes = routes;
        this.schedules = schedules;
    }

    @GetMapping("/{routeId}/schedule")
    public RouteScheduleService.Schedule schedule(@PathVariable String routeId, @RequestParam String headsign) {
        if (!routes.existsById(routeId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (headsign.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destination required");
        return schedules.getSchedule(routeId, headsign);
    }
}
