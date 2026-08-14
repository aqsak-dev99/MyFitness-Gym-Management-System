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
 * Originally checked connection.isClosed() — a genuine mistake, caught
 * during a real recurrence of that same bug. isClosed() only reflects
 * whether the connection was explicitly closed or has ALREADY triggered
 * a fatal error the driver noticed — it doesn't actively test anything.
 * The actual EOFException was only discovered mid-query, inside
 * PGStream.receiveChar(), the moment a real request tried to use the
 * connection. Right up until that instant, isClosed() would still have
 * reported false, meaning the original version of this class shared the
 * exact same blind spot as the bug it existed to catch.
 *
 * isValid(timeoutSeconds) is different: it actively round-trips to the
 * database (a lightweight driver-level check) within the given timeout,
 * rather than trusting a flag that hasn't been updated yet. This is
 * what makes the health check genuinely predictive rather than another
 * copy of the same false confidence.
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    // Kept short deliberately — this runs on every health check call,
    // so it should fail fast rather than hang the check itself if the
    // database is genuinely unreachable.
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    @Override
    public Health health() {
        try {
            Connection connection = DatabaseManager.getInstance().getConnection();
            if (connection != null && connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                return Health.up()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("status", "connection verified")
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