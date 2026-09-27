package com.example.ghostbusters.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class RouteScheduleService {
    static final ZoneId MIAMI = ZoneId.of("America/New_York");
    private final JdbcTemplate jdbc;
    public RouteScheduleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private CalendarData loadCalendar() {
        boolean calendarTable = tableExists("calendar");
        boolean exceptionsTable = tableExists("calendar_dates");
        var calendar = calendarTable ? jdbc.query("""
                select service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday,
                       start_date, end_date from transit.calendar
                """, (rs, n) -> {
            var row = new String[10];
            row[0] = rs.getString(1);
            for (int i = 1; i <= 7; i++) row[i] = rs.getBoolean(i + 1) ? "1" : "0";
            row[8] = rs.getString(9).replace("-", "");
            row[9] = rs.getString(10).replace("-", "");
            return row;
        }) : List.<String[]>of();
        var exceptions = exceptionsTable
                ? jdbc.query("select service_id, date, exception_type from transit.calendar_dates",
                (rs, n) -> new String[]{rs.getString(1), rs.getString(2).replace("-", ""), rs.getString(3)})
                : List.<String[]>of();
        return new CalendarData(calendar, exceptions, calendarTable);
    }

    private boolean tableExists(String tableName) {
        Boolean exists = jdbc.queryForObject("""
                select exists (
                    select 1 from information_schema.tables
                    where lower(table_schema) = lower(?) and lower(table_name) = lower(?)
                )
                """, Boolean.class, "transit", tableName);
        return Boolean.TRUE.equals(exists);
    }

    boolean active(CalendarData data, String service, LocalDate date) {
        if (!data.tablesAvailable()) return true;
        String day = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        for (var row : data.exceptions()) {
            if (row[0].equals(service) && row[1].equals(day)) return row[2].equals("1");
        }
        return data.calendar().stream().anyMatch(row -> row[0].equals(service)
                && row[date.getDayOfWeek().getValue()].equals("1")
                && row[8].compareTo(day) <= 0 && row[9].compareTo(day) >= 0);
    }

    static long seconds(String time) {
        if (time == null || !time.trim().matches("\\d{1,3}:[0-5]\\d:[0-5]\\d")) return -1;
        var parts = time.trim().split(":");
        return Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Long.parseLong(parts[2]);
    }

    // GTFS service times are measured from noon minus 12 hours, including DST days.
    static Instant arrival(LocalDate date, long seconds) {
        return date.atTime(12, 0).atZone(MIAMI).toInstant().minusSeconds(43200).plusSeconds(seconds);
    }

    public Schedule getSchedule(String routeId, String headsign) {
        return calculate(jdbc.query("""
            select st.stop_id, st.arrival_time, t.service_id
            from transit.stop_times st join transit.trips t on t.trip_id = st.trip_id
            where t.route_id = ? and trim(upper(t.trip_headsign)) = trim(upper(?))
            """, (rs, n) -> new Entry(rs.getString(1), rs.getString(2), rs.getString(3)), routeId, headsign), Instant.now(), loadCalendar());
    }

    Schedule calculate(List<Entry> entries, Instant now, CalendarData data) {
        var next = new TreeMap<String, Instant>();
        var today = now.atZone(MIAMI).toLocalDate();
        boolean calendarAvailable = entries.stream().anyMatch(entry ->
                active(data, entry.serviceId(), today) || active(data, entry.serviceId(), today.plusDays(1))
                || data.calendar().stream().anyMatch(row -> row[0].equals(entry.serviceId())
                    && row[8].compareTo(today.format(DateTimeFormatter.BASIC_ISO_DATE)) <= 0
                    && row[9].compareTo(today.format(DateTimeFormatter.BASIC_ISO_DATE)) >= 0));
        for (var entry : entries) {
            long time = seconds(entry.time());
            if (time < 0) continue;
            for (int offset = -(int)(time / 86400) - 1; offset <= 2; offset++) {
                var date = today.plusDays(offset);
                if (!active(data, entry.serviceId(), date)) continue;
                var at = arrival(date, time);
                if (!at.isBefore(now) && at.isBefore(now.plusSeconds(86400)))
                    next.merge(entry.stopId(), at, (a, b) -> a.isBefore(b) ? a : b);
            }
        }
        return new Schedule(now, calendarAvailable || !next.isEmpty(), next);
    }

    record CalendarData(List<String[]> calendar, List<String[]> exceptions, boolean tablesAvailable) {
        CalendarData(List<String[]> calendar, List<String[]> exceptions) {
            this(calendar, exceptions, true);
        }
    }
    record Entry(String stopId, String time, String serviceId) {}
    public record Schedule(Instant generatedAt, boolean calendarAvailable, Map<String, Instant> arrivals) {}
}
