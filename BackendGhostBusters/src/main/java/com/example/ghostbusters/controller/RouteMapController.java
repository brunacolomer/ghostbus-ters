package com.example.ghostbusters.controller;

import com.example.ghostbusters.controller.dto.RouteMapResponse;
import com.example.ghostbusters.entity.*;
import com.example.ghostbusters.repository.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/routes")
public class RouteMapController {

    private final RouteRepository routeRepository;
    private final ScheduledTripRepository scheduledTripRepository;
    private final ShapeRepository shapeRepository;
    private final StopTimeRepository stopTimeRepository;
    private final StopRepository stopRepository;

    public RouteMapController(RouteRepository routeRepository, ScheduledTripRepository scheduledTripRepository,
                              ShapeRepository shapeRepository, StopTimeRepository stopTimeRepository,
                              StopRepository stopRepository) {
        this.routeRepository = routeRepository;
        this.scheduledTripRepository = scheduledTripRepository;
        this.shapeRepository = shapeRepository;
        this.stopTimeRepository = stopTimeRepository;
        this.stopRepository = stopRepository;
    }

    @GetMapping
    public List<RouteMapResponse> getRoutesForMap() {
        long start = System.currentTimeMillis();

        // 1. Fetch everything in bulk, up front
        List<Route> allRoutes = routeRepository.findAll();
        List<ScheduledTrip> allTrips = scheduledTripRepository.findAll();
        System.out.println(">>> Loaded " + allRoutes.size() + " routes, " + allTrips.size() + " trips");

        // 2. Pick ONE representative trip per route, in memory (no DB call per route)
        Map<String, ScheduledTrip> sampleTripByRoute = new HashMap<>();
        for (ScheduledTrip trip : allTrips) {
            sampleTripByRoute.putIfAbsent(trip.getRouteId(), trip);
        }

        // 3. Batch-fetch every shape's points in ONE query
        Set<String> shapeIds = sampleTripByRoute.values().stream()
                .map(ScheduledTrip::getShapeId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, List<double[]>> coordsByShapeId = shapeRepository
                .findByShapeIdInOrderByShapePtSequenceAsc(shapeIds).stream()
                .collect(Collectors.groupingBy(Shape::getShapeId,
                        Collectors.mapping(pt -> new double[]{pt.getShapePtLon(), pt.getShapePtLat()}, Collectors.toList())));
        System.out.println(">>> Loaded shape points for " + coordsByShapeId.size() + " shapes");

        // 4. Batch-fetch every trip's stop_times in ONE query
        Set<String> tripIds = sampleTripByRoute.values().stream()
                .map(ScheduledTrip::getTripId).collect(Collectors.toSet());
        Map<String, List<StopTime>> stopTimesByTripId = stopTimeRepository
                .findByTripIdInOrderByStopSequenceAsc(tripIds).stream()
                .collect(Collectors.groupingBy(StopTime::getTripId));
        System.out.println(">>> Loaded stop_times for " + stopTimesByTripId.size() + " trips");

        // 5. Batch-fetch every stop referenced, in ONE query
        Set<String> stopIds = stopTimesByTripId.values().stream()
                .flatMap(List::stream).map(StopTime::getStopId).collect(Collectors.toSet());
        Map<String, Stop> stopsById = stopRepository.findAllById(stopIds).stream()
                .collect(Collectors.toMap(Stop::getStopId, s -> s));
        System.out.println(">>> Loaded " + stopsById.size() + " stops");

        // 6. Assemble the final response, all in memory — no more DB calls
        List<RouteMapResponse> results = allRoutes.stream().map(route -> {
            ScheduledTrip sampleTrip = sampleTripByRoute.get(route.getRouteId());
            if (sampleTrip == null) return null;

            List<double[]> coordinates = coordsByShapeId.getOrDefault(sampleTrip.getShapeId(), List.of());

            List<RouteMapResponse.StopDto> stops = stopTimesByTripId
                    .getOrDefault(sampleTrip.getTripId(), List.of()).stream()
                    .map(st -> stopsById.get(st.getStopId()))
                    .filter(Objects::nonNull)
                    .map(s -> new RouteMapResponse.StopDto(s.getStopId(), s.getStopName(),
                            new double[]{s.getStopLon(), s.getStopLat()}))
                    .collect(Collectors.toList());

            String color = route.getRouteColor() != null ? "#" + route.getRouteColor() : "#3b82f6";

            return new RouteMapResponse(route.getRouteId(), route.getRouteShortName(), route.getRouteLongName(),
                    sampleTrip.getTripHeadsign(), color, coordinates, stops);
        }).filter(Objects::nonNull).collect(Collectors.toList());

        System.out.println(">>> DONE in " + (System.currentTimeMillis() - start) + "ms — returning " + results.size() + " routes");
        return results;
    }
}