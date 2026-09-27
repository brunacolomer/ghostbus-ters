package com.example.ghostbusters.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RouteScheduleServiceTest {
    private final RouteScheduleService service = new RouteScheduleService(mock(JdbcTemplate.class));
    private final RouteScheduleService.CalendarData calendar = new RouteScheduleService.CalendarData(
        List.of(
            new String[]{"1", "1", "1", "1", "1", "1", "0", "0", "20260720", "20261122"},
            new String[]{"2", "0", "0", "0", "0", "0", "1", "0", "20260720", "20261122"},
            new String[]{"3", "0", "0", "0", "0", "0", "0", "1", "20260720", "20261122"},
            new String[]{"11", "1", "1", "1", "1", "1", "0", "0", "20231113", "20261231"}),
        List.of(new String[]{"3", "20260907", "1"}, new String[]{"1", "20260907", "2"}));

    @Test
    void readsCalendarsFromDatabaseOnEveryRequest() {
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:schedule_" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var db = new JdbcTemplate(source);
        db.execute("create schema transit");
        db.execute("create table transit.calendar(service_id varchar, monday boolean, tuesday boolean, wednesday boolean, thursday boolean, friday boolean, saturday boolean, sunday boolean, start_date date, end_date date)");
        db.execute("create table transit.calendar_dates(service_id varchar, date date, exception_type integer)");
        db.execute("create table transit.trips(trip_id varchar, route_id varchar, trip_headsign varchar, service_id varchar)");
        db.execute("create table transit.stop_times(trip_id varchar, stop_id varchar, arrival_time varchar)");
        db.execute("insert into transit.calendar values ('daily',true,true,true,true,true,true,true,'2020-01-01','2099-12-31')");
        db.execute("insert into transit.trips values ('trip','route','Downtown','daily')");
        db.execute("insert into transit.stop_times values ('trip','stop','12:00:00')");
        var backedByDatabase = new RouteScheduleService(db);
        assertTrue(backedByDatabase.getSchedule("route", "Downtown").arrivals().containsKey("stop"));
        assertTrue(backedByDatabase.getSchedule("route", "Uptown").arrivals().isEmpty());
        var today = LocalDate.now(RouteScheduleService.MIAMI);
        for (int i = -1; i <= 2; i++) {
            db.update("insert into transit.calendar_dates values ('daily',?,2)", today.plusDays(i));
        }
        assertTrue(backedByDatabase.getSchedule("route", "Downtown").arrivals().isEmpty());
        db.execute("drop table transit.calendar");
        assertTrue(backedByDatabase.getSchedule("route", "Downtown").arrivals().containsKey("stop"));
        db.execute("shutdown");
    }

    @Test
    void calendarRespectsWeekendsAndHolidayOverrides() {
        assertFalse(service.active(calendar, "1", LocalDate.of(2026, 9, 26)));
        assertTrue(service.active(calendar, "2", LocalDate.of(2026, 9, 26)));
        assertFalse(service.active(calendar, "1", LocalDate.of(2026, 9, 7)));
        assertTrue(service.active(calendar, "3", LocalDate.of(2026, 9, 7)));
        assertFalse(service.active(calendar, "1", LocalDate.of(2027, 1, 1)));
        assertFalse(service.active(calendar, "missing", LocalDate.of(2026, 9, 26)));
    }

    @Test
    void choosesNextArrivalAndIgnoresInactiveAndInvalidTimes() {
        var now = Instant.parse("2026-09-26T16:00:00Z");
        var result = service.calculate(List.of(
            new RouteScheduleService.Entry("a", "11:59:00", "2"),
            new RouteScheduleService.Entry("a", "12:10:00", "2"),
            new RouteScheduleService.Entry("a", "12:05:00", "2"),
            new RouteScheduleService.Entry("a", "12:01:00", "1"),
            new RouteScheduleService.Entry("b", "bad", "2")
        ), now, calendar);
        assertEquals(Instant.parse("2026-09-26T16:05:00Z"), result.arrivals().get("a"));
        assertFalse(result.arrivals().containsKey("b"));
    }

    @Test
    void includesPreviousServiceDayAfterMidnight() {
        var result = service.calculate(List.of(
            new RouteScheduleService.Entry("a", "25:10:00", "1")
        ), Instant.parse("2026-09-26T05:00:00Z"), calendar);
        assertEquals(Instant.parse("2026-09-26T05:10:00Z"), result.arrivals().get("a"));
    }

    @Test
    void includesTomorrowAndReportsExpiredCalendar() {
        var result = service.calculate(List.of(new RouteScheduleService.Entry("a", "00:10:00", "3")),
                Instant.parse("2026-09-27T03:55:00Z"), calendar);
        assertEquals(Instant.parse("2026-09-27T04:10:00Z"), result.arrivals().get("a"));
        assertFalse(service.calculate(List.of(new RouteScheduleService.Entry("a", "12:10:00", "1")),
                Instant.parse("2027-01-01T16:00:00Z"), calendar).calendarAvailable());
    }

    @Test
    void springForwardWindowCanReachTwoCalendarDatesAhead() {
        var result = service.calculate(List.of(new RouteScheduleService.Entry("a", "00:10:00", "11")),
                Instant.parse("2026-03-08T04:55:00Z"), calendar);
        assertEquals(Instant.parse("2026-03-09T04:10:00Z"), result.arrivals().get("a"));
    }

    @Test
    void handlesGtfsHoursAndDstReference() {
        assertEquals(90600, RouteScheduleService.seconds("25:10:00"));
        assertEquals(-1, RouteScheduleService.seconds("12:99:00"));
        assertEquals(Instant.parse("2026-11-01T05:00:00Z"),
                RouteScheduleService.arrival(LocalDate.of(2026, 11, 1), 0));
    }
}
