package com.gymmanagement.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DatabaseManager — application-wide SQLite connection manager.
 *
 * Design decisions:
 *
 *  Singleton:  The entire application shares one Connection object.
 *              SQLite supports only one writer at a time; a single
 *              connection is correct for a single-user console app.
 *              If this were a multi-threaded web app, a connection
 *              pool (e.g. HikariCP) would replace this singleton.
 *
 *  Lazy init:  The connection is created on the first call to
 *              getInstance(), not at class-load time.  This means
 *              tests that never call getInstance() do not open a file.
 *
 *  DB file:    Stored as "gym.db" in the working directory.
 *              On first run SQLite creates the file automatically.
 *              Subsequent runs reuse the existing file — data persists.
 *
 *  Schema:     createSchema() is called once after the connection opens.
 *              "CREATE TABLE IF NOT EXISTS" statements are idempotent —
 *              they do nothing if the tables already exist, so calling
 *              this on every startup is safe.
 *
 *  FK support: SQLite does not enforce foreign keys by default.
 *              PRAGMA foreign_keys = ON is executed after every new
 *              connection because the pragma resets when the connection
 *              closes.
 */
public final class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:gym.db";

    private static DatabaseManager instance;
    private        Connection       connection;

    // ── private constructor (Singleton) ───────────────────
    private DatabaseManager() {
        try {
            // Load the SQLite JDBC driver explicitly.
            // Required in some environments where ServiceLoader auto-detection
            // does not pick up the driver from the classpath.
            Class.forName("org.sqlite.JDBC");

            connection = DriverManager.getConnection(DB_URL);
            enableForeignKeys();
            createSchema();

            System.out.println("[DB] Connected to SQLite database: gym.db");

        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                "[DB] SQLite JDBC driver not found. " +
                "Add sqlite-jdbc-3.45.1.0.jar to your classpath.", e);
        } catch (SQLException e) {
            throw new RuntimeException(
                "[DB] Failed to open database connection: " + e.getMessage(), e);
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
            // Reopen if the connection was closed (e.g. after a test teardown)
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(DB_URL);
                enableForeignKeys();
            }
        } catch (SQLException e) {
            throw new RuntimeException("[DB] Failed to reopen connection: " + e.getMessage(), e);
        }
        return connection;
    }

    // ── Graceful shutdown ─────────────────────────────────
    /**
     * Close the connection cleanly.
     * Called from Main's shutdown hook so WAL files are flushed.
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

    private void enableForeignKeys() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(DatabaseSchema.ENABLE_FOREIGN_KEYS);
        }
    }

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
}