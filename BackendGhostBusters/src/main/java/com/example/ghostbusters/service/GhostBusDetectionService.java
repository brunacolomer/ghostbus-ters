package com.example.ghostbusters.service;

import com.example.ghostbusters.entity.*;
import com.example.ghostbusters.repository.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GhostBusDetectionService {

    private static final double DISTANCE_THRESHOLD_METERS = 300.0;
    private static final double TIME_TOLERANCE_SECONDS = 1800.0;
    private static final int SECONDS_PER_DAY = 86400;

    private final StopTimeRepository stopTimeRepository;
    private final StopVisitRepository stopVisitRepository;
    private final BusPositionRepository busPositionRepository;

    public GhostBusDetectionService(StopTimeRepository stopTimeRepository,
                                    StopVisitRepository stopVisitRepository,
                                    BusPositionRepository busPositionRepository) {
        this.stopTimeRepository = stopTimeRepository;
        this.stopVisitRepository = stopVisitRepository;
        this.busPositionRepository = busPositionRepository;
    }

    @Scheduled(fixedRate = 120000)
    public void detectGhostBuses() {
        long start = System.currentTimeMillis();

        int nowSeconds = toSecondsSinceMidnight(LocalTime.now());
        int windowStartSeconds = Math.floorMod(nowSeconds - 30 * 60, SECONDS_PER_DAY);
        int windowEndSeconds = Math.floorMod(nowSeconds - 10 * 60, SECONDS_PER_DAY);
        System.out.println(">>> Ghost-bus check running for window " + windowStartSeconds + "s - " + windowEndSeconds + "s (of day)");

        List<StopTime> dueStopTimes = stopTimeRepository.findAll().stream()
                .filter(st -> {
                    Integer schedSeconds = parseGtfsSeconds(st.getArrivalTime());
                    return schedSeconds != null && isInWindow(schedSeconds, windowStartSeconds, windowEndSeconds);
                })
                .toList();
        System.out.println(">>> " + dueStopTimes.size() + " scheduled stops due for checking");

        Set<String> alreadyVisited = new HashSet<>();
        for (StopVisit v : stopVisitRepository.findByCheckedAtAfter(LocalDateTime.now().minusHours(2))) {
            if (v.getStatus() == StopVisitStatus.VISITED) {
                alreadyVisited.add(v.getTripId() + "|" + v.getStopId());
            }
        }
        System.out.println(">>> " + alreadyVisited.size() + " stops already confirmed visited, skipping those");

        List<StopTime> toCheck = dueStopTimes.stream()
                .filter(st -> !alreadyVisited.contains(st.getTripId() + "|" + st.getStopId()))
                .toList();
        System.out.println(">>> " + toCheck.size() + " stops actually need checking");

        if (toCheck.isEmpty()) {
            System.out.println(">>> Nothing to check, done in " + (System.currentTimeMillis() - start) + "ms");
            return;
        }

        Set<String> tripIds = toCheck.stream().map(StopTime::getTripId).collect(Collectors.toSet());
        List<Object[]> visitedPairs = busPositionRepository.findVisitedTripStopPairs(
                tripIds, LocalDateTime.now().minusMinutes(45), DISTANCE_THRESHOLD_METERS, TIME_TOLERANCE_SECONDS);

        Set<String> visitedKeys = new HashSet<>();
        for (Object[] row : visitedPairs) {
            visitedKeys.add(row[0] + "|" + row[1]);
        }
        System.out.println(">>> " + visitedKeys.size() + " (trip,stop) pairs confirmed visited via GPS proximity");

        List<StopVisit> toSave = new ArrayList<>();
        int visited = 0, missed = 0;

        for (StopTime st : toCheck) {
            String key = st.getTripId() + "|" + st.getStopId();

            StopVisit visit = new StopVisit();
            visit.setTripId(st.getTripId());
            visit.setStopId(st.getStopId());
            visit.setScheduledTime(st.getArrivalTime());
            visit.setCheckedAt(LocalDateTime.now());

            if (visitedKeys.contains(key)) {
                visit.setStatus(StopVisitStatus.VISITED);
                visited++;
            } else {
                visit.setStatus(StopVisitStatus.MISSED);
                missed++;
            }
            toSave.add(visit);
        }

        stopVisitRepository.saveAll(toSave);

        System.out.println(">>> Ghost-bus check done in " + (System.currentTimeMillis() - start) + "ms — "
                + visited + " visited, " + missed + " missed");
    }

    private int toSecondsSinceMidnight(LocalTime t) {
        return t.getHour() * 3600 + t.getMinute() * 60 + t.getSecond();
    }

    private boolean isInWindow(int scheduledSeconds, int windowStartSeconds, int windowEndSeconds) {
        if (windowStartSeconds <= windowEndSeconds) {
            return scheduledSeconds >= windowStartSeconds && scheduledSeconds <= windowEndSeconds;
        } else {
            // window itself wraps past midnight
            return scheduledSeconds >= windowStartSeconds || scheduledSeconds <= windowEndSeconds;
        }
    }

    private Integer parseGtfsSeconds(String gtfsTime) {
        if (gtfsTime == null) return null;
        try {
            String[] parts = gtfsTime.split(":");
            int hour = Integer.parseInt(parts[0]) % 24;
            int minute = Integer.parseInt(parts[1]);
            int second = Integer.parseInt(parts[2]);
            return hour * 3600 + minute * 60 + second;
        } catch (Exception e) {
            return null;
        }
    }
}