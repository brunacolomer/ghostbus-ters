package com.example.ghostbusters.service;

import com.example.ghostbusters.entity.Stop;
import com.example.ghostbusters.repository.StopRepository;
import com.example.ghostbusters.repository.StopVisitRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class RoutePlannerService {
    private static final Logger log = LoggerFactory.getLogger(RoutePlannerService.class);
    private static final int MAX_RIDES = 3;
    private final JdbcTemplate jdbc;
    private final StopRepository stops;
    private final StopVisitRepository visits;
    private volatile Graph cached;
    private volatile Map<String, Double> cachedReliability;

    public RoutePlannerService(JdbcTemplate jdbc, StopRepository stops, StopVisitRepository visits) {
        this.jdbc = jdbc;
        this.stops = stops;
        this.visits = visits;
    }

    // Warm at startup and refresh in the background. Requests keep using the previous
    // snapshot during refresh; only the first cold request may need to wait.
    @Scheduled(fixedDelay = 3_600_000)
    public synchronized void refreshGraph() {
        List<Pattern> patterns = jdbc.query("""
                SELECT DISTINCT t.route_id, t.direction_id,
                       array_agg(st.stop_id ORDER BY st.stop_sequence) AS stops
                FROM transit.trips t JOIN transit.stop_times st ON st.trip_id = t.trip_id
                GROUP BY t.route_id, t.direction_id, t.trip_id
                """, (rs, n) -> {
            var array = rs.getArray("stops");
            try {
                return new Pattern(rs.getString("route_id"),
                        Arrays.stream((Object[]) array.getArray()).map(Object::toString).toList());
            } finally {
                array.free();
            }
        });
        Map<String, Stop> names = new HashMap<>();
        stops.findAll().forEach(stop -> names.put(stop.getStopId(), stop));
        Map<String, List<String>> nearby = new HashMap<>();
        // The geometry bounding box can use a geom index when available. 0.003 degrees
        // covers 250 metres in Miami; geography performs the exact distance check.
        jdbc.query("""
                SELECT a.stop_id AS origin, b.stop_id AS destination
                FROM transit.stops a JOIN transit.stops b
                  ON b.geom OPERATOR(public.&&) public.ST_Expand(a.geom, 0.003)
                 AND public.ST_DWithin(a.geom::public.geography, b.geom::public.geography, 250)
                WHERE a.stop_id <> b.stop_id
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                nearby.computeIfAbsent(rs.getString("origin"), ignored -> new ArrayList<>())
                        .add(rs.getString("destination")));
        cached = new Graph(patterns, names, nearby);
    }

    private Graph graph() {
        if (cached == null) {
            synchronized (this) {
                if (cached == null) refreshGraph();
            }
        }
        return cached;
    }

    @Scheduled(fixedDelay = 60_000)
    public synchronized void refreshReliability() {
        Map<String, Double> reliability = new HashMap<>();
        for (var row : visits.findRouteReliability(LocalDateTime.now(ZoneId.of("America/New_York")).minusHours(1))) {
            reliability.put((String) row.get("routeId"),
                    1.0 - ((Number) row.get("missRatePercent")).doubleValue() / 100.0);
        }
        cachedReliability = Map.copyOf(reliability);
    }

    public Plan plan(String fromStopId, String toStopId) {
        long started = System.nanoTime();
        try {
            if (fromStopId.isBlank() || toStopId.isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both stop IDs are required");
            Graph graph = graph();
            if (!graph.stops.containsKey(fromStopId) || !graph.stops.containsKey(toStopId))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown stop ID");
            if (cachedReliability == null) {
                synchronized (this) {
                    if (cachedReliability == null) refreshReliability();
                }
            }
            return search(graph, fromStopId, toStopId, cachedReliability);
        } finally {
            log.info("route planning: {} ms", (System.nanoTime() - started) / 1_000_000);
        }
    }

    // Breadth-first rounds minimize boardings. Within a round, minimize ridden stop
    // intervals, then maximize the reliability product. Never splice trip patterns.
    Plan search(Graph graph, String from, String to, Map<String, Double> reliability) {
        Set<String> destinations = new HashSet<>(graph.nearby.getOrDefault(to, List.of()));
        destinations.add(to);
        if (destinations.contains(from)) return new Plan(from, to, List.of(), 1.0);
        Set<Pattern> destinationPatterns = new HashSet<>();
        destinations.forEach(stop -> destinationPatterns.addAll(graph.serving.getOrDefault(stop, List.of())));
        Map<String, Label> frontier = Map.of(from, new Label(0, 1.0, List.of()));
        Set<String> reached = new HashSet<>();
        reached.add(from);
        for (int ride = 1; ride <= MAX_RIDES && !frontier.isEmpty(); ride++) {
            Map<String, Label> boarding = new HashMap<>(frontier);
            for (var entry : frontier.entrySet()) {
                for (String neighbor : graph.nearby.getOrDefault(entry.getKey(), List.of()))
                    keepBest(boarding, neighbor, entry.getValue());
            }
            Set<Pattern> candidates = new LinkedHashSet<>();
            boarding.keySet().forEach(stop -> candidates.addAll(graph.serving.getOrDefault(stop, List.of())));
            if (ride == MAX_RIDES) candidates.retainAll(destinationPatterns);
            Map<String, Label> arrivals = new HashMap<>();
            for (Pattern pattern : candidates) {
                double routeReliability = reliability.getOrDefault(pattern.routeId, 0.5);
                Label riding = null;
                String boardedAt = null;
                for (String stop : pattern.stops) {
                    if (riding != null) {
                        riding = new Label(riding.hops + 1, riding.reliability, riding.segments);
                        List<Segment> segments = new ArrayList<>(riding.segments);
                        segments.add(new Segment(pattern.routeId, boardedAt, graph.stops.get(boardedAt).getStopName(),
                                stop, graph.stops.get(stop).getStopName(), routeReliability));
                        if (!reached.contains(stop))
                            keepBest(arrivals, stop, new Label(riding.hops, riding.reliability, segments));
                    }
                    Label previous = boarding.get(stop);
                    if (previous != null) {
                        Label candidate = new Label(previous.hops, previous.reliability * routeReliability, previous.segments);
                        if (better(candidate, riding)) {
                            riding = candidate;
                            boardedAt = stop;
                        }
                    }
                }
            }
            Label destination = null;
            for (String stop : destinations) {
                Label candidate = arrivals.get(stop);
                if (candidate != null && better(candidate, destination)) destination = candidate;
            }
            if (destination != null) return new Plan(from, to, destination.segments, destination.reliability);
            reached.addAll(arrivals.keySet());
            frontier = arrivals;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No public-transport route found within two transfers");
    }

    private static boolean better(Label candidate, Label current) {
        return current == null || candidate.hops < current.hops
                || (candidate.hops == current.hops && candidate.reliability > current.reliability);
    }

    private static void keepBest(Map<String, Label> labels, String stop, Label candidate) {
        if (better(candidate, labels.get(stop))) labels.put(stop, candidate);
    }

    record Pattern(String routeId, List<String> stops) {}
    record Graph(List<Pattern> patterns, Map<String, Stop> stops,
                 Map<String, List<String>> nearby, Map<String, List<Pattern>> serving) {
        Graph(List<Pattern> patterns, Map<String, Stop> stops, Map<String, List<String>> nearby) {
            this(patterns, stops, nearby, index(patterns));
        }

        private static Map<String, List<Pattern>> index(List<Pattern> patterns) {
            Map<String, List<Pattern>> serving = new HashMap<>();
            for (Pattern pattern : patterns) {
                for (String stop : new HashSet<>(pattern.stops))
                    serving.computeIfAbsent(stop, ignored -> new ArrayList<>()).add(pattern);
            }
            return serving;
        }
    }
    private record Label(int hops, double reliability, List<Segment> segments) {}
    public record Segment(String routeId, String fromStopId, String fromStopName,
                          String toStopId, String toStopName, double reliability) {}
    public record Plan(String fromStopId, String toStopId, List<Segment> segments, double reliability) {}
}
