package com.gymmanagement.config;

import com.gymmanagement.db.DatabaseManager;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.sql.Connection;

/**
 * DatabaseHealthIndicator — makes /actuator/health actually mean
 * something for this project, not just "the JVM is running."
 *
 * Spring Boot Actuator can auto-detect database health for free, but
 * only when the connection is a proper Spring-managed DataSource bean.
 * This project's DatabaseManager is a hand-rolled Singleton using raw
 * DriverManager.getConnection() (see its own comments on why — same
 * reasoning applies here as everywhere else). Actuator has no way to
 * see inside that on its own, so without this class, /actuator/health
 * would only ever report "UP" as long as Spring itself started
 * successfully — even if the actual Postgres connection had silently
 * died, exactly like the real EOFException bug found earlier tonight.
 *
 * This directly closes that gap: it asks DatabaseManager for its live
 * connection and actually checks whether it's open, on every health
 * check call — so a dead connection now shows up as DOWN, not silently
 * accepted until the next real request fails.
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        try {
            Connection connection = DatabaseManager.getInstance().getConnection();
            if (connection != null && !connection.isClosed()) {
                return Health.up()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("status", "connection open")
                    .build();
            }
            return Health.down()
                .withDetail("database", "PostgreSQL")
                .withDetail("status", "connection closed")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("database", "PostgreSQL")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}