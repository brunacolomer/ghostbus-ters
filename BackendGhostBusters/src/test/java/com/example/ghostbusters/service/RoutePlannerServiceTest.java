package com.example.ghostbusters.service;

import com.example.ghostbusters.entity.Stop;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RoutePlannerServiceTest {
    private Map<String, List<String>> connections = Map.of();
    private final RoutePlannerService planner = new RoutePlannerService(null, null, null);

    private RoutePlannerService.Pattern pattern(String route, String... stops) {
        return new RoutePlannerService.Pattern(route, List.of(stops));
    }

    private RoutePlannerService.Plan plan(String from, String to, Map<String, Double> reliability,
                                           RoutePlannerService.Pattern... patterns) {
        Map<String, Stop> stops = new HashMap<>();
        for (var pattern : patterns) for (String id : pattern.stops()) {
            Stop stop = new Stop();
            stop.setStopId(id);
            stop.setStopName("Stop " + id);
            stops.put(id, stop);
        }
        for (String id : List.of(from, to)) {
            stops.computeIfAbsent(id, key -> {
                Stop stop = new Stop();
                stop.setStopId(key);
                stop.setStopName("Stop " + key);
                return stop;
            });
        }
        return planner.search(new RoutePlannerService.Graph(List.of(patterns), stops, connections), from, to, reliability);
    }

    @Test void transfersBetweenNearbyStopsAndMultipliesReliability() {
        connections = Map.of("D", List.of("E"));
        var result = plan("A", "Z", Map.of("8", .9, "11", .8),
                pattern("8", "A", "B", "C", "D"), pattern("11", "E", "F", "Z"));
        assertEquals(2, result.segments().size());
        assertEquals("D", result.segments().get(0).toStopId());
        assertEquals("E", result.segments().get(1).fromStopId());
        assertEquals("Stop E", result.segments().get(1).fromStopName());
        assertEquals(.72, result.reliability(), 1e-9);
    }

    @Test void prefersDirectRideOverShorterTransfer() {
        var result = plan("A", "Z", Map.of("direct", .5, "one", 1., "two", 1.),
                pattern("direct", "A", "B", "C", "Z"),
                pattern("one", "A", "X"), pattern("two", "X", "Z"));
        assertEquals("direct", result.segments().getFirst().routeId());
        assertEquals(1, result.segments().size());
    }

    @Test void prefersShorterRideThenReliabilityForEqualLength() {
        var result = plan("A", "Z", Map.of("long", .99, "short", .8, "reliable", .9),
                pattern("long", "A", "B", "C", "Z"),
                pattern("short", "A", "Z"), pattern("reliable", "A", "Z"));
        assertEquals("reliable", result.segments().getFirst().routeId());
    }

    @Test void doesNotTravelBackward() {
        assertThrows(ResponseStatusException.class,
                () -> plan("Z", "A", Map.of(), pattern("8", "A", "B", "Z")));
    }

    @Test void changingTripPatternsRequiresAnotherBoardingEvenOnSameRoute() {
        var result = plan("A", "Z", Map.of("8", .8),
                pattern("8", "A", "X"), pattern("8", "X", "Z"));
        assertEquals(2, result.segments().size());
        assertEquals(.64, result.reliability(), 1e-9);
    }

    @Test void doesNotChainWalkingTransfers() {
        connections = Map.of("D", List.of("E"), "E", List.of("F"));
        assertThrows(ResponseStatusException.class, () -> plan("A", "Z", Map.of(),
                pattern("8", "A", "D"), pattern("11", "F", "Z")));
    }

    @Test void retainsLoopOccurrencesAndUsesUnknownReliabilityFallback() {
        var result = plan("A", "Z", Map.of(), pattern("8", "A", "B", "A", "Z"));
        assertEquals(1, result.segments().size());
        assertEquals(.5, result.reliability());
        assertEquals("A", result.segments().getFirst().fromStopId());
    }

    @Test void sameStopNeedsNoRide() {
        var result = plan("A", "A", Map.of(), pattern("8", "A", "Z"));
        assertTrue(result.segments().isEmpty());
        assertEquals(1., result.reliability());
    }
    @Test void boardsAndArrivesAtNearbyStopsWithActualStopNames() {
        connections = Map.of("A", List.of("A2"), "Z", List.of("Z2"));
        var result = plan("A", "Z", Map.of("8", .9), pattern("8", "A2", "B", "Z2"));
        assertEquals("A", result.fromStopId());
        assertEquals("Z", result.toStopId());
        assertEquals("A2", result.segments().getFirst().fromStopId());
        assertEquals("Stop A2", result.segments().getFirst().fromStopName());
        assertEquals("Z2", result.segments().getFirst().toStopId());
        assertEquals("Stop Z2", result.segments().getFirst().toStopName());
    }

    @Test void oppositeSideOriginAllowsCorrectDirectionButNotBackwardTravel() {
        connections = Map.of("A", List.of("A2"));
        var result = plan("A", "Z", Map.of(),
                pattern("outbound", "Z", "A"), pattern("inbound", "A2", "Z"));
        assertEquals("inbound", result.segments().getFirst().routeId());
        assertThrows(ResponseStatusException.class,
                () -> plan("A", "Z", Map.of(), pattern("outbound", "Z", "A2")));
    }

    @Test void permitsTwoTransfersButRejectsThree() {
        var result = plan("A", "Z", Map.of(), pattern("1", "A", "B"),
                pattern("2", "B", "C"), pattern("3", "C", "Z"));
        assertEquals(3, result.segments().size());
        assertThrows(ResponseStatusException.class, () -> plan("A", "Z", Map.of(),
                pattern("1", "A", "B"), pattern("2", "B", "C"),
                pattern("3", "C", "D"), pattern("4", "D", "Z")));
    }

    @Test void nearbyDestinationNeedsNoRide() {
        connections = Map.of("Z", List.of("A"));
        assertTrue(plan("A", "Z", Map.of(), pattern("8", "A", "Z")).segments().isEmpty());
    }

    @Test void warmRequestsReuseConnectivityAndReliabilityWithoutDatabaseCalls() {
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var stops = org.mockito.Mockito.mock(com.example.ghostbusters.repository.StopRepository.class);
        var visits = org.mockito.Mockito.mock(com.example.ghostbusters.repository.StopVisitRepository.class);
        var service = new RoutePlannerService(jdbc, stops, visits);
        Stop a = new Stop(); a.setStopId("A"); a.setStopName("A");
        Stop z = new Stop(); z.setStopId("Z"); z.setStopName("Z");
        var graph = new RoutePlannerService.Graph(List.of(pattern("8", "A", "Z")),
                Map.of("A", a, "Z", z), Map.of());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "cached", graph);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "cachedReliability", Map.of("8", .9));
        assertEquals(.9, service.plan("A", "Z").reliability());
        assertEquals(.9, service.plan("A", "Z").reliability());
        org.mockito.Mockito.verifyNoInteractions(jdbc, stops, visits);
    }

    @Test void boundedSearchHandlesLargeCachedPatternSet() {
        var patterns = new ArrayList<RoutePlannerService.Pattern>();
        for (int route = 0; route < 2000; route++) {
            List<String> stops = new ArrayList<>();
            stops.add("A");
            for (int stop = 0; stop < 40; stop++) stops.add(route + "-" + stop);
            stops.add("Z");
            patterns.add(new RoutePlannerService.Pattern("r" + route, stops));
        }
        assertTimeout(java.time.Duration.ofSeconds(5), () -> {
            long started = System.nanoTime();
            var result = plan("A", "Z", Map.of(), patterns.toArray(RoutePlannerService.Pattern[]::new));
            assertEquals(1, result.segments().size());
            System.out.println("synthetic cached planner (2000 patterns): "
                    + (System.nanoTime() - started) / 1_000_000 + " ms");
        });
    }

}
