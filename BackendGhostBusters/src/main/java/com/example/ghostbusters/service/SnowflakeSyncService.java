package com.example.ghostbusters.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class SnowflakeSyncService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${snowflake.url}")
    private String snowflakeUrl;

    @Value("${snowflake.user}")
    private String snowflakeUser;

    @Value("${snowflake.password}")
    private String snowflakePassword;

    public SnowflakeSyncService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedRate = 30000) // every 10 minutes
    public void syncRouteReliability() {
        long start = System.currentTimeMillis();
        System.out.println(">>> Snowflake sync starting");

        String sql = """
            WITH stop_outcomes AS (
                SELECT trip_id, stop_id, BOOL_OR(status = 'VISITED') AS was_visited
                FROM transit.stop_visits
                WHERE checked_at > NOW() - INTERVAL '1 hour'
                GROUP BY trip_id, stop_id
            )
            SELECT r.route_id, r.route_short_name,
                   COUNT(*) FILTER (WHERE NOT so.was_visited) AS missed,
                   COUNT(*) AS total,
                   ROUND(100.0 * COUNT(*) FILTER (WHERE NOT so.was_visited) / COUNT(*), 1) AS miss_rate
            FROM stop_outcomes so
            JOIN transit.trips t ON t.trip_id = so.trip_id
            JOIN transit.routes r ON r.route_id = t.route_id
            GROUP BY r.route_id, r.route_short_name
            """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        System.out.println(">>> " + rows.size() + " routes to sync");

        if (rows.isEmpty()) {
            System.out.println(">>> Nothing to sync, done in " + (System.currentTimeMillis() - start) + "ms");
            return;
        }

        String insertSql = """
            INSERT INTO route_reliability
              (route_id, route_short_name, missed_count, total_checked, miss_rate_percent, synced_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(snowflakeUrl, snowflakeUser, snowflakePassword)) {
            try (Statement tzStmt = conn.createStatement()) {
                tzStmt.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
            }

            Instant nowUtc = Instant.now();
            Timestamp nowTimestamp = Timestamp.from(nowUtc);

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (Map<String, Object> row : rows) {
                    ps.setString(1, (String) row.get("route_id"));
                    ps.setString(2, (String) row.get("route_short_name"));
                    ps.setInt(3, ((Number) row.get("missed")).intValue());
                    ps.setInt(4, ((Number) row.get("total")).intValue());
                    ps.setDouble(5, ((Number) row.get("miss_rate")).doubleValue());
                    ps.setTimestamp(6, nowTimestamp);
                    ps.addBatch();
                }
                int[] results = ps.executeBatch();
                System.out.println(">>> Inserted " + results.length + " rows into Snowflake at " + nowUtc);
            }
        } catch (Exception e) {
            System.out.println(">>> Snowflake sync FAILED: " + e.getMessage());
        }

        System.out.println(">>> Snowflake sync done in " + (System.currentTimeMillis() - start) + "ms");
    }
}