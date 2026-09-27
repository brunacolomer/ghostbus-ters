package com.example.ghostbusters.service;

import com.example.ghostbusters.entity.StopTime;
import com.example.ghostbusters.entity.StopVisit;
import com.example.ghostbusters.entity.StopVisitStatus;
import com.example.ghostbusters.repository.StopTimeRepository;
import com.example.ghostbusters.repository.StopVisitRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GhostBusDetectionServiceTests {
    @Test
    void filtersInDatabaseSkipsCheckedStopsAndSavesBoundedBatches() {
        var times = mock(StopTimeRepository.class);
        var visits = mock(StopVisitRepository.class);
        List<StopTime> due = new ArrayList<>();
        for (int i = 0; i < 502; i++) {
            StopTime stop = new StopTime();
            stop.setTripId(i % 2 == 0 ? "active" : "missing");
            stop.setStopId("stop" + i);
            stop.setArrivalTime("12:00:00");
            due.add(stop);
        }
        when(times.findDueStopTimes(any(), any())).thenReturn(due);
        when(times.findActiveTripIds()).thenReturn(Set.of("active"));
        when(visits.findCheckedStopKeysAfter(any())).thenReturn(Set.of("active|stop0"));
        List<Integer> batchSizes = new ArrayList<>();
        List<StopVisit> saved = new ArrayList<>();
        when(visits.saveAll(any())).thenAnswer(invocation -> {
            List<StopVisit> batch = invocation.getArgument(0);
            batchSizes.add(batch.size());
            saved.addAll(batch);
            return List.of();
        });

        new GhostBusDetectionService(times, visits).detectGhostBuses();

        assertEquals(List.of(250, 250, 1), batchSizes);
        assertEquals(250, saved.stream().filter(v -> v.getStatus() == StopVisitStatus.VISITED).count());
        assertEquals(251, saved.stream().filter(v -> v.getStatus() == StopVisitStatus.MISSED).count());
        verify(times, never()).findAll();
        verify(visits, never()).findByCheckedAtAfter(any());
    }
}
