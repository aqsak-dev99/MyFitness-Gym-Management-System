package com.gymmanagement.db;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DatabaseManager — application-wide PostgreSQL connection manager.
 *
 * Migrated from SQLite. What changed and what didn't:
 *
 *  Still a Singleton, still lazy-init, still calls createSchema() on
 *  every startup using "CREATE TABLE IF NOT EXISTS" (idempotent, safe
 *  to re-run). None of that needed to change moving databases — those
 *  decisions were about application structure, not which SQL engine
 *  sits underneath.
 *
 *  What DID change: SQLite was a local file needing zero configuration.
 *  Postgres is a real network service needing a host, port, database
 *  name, username, and password. Rather than five separate environment
 *  variables (five more places to make a typo), this reads ONE —
 *  DATABASE_URL — in the format every major Postgres host already hands
 *  you front-and-centre in their dashboard:
 *
 *      postgres://username:password@host:port/database
 *
 *  and parses it into what java.sql.DriverManager actually needs
 *  internally. Copy the connection string your provider gives you,
 *  paste it as one environment variable, done — no manual splitting
 *  required.
 *
 *  FK enforcement: no more PRAGMA call. Postgres enforces every
 *  FOREIGN KEY constraint unconditionally — see DatabaseSchema for
 *  why that's a meaningful improvement, not just a syntax difference.
 *
 *  What I'd change at scale: this is still a single hand-rolled
 *  Connection shared across the whole app — correct for SQLite's
 *  single-writer model, but Postgres supports real concurrent
 *  connections. A production version of this would use a connection
 *  pool (HikariCP, which Spring Boot auto-configures for free once
 *  you use spring.datasource.* properties instead of a hand-rolled
 *  singleton) rather than one Connection object reused everywhere.
 */
public final class DatabaseManager {

    private static DatabaseManager instance;
    private        Connection       connection;

    // ── private constructor (Singleton) ───────────────────
    private DatabaseManager() {
        try {
            Class.forName("org.postgresql.Driver");

            String[] parts = resolveConnectionParts();
            connection = DriverManager.getConnection(parts[0], parts[1], parts[2]);
            createSchema();

            System.out.println("[DB] Connected to PostgreSQL database.");

        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                "[DB] PostgreSQL JDBC driver not found on the classpath.", e);
        } catch (SQLException e) {
            throw new RuntimeException(
                "[DB] Failed to open database connection: " + e.getMessage(), e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(
                "[DB] DATABASE_URL is not a valid connection string: " + e.getMessage(), e);
        }
    }

    // ── Singleton accessor ────────────────────────────────
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    // ── Connection accessor ───────────────────────────────
    /**
     * Returns the shared Connection.
     * Repositories call this to obtain a connection for every query.
     * They do NOT close this connection — it stays open for the
     * lifetime of the application.
     */
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                String[] parts = resolveConnectionParts();
                connection = DriverManager.getConnection(parts[0], parts[1], parts[2]);
            }
        } catch (SQLException | URISyntaxException e) {
            throw new RuntimeException("[DB] Failed to reopen connection: " + e.getMessage(), e);
        }
        return connection;
    }

    // ── Graceful shutdown ─────────────────────────────────
    /**
     * Close the connection cleanly.
     * Called from Main's shutdown hook.
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[DB] Connection closed.");
            }
        } catch (SQLException e) {
            System.err.println("[DB] Error closing connection: " + e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────

    private void createSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(DatabaseSchema.CREATE_MEMBERS);
            st.execute(DatabaseSchema.CREATE_MEMBERSHIPS);
            st.execute(DatabaseSchema.CREATE_PAYMENTS);
            st.execute(DatabaseSchema.CREATE_BOOTCAMP_CLASSES);
            st.execute(DatabaseSchema.CREATE_BOOTCAMP_ENROLMENTS);
            st.execute(DatabaseSchema.CREATE_USERS);
        }
        System.out.println("[DB] Schema verified.");
    }

    /**
     * Reads the DATABASE_URL environment variable and converts it from
     * the "postgres://user:pass@host:port/db" shape hosting providers
     * hand you, into what DriverManager.getConnection() actually wants:
     * a jdbc:postgresql:// URL, plus username and password as separate
     * arguments.
     *
     * Returns a 3-element array: [jdbcUrl, username, password].
     *
     * Any query parameters on the original string (most providers append
     * something like ?sslmode=require) are preserved and passed through —
     * cloud Postgres almost always requires an encrypted connection, and
     * silently dropping that parameter would cause a much more confusing
     * failure than this method just carrying it along.
     */
    private String[] resolveConnectionParts() throws URISyntaxException {
        String raw = System.getenv("DATABASE_URL");
        if (raw == null || raw.isBlank()) {
            throw new RuntimeException(
                "[DB] DATABASE_URL environment variable is not set. " +
                "Set it to a connection string like " +
                "postgres://user:password@host:5432/dbname before running.");
        }

        // Some providers use "postgres://", others "postgresql://" — normalise
        // to one scheme before parsing so java.net.URI handles it consistently.
        URI uri = new URI(raw.replaceFirst("^postgres://", "postgresql://"));

        String userInfo = uri.getUserInfo();
        if (userInfo == null || !userInfo.contains(":")) {
            throw new RuntimeException(
                "[DB] DATABASE_URL is missing username/password — expected format: " +
                "postgres://user:password@host:port/dbname");
        }
        String[] creds = userInfo.split(":", 2);
        String   username = creds[0];
        String   password = creds[1];

        int    port  = uri.getPort() != -1 ? uri.getPort() : 5432;
        String query = uri.getQuery() != null ? "?" + uri.getQuery() : "";
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath() + query;

        return new String[]{ jdbcUrl, username, password };
    }
}