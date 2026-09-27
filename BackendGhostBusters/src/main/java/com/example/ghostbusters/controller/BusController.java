package com.example.ghostbusters.controller;

import com.example.ghostbusters.repository.BusRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/buses")
public class BusController {

    private final BusRepository busRepository;

    public BusController(BusRepository busRepository) {
        this.busRepository = busRepository;
    }

    @GetMapping
    public List<BusPositionResponse> getBusPositions() {
        return busRepository.findAll().stream().map(bus ->
                // The live RouteID is the public line number, not the GTFS route primary key.
                new BusPositionResponse(bus.getBusId(), bus.getLatitude(), bus.getLongitude(),
                        bus.getRouteId(), bus.getRouteId(), bus.getTripId(),
                        null, bus.getTripHeadsign(), bus.getLastUpdated())
        ).toList();
    }

    public record BusPositionResponse(Long busId, Double latitude, Double longitude,
                                      String routeId, String line, String tripId,
                                      Integer directionId, String headsign,
                                      LocalDateTime lastUpdated) {}
}
