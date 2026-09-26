package com.example.ghostbusters.service;

import com.example.ghostbusters.entity.*;
import com.example.ghostbusters.repository.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class GhostBusDetectionService {

    private final StopTimeRepository stopTimeRepository;
    private final StopVisitRepository stopVisitRepository;

    public GhostBusDetectionService(StopTimeRepository stopTimeRepository,
                                    StopVisitRepository stopVisitRepository) {
        this.stopTimeRepository = stopTimeRepository;
        this.stopVisitRepository = stopVisitRepository;
    }

    @Scheduled(fixedRate = 120000) // every 2 minutes
    public void detectGhostBuses() {
        long start = System.currentTimeMillis();

        LocalTime now = LocalTime.now();
        LocalTime windowStart = now.minusMinutes(20);
        LocalTime windowEnd = now.minusMinutes(10);
        System.out.println(">>> Ghost-bus check running for window " + windowStart + " - " + windowEnd);

        // 1. Get all scheduled stops due in this window
        List<StopTime> dueStopTimes = stopTimeRepository.findAll().stream()
                .filter(st -> {
                    LocalTime schedTime = parseGtfsTime(st.getArrivalTime());
                    return schedTime != null && !schedTime.isBefore(windowStart) && !schedTime.isAfter(windowEnd);
                })
                .toList();
        System.out.println(">>> " + dueStopTimes.size() + " scheduled stops due for checking");

        // 2. Batch-fetch every (trip_id, stop_id) pair already checked in the last 2 hours — ONE query
        Set<String> alreadyChecked = new HashSet<>();
        for (StopVisit v : stopVisitRepository.findByCheckedAtAfter(LocalDateTime.now().minusHours(2))) {
            alreadyChecked.add(v.getTripId() + "|" + v.getStopId());
        }
        System.out.println(">>> " + alreadyChecked.size() + " stop checks already on record");

        // 3. Get every currently-active trip_id via route+headsign match — ONE query
        Set<String> activeTripIds = stopTimeRepository.findActiveTripIds();
        System.out.println(">>> " + activeTripIds.size() + " active trip IDs found via route+headsign match");

        // 4. Decide VISITED/MISSED for each due stop, all in memory — no DB calls in this loop
        List<StopVisit> toSave = new ArrayList<>();
        int visited = 0, missed = 0, skipped = 0;

        for (StopTime st : dueStopTimes) {
            String key = st.getTripId() + "|" + st.getStopId();
            if (alreadyChecked.contains(key)) { skipped++; continue; }

            StopVisit visit = new StopVisit();
            visit.setTripId(st.getTripId());
            visit.setStopId(st.getStopId());
            visit.setScheduledTime(st.getArrivalTime());
            visit.setCheckedAt(LocalDateTime.now());

            if (activeTripIds.contains(st.getTripId())) {
                visit.setStatus(StopVisitStatus.VISITED);
                visited++;
            } else {
                visit.setStatus(StopVisitStatus.MISSED);
                missed++;
            }
            toSave.add(visit);
        }

        // 5. Save everything in one batch call instead of one-by-one
        stopVisitRepository.saveAll(toSave);

        System.out.println(">>> Ghost-bus check done in " + (System.currentTimeMillis() - start) + "ms — "
                + visited + " visited, " + missed + " missed, " + skipped + " already checked");
    }

    private LocalTime parseGtfsTime(String gtfsTime) {
        if (gtfsTime == null) return null;
        try {
            String[] parts = gtfsTime.split(":");
            int hour = Integer.parseInt(parts[0]) % 24;
            int minute = Integer.parseInt(parts[1]);
            int second = Integer.parseInt(parts[2]);
            return LocalTime.of(hour, minute, second);
        } catch (Exception e) {
            return null;
        }
    }
}