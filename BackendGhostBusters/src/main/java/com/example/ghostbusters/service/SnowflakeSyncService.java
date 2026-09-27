package com.example.ghostbusters.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class SnowflakeSyncService {

    private final JdbcTemplate jdbcTemplate; // reads from your existing Postgres

    @Value("${snowflake.url}")
    private String snowflakeUrl;

    @Value("${snowflake.user}")
    private String snowflakeUser;

    @Value("${snowflake.password}")
    private String snowflakePassword;

    public SnowflakeSyncService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedRate = 600000) // every 10 minutes
    public void syncRouteReliability() {
        long start = System.currentTimeMillis();
        System.out.println(">>> Snowflake sync starting");

        // 1. Compute per-route miss stats from Postgres
        String sql = """
            SELECT r.route_id, r.route_short_name,
                   COUNT(*) FILTER (WHERE sv.status = 'MISSED') AS missed,
                   COUNT(*) AS total,
                   ROUND(100.0 * COUNT(*) FILTER (WHERE sv.status = 'MISSED') / COUNT(*), 1) AS miss_rate
            FROM transit.stop_visits sv
            JOIN transit.trips t ON t.trip_id = sv.trip_id
            JOIN transit.routes r ON r.route_id = t.route_id
            WHERE sv.checked_at > (SELECT counting_since FROM transit.ghost_count_settings WHERE id = 1)
            GROUP BY r.route_id, r.route_short_name
            """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        System.out.println(">>> " + rows.size() + " routes to sync");

        if (rows.isEmpty()) {
            System.out.println(">>> Nothing to sync, done in " + (System.currentTimeMillis() - start) + "ms");
            return;
        }

        // 2. Push into Snowflake
        String insertSql = """
            INSERT INTO route_reliability
              (route_id, route_short_name, missed_count, total_checked, miss_rate_percent, synced_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(snowflakeUrl, snowflakeUser, snowflakePassword)) {
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (Map<String, Object> row : rows) {
                    ps.setString(1, (String) row.get("route_id"));
                    ps.setString(2, (String) row.get("route_short_name"));
                    ps.setInt(3, ((Number) row.get("missed")).intValue());
                    ps.setInt(4, ((Number) row.get("total")).intValue());
                    ps.setDouble(5, ((Number) row.get("miss_rate")).doubleValue());
                    ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
                    ps.addBatch();
                }
                int[] results = ps.executeBatch();
                System.out.println(">>> Inserted " + results.length + " rows into Snowflake");
            }
        } catch (Exception e) {
            System.out.println(">>> Snowflake sync FAILED: " + e.getMessage());
        }

        System.out.println(">>> Snowflake sync done in " + (System.currentTimeMillis() - start) + "ms");
    }
}