package com.example.ghostbusters.controller;

import com.example.ghostbusters.entity.StopVisitStatus;
import com.example.ghostbusters.repository.RouteRepository;
import com.example.ghostbusters.repository.StopVisitRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/routes")
public class RouteActivityController {
    private final StopVisitRepository visits;
    private final RouteRepository routes;

    public RouteActivityController(StopVisitRepository visits, RouteRepository routes) {
        this.visits = visits;
        this.routes = routes;
    }

    @GetMapping("/{routeId}/activity")
    public Activity getActivity(@PathVariable String routeId) {
        if (!routes.existsById(routeId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var checkedAt = LocalDateTime.now();
        var records = visits.findRouteActivity(routeId, StopVisitStatus.MISSED,
                checkedAt.minusHours(1), PageRequest.of(0, 101));
        return new Activity(checkedAt, records.size() > 100, records.stream().limit(100)
                .map(v -> new MissedArrival(v.getId(), v.getStopId(), v.getTripId(),
                        v.getScheduledTime(), v.getCheckedAt())).toList());
    }

    public record Activity(LocalDateTime checkedAt, boolean truncated, List<MissedArrival> missedArrivals) {}
    public record MissedArrival(Long id, String stopId, String tripId, String scheduledTime, LocalDateTime checkedAt) {}
}
