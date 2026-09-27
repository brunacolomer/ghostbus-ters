package com.example.ghostbusters.service;

import com.example.ghostbusters.controller.GhostBusController;
import com.example.ghostbusters.repository.GhostCountSettingsRepository;
import com.example.ghostbusters.repository.StopVisitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GhostBusCountingTest {
    private JdbcTemplate database() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE SCHEMA transit");
        return jdbc;
    }

    @Test void countUsesDistinctStopsVisitedPrecedenceThresholdsWindowAndService() throws Exception {
        var jdbc = database();
        jdbc.execute("CREATE TABLE transit.trips (trip_id varchar PRIMARY KEY, service_id varchar, route_id varchar)");
        jdbc.execute("CREATE TABLE transit.routes (route_id varchar PRIMARY KEY, route_type int)");
        jdbc.execute("INSERT INTO transit.routes VALUES ('bus', 3), ('metro', 1)");
        jdbc.execute("CREATE TABLE transit.stop_visits (trip_id varchar, stop_id varchar, status varchar, checked_at timestamp)");
        var now = LocalDateTime.of(2026, 9, 27, 12, 0);
        // Only 'three' (2/3 missed) and 'half' (2/4 missed) qualify.
        String[][] cases = {{"three", "MMV"}, {"half", "MMVV"}, {"few", "MM"},
                {"minority", "MMVVV"}, {"one", "MVV"}, {"override", "MMV"},
                {"metro", "MMM"}, {"inactive", "MMM"}, {"old", "MMM"}, {"future", "MMM"}};
        for (var row : cases) {
            jdbc.update("INSERT INTO transit.trips VALUES (?, ?, ?)", row[0], row[0].equals("inactive") ? "off" : "on",
                    row[0].equals("metro") ? "metro" : "bus");
            var time = row[0].equals("old") ? now.minusHours(2)
                    : row[0].equals("future") ? now.plusMinutes(1) : now.minusMinutes(10);
            for (int i = 0; i < row[1].length(); i++) {
                // Repeated polling must not increase the checked or missed stop counts.
                for (int duplicate = 0; duplicate < 2; duplicate++)
                    jdbc.update("INSERT INTO transit.stop_visits VALUES (?, ?, ?, ?)", row[0], "s" + i,
                            row[1].charAt(i) == 'V' ? "VISITED" : "MISSED", time);
            }
        }
        jdbc.update("INSERT INTO transit.stop_visits VALUES ('override', 's0', 'VISITED', ?)", now.minusMinutes(5));
        String sql = StopVisitRepository.class
                .getMethod("countDistinctGhostTrips", LocalDateTime.class, LocalDateTime.class, List.class, boolean.class)
                .getAnnotation(Query.class).value();
        Long count = new NamedParameterJdbcTemplate(jdbc).queryForObject(sql,
                Map.of("since", now.minusHours(1), "now", now, "activeServiceIds", List.of("on"), "filterServices", true), Long.class);
        assertEquals(2L, count);
        assertEquals(3L, new NamedParameterJdbcTemplate(jdbc).queryForObject(sql,
                Map.of("since", now.minusHours(1), "now", now, "activeServiceIds", List.of(""),
                        "filterServices", false), Long.class));
    }

    @Test void calendarHonorsWeekdayDateRangeAndExceptions() {
        var jdbc = database();
        jdbc.execute("""
                CREATE TABLE transit.calendar (service_id varchar, monday int, tuesday int, wednesday int,
                    thursday int, friday int, saturday int, sunday int, start_date varchar, end_date varchar)
                """);
        jdbc.execute("CREATE TABLE transit.calendar_dates (service_id varchar, date varchar, exception_type int)");
        jdbc.execute("""
                INSERT INTO transit.calendar VALUES
                ('regular', 0,0,0,0,0,0,1, '20260901', '20260930'),
                ('removed', 0,0,0,0,0,0,1, '20260901', '20260930'),
                ('weekday', 1,1,1,1,1,0,0, '20260901', '20260930'),
                ('expired', 1,1,1,1,1,1,1, '20260801', '20260831')
                """);
        jdbc.execute("INSERT INTO transit.calendar_dates VALUES ('removed', '20260927', 2), ('added', '2026-09-27', 1)");
        var service = new RouteScheduleService(jdbc);
        assertEquals(java.util.Set.of("regular", "added"), java.util.Set.copyOf(service.activeServiceIds(LocalDate.of(2026, 9, 27))));
        jdbc.execute("DROP TABLE transit.calendar");
        assertEquals(List.of("added"), service.activeServiceIds(LocalDate.of(2026, 9, 27)));
        jdbc.execute("DROP TABLE transit.calendar_dates");
        assertTrue(service.activeServiceIds(LocalDate.of(2026, 9, 27)).isEmpty());
    }

    @Test void countDefaultsToOneHourWithoutInitializingAReset() {
        var visits = mock(StopVisitRepository.class);
        var settings = mock(GhostCountSettingsRepository.class);
        var schedules = mock(RouteScheduleService.class);
        when(schedules.activeServiceIds(any())).thenReturn(List.of("on"));
        when(visits.countDistinctGhostTrips(any(), any(), any(), eq(true))).thenAnswer(invocation -> {
            LocalDateTime since = invocation.getArgument(0);
            LocalDateTime now = invocation.getArgument(1);
            assertEquals(now.minusHours(1), since);
            return 2L;
        });
        assertEquals(Map.of("ghostBusCount", 2L), new GhostBusController(visits, settings, schedules).getGhostBusCount());
        verify(settings, never()).save(any());
    }
    @Test void emptyOrUnavailableServicesStillCountBusTrips() {
        var visits = mock(StopVisitRepository.class);
        var settings = mock(GhostCountSettingsRepository.class);
        var schedules = mock(RouteScheduleService.class);
        when(schedules.activeServiceIds(any())).thenReturn(List.of())
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("unavailable"));
        when(visits.countDistinctGhostTrips(any(), any(), eq(List.of("")), eq(false))).thenReturn(4L);
        var controller = new GhostBusController(visits, settings, schedules);
        assertEquals(4L, controller.getGhostBusCount().get("ghostBusCount"));
        assertEquals(4L, controller.getGhostBusCount().get("ghostBusCount"));
    }
    @Test void detectorFiltersKnownServicesAndContinuesWhenServicesAreUnresolved() {
        var times = mock(com.example.ghostbusters.repository.StopTimeRepository.class);
        var visits = mock(StopVisitRepository.class);
        var positions = mock(com.example.ghostbusters.repository.BusPositionRepository.class);
        var schedules = mock(RouteScheduleService.class);
        when(schedules.activeServiceIds(any())).thenReturn(List.of("on"), List.of())
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("unavailable"));
        var stop = new com.example.ghostbusters.entity.StopTime();
        stop.setTripId("bus-trip");
        stop.setStopId("stop");
        stop.setStopSequence(1);
        stop.setArrivalTime("12:00:00");
        when(times.findDueInWindow(anyInt(), anyInt(), anyList(), anyBoolean())).thenReturn(List.of(stop));
        var detector = new GhostBusDetectionService(times, visits, positions, schedules);
        detector.detectGhostBuses();
        verify(times).findDueInWindow(anyInt(), anyInt(), eq(List.of("on")), eq(true));
        clearInvocations(times, visits);
        detector.detectGhostBuses();
        detector.detectGhostBuses();
        verify(times, times(2)).findDueInWindow(anyInt(), anyInt(), eq(List.of("")), eq(false));
        verify(visits, times(2)).saveAll(argThat(rows ->
                rows.iterator().next().getStatus() == com.example.ghostbusters.entity.StopVisitStatus.MISSED));
        verify(positions, times(3)).findVisitedTripStopPairs(any(), any(), any(), any(), any(), eq(150.0), eq(300.0));
    }
}
