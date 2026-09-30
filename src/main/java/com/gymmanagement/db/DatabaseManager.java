package com.gymmanagement.db;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * DatabaseManager — as of the HikariCP migration, this is now a pure,
 * stateless parsing utility. It no longer holds a Connection, a
 * Singleton instance, or any connection lifecycle logic at all —
 * that entire responsibility moved to DataSourceConfig, which builds
 * a real HikariCP pool instead of one hand-rolled shared Connection.
 *
 * What stayed, and why: the DATABASE_URL parsing logic below never
 * touched any instance state — it was always pure string parsing —
 * so DataSourceConfig reuses it directly rather than duplicating the
 * same URI-format handling in a second place. This preserves the
 * exact existing DATABASE_URL format (postgres://user:pass@host:port/db)
 * with zero deployment configuration changes required.
 *
 * What used to live here and is now gone: the Singleton getInstance(),
 * the shared Connection field, getConnection() (including its
 * isValid()-based stale-connection reconnect logic — genuinely
 * superseded, not just moved, by HikariCP's keepaliveTime, which
 * proactively validates idle pooled connections on its own schedule
 * rather than only checking reactively when something asks for one),
 * close(), and createSchema() (now DataSourceConfig.runSchemaCreation(),
 * using the exact same DatabaseSchema statements, unchanged).
 */
public final class DatabaseManager {

    private DatabaseManager() {
        // Not instantiated anymore — every method here is static.
    }

    /**
     * Reads the DATABASE_URL environment variable and converts it from
     * the "postgres://user:pass@host:port/db" shape hosting providers
     * hand you, into what a JDBC DataSource actually needs: a
     * jdbc:postgresql:// URL, plus username and password separately.
     *
     * Returns a 3-element array: [jdbcUrl, username, password].
     *
     * Any query parameters on the original string (most providers append
     * something like ?sslmode=require) are preserved and passed through —
     * cloud Postgres almost always requires an encrypted connection, and
     * silently dropping that parameter would cause a much more confusing
     * failure than this method just carrying it along.
     */
    public static String[] resolveConnectionParts() throws URISyntaxException {
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