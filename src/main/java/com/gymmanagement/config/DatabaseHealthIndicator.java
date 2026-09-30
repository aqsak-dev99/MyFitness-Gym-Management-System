package com.gymmanagement.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * DatabaseHealthIndicator — makes /actuator/health actually mean
 * something for this project, not just "the JVM is running."
 *
 * Updated for the HikariCP migration: now borrows a connection from
 * the real pool (DataSource) instead of reaching into the old
 * DatabaseManager singleton, which no longer holds a connection at
 * all. Borrowing and immediately returning a connection here doubles
 * as a real, live check that the pool itself is healthy — not just
 * that a Bean got constructed successfully at startup.
 *
 * isValid(timeoutSeconds) is still the right check, for the same
 * reason as before: it actively round-trips to the database within a
 * timeout, rather than trusting a flag that may not reflect a
 * connection that died mid-use.
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    // Kept short deliberately — this runs on every health check call,
    // so it should fail fast rather than hang the check itself if the
    // database is genuinely unreachable.
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                return Health.up()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("status", "connection pool healthy")
                    .build();
            }
            return Health.down()
                .withDetail("database", "PostgreSQL")
                .withDetail("status", "connection invalid or unresponsive")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("database", "PostgreSQL")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}