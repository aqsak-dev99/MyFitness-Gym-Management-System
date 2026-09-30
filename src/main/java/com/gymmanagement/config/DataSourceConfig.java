package com.gymmanagement.config;

import com.gymmanagement.db.DatabaseManager;
import com.gymmanagement.db.DatabaseSchema;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The real fix for this project's repeated EOFException/
 * SocketTimeoutException/"connection stale" failures — replacing
 * DatabaseManager's single hand-rolled Connection with an actual
 * HikariCP pool.
 *
 * Why this solves the architectural weakness the earlier isValid()
 * fix couldn't: that fix could only catch a connection that had
 * ALREADY gone stale sitting idle, checked at the moment something
 * asked for it — it had no way to protect a connection that died
 * DURING an active query (the SocketTimeoutException inside
 * loadPaymentsForMember() a few messages back is exactly that case).
 * HikariCP's keepaliveTime proactively pings idle pooled connections
 * on its own schedule, catching staleness before a real request ever
 * touches that connection — a fundamentally different mechanism, not
 * a bigger version of the same check.
 *
 * DATABASE_URL is unchanged — this reuses DatabaseManager's own
 * existing parsing logic rather than requiring any new environment
 * variable or a different format, so local/Render/Neon deployment
 * configuration keeps working exactly as before.
 *
 * Schema creation now runs here, once, using a connection borrowed
 * from the freshly-built pool and immediately returned — every SQL
 * statement in DatabaseSchema is reused completely unchanged; only
 * WHERE it executes from changed, not what it does.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource() throws URISyntaxException, SQLException {
        String[] parts = DatabaseManager.resolveConnectionParts();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(parts[0]);
        config.setUsername(parts[1]);
        config.setPassword(parts[2]);

        // Sensible, deliberately non-aggressive for a small portfolio
        // app — not tuned for real concurrent production load, which
        // this project doesn't have yet.
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);   // 10s to obtain a connection before giving up
        config.setValidationTimeout(3_000);    // matches the timeout the old isValid() fix used

        // The actual fix for mid-idle staleness: proactively pings
        // pooled connections that have been idle this long, replacing
        // a dead one before any real request ever borrows it — rather
        // than only discovering staleness reactively when a query
        // finally hits it.
        config.setKeepaliveTime(120_000);      // 2 minutes
        config.setMaxLifetime(1_500_000);      // 25 minutes — well under Neon's own idle cutoff

        // Real fix for a genuine failure just observed: immediately after
        // a rapid restart, HikariCP's very first connection attempt can
        // come back already-closed (Neon momentarily rejecting a near-
        // instant reconnect from the same client is the most likely
        // explanation, though not fully certain). HikariCP's default is
        // to fail the whole pool on that very first attempt, no retry —
        // this gives it room to try again a few times before actually
        // giving up. If the database is genuinely unreachable for this
        // whole window, startup still correctly fails — this only
        // absorbs a transient first-connection hiccup, not a real outage.
        config.setInitializationFailTimeout(30_000);   // 30s, a few real retries

        HikariDataSource dataSource = new HikariDataSource(config);

        runSchemaCreation(dataSource);

        return dataSource;
    }

    /**
     * Runs once, immediately after the pool is built — guarantees the
     * schema exists before any repository bean (all of which depend on
     * this DataSource bean transitively) can possibly query it. Every
     * statement here is identical to DatabaseManager's original
     * createSchema() — copied, not modified.
     *
     * Public static so Main.java (the separate, non-Spring console
     * entry point) can reuse the exact same schema statements rather
     * than duplicating this list in a second place.
     */
    public static void runSchemaCreation(DataSource dataSource) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.execute(DatabaseSchema.CREATE_MEMBERS);
            st.execute(DatabaseSchema.CREATE_MEMBERSHIPS);
            st.execute(DatabaseSchema.CREATE_PAYMENTS);
            st.execute(DatabaseSchema.CREATE_BOOTCAMP_CLASSES);
            st.execute(DatabaseSchema.CREATE_BOOTCAMP_ENROLMENTS);
            st.execute(DatabaseSchema.CREATE_USERS);
            st.execute(DatabaseSchema.CREATE_DOCUMENTS);
            st.execute(DatabaseSchema.CREATE_VECTOR_EXTENSION);   // must run first — CREATE_DOCUMENT_CHUNKS references the vector type
            st.execute(DatabaseSchema.CREATE_DOCUMENT_CHUNKS);
            st.execute(DatabaseSchema.ADD_EMBEDDING_COLUMN);
            st.execute(DatabaseSchema.ADD_FITNESS_GOAL_COLUMN);
            st.execute(DatabaseSchema.ADD_ACTIVE_COLUMN);
            st.execute(DatabaseSchema.ADD_CANCELLED_COLUMN);
            st.execute(DatabaseSchema.ADD_DOCUMENT_AUDIENCE_COLUMN);
            st.execute(DatabaseSchema.ADD_NEXT_PAYMENT_DUE_DATE_COLUMN);
            st.execute(DatabaseSchema.CREATE_STAFF);
        }
        System.out.println("[DB] Schema verified.");
        System.out.println("[DB] Connected to PostgreSQL database via HikariCP pool.");
    }
}