package net.minedrop.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import redis.clients.jedis.JedisPool;

/**
 * Central API entry point for the MineDrop Network.
 * <p>
 * Every MineDrop plugin calls {@link #getInstance()} to access shared services:
 * <ul>
 *   <li>Database connection pool (HikariCP)</li>
 *   <li>Redis connection pool (Jedis)</li>
 *   <li>JSON object mapper (Jackson)</li>
 *   <li>Plugin registration & lifecycle</li>
 * </ul>
 */
public final class MDNAPI {

    private static MDNAPI instance;

    private HikariDataSource dataSource;
    private JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    private MDNAPI() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Returns the singleton API instance.
     * @throws IllegalStateException if not yet initialized via {@link #initialize}
     */
    public static MDNAPI getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MDN-API has not been initialized. Call MDNAPI.initialize() first.");
        }
        return instance;
    }

    /**
     * Initializes the API with database and Redis pools.
     *
     * @param dataSource HikariCP datasource (provided by MDN-Core)
     * @param jedisPool  Jedis pool (provided by MDN-Core)
     */
    public static void initialize(HikariDataSource dataSource, JedisPool jedisPool) {
        if (instance != null) {
            throw new IllegalStateException("MDN-API is already initialized.");
        }
        instance = new MDNAPI();
        instance.dataSource = dataSource;
        instance.jedisPool = jedisPool;
    }

    /** Shared Jackson ObjectMapper for JSON serialization. */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /** HikariCP datasource for MySQL operations. */
    public HikariDataSource getDataSource() {
        return dataSource;
    }

    /** Jedis pool for Redis Pub/Sub and caching. */
    public JedisPool getJedisPool() {
        return jedisPool;
    }
}
