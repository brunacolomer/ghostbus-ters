package com.example.ghostbusters.service;

import com.example.ghostbusters.entity.*;
import com.example.ghostbusters.repository.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class GhostBusDetectionService {

    private static final int SECONDS_PER_DAY = 86400;
    private static final ZoneId EASTERN = ZoneId.of("America/New_York");

    private final StopTimeRepository stopTimeRepository;
    private final StopVisitRepository stopVisitRepository;

    public GhostBusDetectionService(StopTimeRepository stopTimeRepository,
                                    StopVisitRepository stopVisitRepository) {
        this.stopTimeRepository = stopTimeRepository;
        this.stopVisitRepository = stopVisitRepository;
    }

    @Scheduled(fixedRate = 120000)
    public void detectGhostBuses() {
        long start = System.currentTimeMillis();

        int nowSeconds = toSecondsSinceMidnight(LocalTime.now(EASTERN));
        int windowStartSeconds = Math.floorMod(nowSeconds - 30 * 60, SECONDS_PER_DAY);
        int windowEndSeconds = Math.floorMod(nowSeconds - 10 * 60, SECONDS_PER_DAY);
        System.out.println(">>> Ghost-bus check running for window " + windowStartSeconds + "s - " + windowEndSeconds + "s (of day)");

        List<StopTime> dueStopTimes = stopTimeRepository.findDueInWindow(windowStartSeconds, windowEndSeconds);
        System.out.println(">>> " + dueStopTimes.size() + " scheduled stops due for checking");

        Set<String> alreadyVisited = new HashSet<>();
        for (StopVisit v : stopVisitRepository.findByCheckedAtAfter(LocalDateTime.now(EASTERN).minusHours(2))) {
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

        Set<String> activeTripIds = stopTimeRepository.findActiveTripIds();
        System.out.println(">>> " + activeTripIds.size() + " active trip IDs found via route+headsign match");

        List<StopVisit> toSave = new ArrayList<>();
        int visited = 0, missed = 0;

        for (StopTime st : toCheck) {
            StopVisit visit = new StopVisit();
            visit.setTripId(st.getTripId());
            visit.setStopId(st.getStopId());
            visit.setScheduledTime(st.getArrivalTime());
            visit.setCheckedAt(LocalDateTime.now(EASTERN));

            if (activeTripIds.contains(st.getTripId())) {
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
}