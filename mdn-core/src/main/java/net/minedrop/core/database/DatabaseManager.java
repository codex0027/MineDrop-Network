package net.minedrop.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.minedrop.api.database.DatabaseSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the MySQL connection pool via HikariCP.
 * <p>
 * Initialized by MDN-Core on startup. All plugins access the datasource
 * through {@link net.minedrop.api.MDNAPI#getDataSource()}.
 */
public final class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private final HikariDataSource dataSource;

    public DatabaseManager(String host, int port, String database,
                           String username, String password,
                           int maxPoolSize, int minIdle,
                           long connectionTimeoutMs, long idleTimeoutMs) {
        HikariConfig config = new HikariConfig();

        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                host, port, database);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);

        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeoutMs);
        config.setIdleTimeout(idleTimeoutMs);
        config.setMaxLifetime(1_800_000); // 30 minutes

        // Performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        this.dataSource = new HikariDataSource(config);
        log.info("HikariCP connection pool initialized (max: {}, min: {})", maxPoolSize, minIdle);
    }

    /**
     * Executes all CREATE TABLE IF NOT EXISTS statements from MDN-API's schema.
     */
    public void initializeSchema() {
        log.info("Initializing database schema...");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            for (String ddl : DatabaseSchema.getCreateTableStatements()) {
                stmt.execute(ddl);
            }
            log.info("Database schema initialized successfully ({} tables).",
                    DatabaseSchema.getCreateTableStatements().size());

        } catch (SQLException e) {
            log.error("Failed to initialize database schema", e);
            throw new RuntimeException("Database schema initialization failed", e);
        }
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    /**
     * Attempts a test query to verify connectivity.
     */
    public boolean isConnected() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("Database connection pool shut down.");
        }
    }
}
