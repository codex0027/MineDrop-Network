package net.minedrop.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central API entry point for the MineDrop Network.
 * <p>
 * Every MineDrop plugin calls {@link #getInstance()} to access shared services:
 * <ul>
 *   <li>Database connection pool (HikariCP)</li>
 *   <li>Redis connection pool (Jedis)</li>
 *   <li>JSON object mapper (Jackson — pre-configured)</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #initialize(HikariDataSource, JedisPool)} — called once by MDN-Core on startup</li>
 *   <li>{@link #getInstance()} — used by all plugins to access services</li>
 *   <li>{@link #shutdown()} — called by MDN-Core on shutdown to release resources</li>
 * </ol>
 */
public final class MDNAPI {

    private static final Logger log = LoggerFactory.getLogger(MDNAPI.class);

    private static volatile MDNAPI instance;
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private HikariDataSource dataSource;
    private JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    /** Per-instance correlation ID for tracing across servers. */
    private String instanceId = UUID.randomUUID().toString().substring(0, 8);

    private MDNAPI() {
        this.objectMapper = createObjectMapper();
    }

    // ── ObjectMapper factory with production-ready config ──

    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                // Register JavaTimeModule for proper Instant/LocalDateTime handling
                .registerModule(new JavaTimeModule())
                // Ignore unknown properties for forward compatibility
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                // Write dates as ISO-8601 strings, not timestamps
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                // Exclude null values from JSON to reduce payload size
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    // ── Singleton lifecycle ──

    /**
     * Returns the singleton API instance.
     *
     * @return the active API instance
     * @throws IllegalStateException if not yet initialized via {@link #initialize}
     */
    public static MDNAPI getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "MDN-API has not been initialized. Call MDNAPI.initialize() first.");
        }
        return instance;
    }

    /**
     * Returns true if MDN-API has been initialized and is ready for use.
     */
    public static boolean isInitialized() {
        return instance != null && initialized.get();
    }

    /**
     * Initializes the API with database and Redis pools.
     * <p>
     * Must be called exactly once, typically by MDN-Core on startup.
     *
     * @param dataSource HikariCP datasource (provided by MDN-Core)
     * @param jedisPool  Jedis pool (provided by MDN-Core)
     * @throws IllegalStateException if already initialized or shutting down
     */
    public static void initialize(HikariDataSource dataSource, JedisPool jedisPool) {
        if (initialized.get()) {
            throw new IllegalStateException("MDN-API is already initialized.");
        }
        if (shuttingDown.get()) {
            throw new IllegalStateException("MDN-API is shutting down — cannot reinitialize.");
        }

        instance = new MDNAPI();
        instance.dataSource = dataSource;
        instance.jedisPool = jedisPool;
        initialized.set(true);
        log.info("MDN-API initialized successfully.");
    }

    /**
     * Creates a standalone API instance for testing or development without
     * a live database or Redis connection. Not for production use.
     */
    public static MDNAPI createStandalone() {
        MDNAPI api = new MDNAPI();
        api.dataSource = null;
        api.jedisPool = null;
        initialized.set(true); // mark as ready so getInstance() won't complain if called later
        return api;
    }

    /**
     * Shuts down the API, releasing database and Redis connections.
     * <p>
     * After shutdown, the API cannot be reinitialized. Safe to call multiple times.
     */
    public static void shutdown() {
        if (!initialized.getAndSet(false)) {
            return; // Already shut down or never initialized
        }
        shuttingDown.set(true);

        if (instance != null) {
            try {
                if (instance.dataSource != null && !instance.dataSource.isClosed()) {
                    instance.dataSource.close();
                    log.info("Database connection pool closed.");
                }
            } catch (Exception e) {
                log.error("Error closing datasource during shutdown", e);
            }

            try {
                if (instance.jedisPool != null && !instance.jedisPool.isClosed()) {
                    instance.jedisPool.close();
                    log.info("Redis connection pool closed.");
                }
            } catch (Exception e) {
                log.error("Error closing Redis pool during shutdown", e);
            }
        }

        instance = null;
        log.info("MDN-API shut down.");
    }

    // ── Service accessors ──

    /**
     * Returns the current API version for compatibility checking.
     * Plugins can call this to verify they're running against a compatible API.
     */
    public static ApiVersion getApiVersion() {
        return ApiVersion.CURRENT;
    }

    /**
     * Returns the unique instance ID of this API instance.
     * Used for correlation in distributed tracing.
     */
    public String getInstanceId() {
        return instanceId;
    }
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /** HikariCP datasource for MySQL operations. May be null in standalone mode. */
    public HikariDataSource getDataSource() {
        return dataSource;
    }

    /** Jedis pool for Redis Pub/Sub and caching. May be null in standalone mode. */
    public JedisPool getJedisPool() {
        return jedisPool;
    }
}
