package net.minedrop.core.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Manages the Redis connection pool and Pub/Sub messaging.
 * <p>
 * All cross-server communication flows through this manager.
 * Plugins subscribe to channels to receive network-wide events.
 */
public final class RedisManager {

    private static final Logger log = LoggerFactory.getLogger(RedisManager.class);

    private final JedisPool jedisPool;
    private final String pubSubChannel;

    public RedisManager(String host, int port, String password, int timeoutMs, String pubSubChannel) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(2);
        poolConfig.setMaxWait(Duration.ofMillis(timeoutMs));

        if (password != null && !password.isEmpty()) {
            this.jedisPool = new JedisPool(poolConfig, host, port, timeoutMs, password);
        } else {
            this.jedisPool = new JedisPool(poolConfig, host, port, timeoutMs);
        }

        this.pubSubChannel = pubSubChannel;
        log.info("Redis connection pool initialized (host: {}, channel: {})", host, pubSubChannel);
    }

    /**
     * Publishes a message on a Redis channel.
     */
    public void publish(String channel, String message) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.publish(channel, message);
        } catch (Exception e) {
            log.error("Failed to publish to Redis channel '{}'", channel, e);
        }
    }

    /**
     * Publishes a packet on the main network bus.
     */
    public void publishPacket(String packetJson) {
        publish(pubSubChannel, packetJson);
    }

    /**
     * Subscribes to a Redis channel with a callback handler.
     * Runs on a separate thread via CompletableFuture.
     *
     * @param channel the Redis channel to subscribe to
     * @param handler callback for each received message
     */
    public CompletableFuture<Void> subscribe(String channel, Consumer<String> handler) {
        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                log.info("Subscribing to Redis channel: {}", channel);
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String ch, String message) {
                        try {
                            handler.accept(message);
                        } catch (Exception e) {
                            log.error("Error handling Redis message on channel '{}'", ch, e);
                        }
                    }
                }, channel);
            } catch (Exception e) {
                log.error("Redis subscription error on channel '{}'", channel, e);
            }
        });
    }

    /**
     * Sets a key with TTL in Redis.
     */
    public void setWithExpiry(String key, String value, int expirySeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(key, expirySeconds, value);
        }
    }

    /**
     * Gets a value by key from Redis.
     */
    public String get(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        }
    }

    /**
     * Deletes a key from Redis.
     */
    public void delete(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
        }
    }

    /**
     * Checks connection health.
     */
    public boolean isConnected() {
        try (Jedis jedis = jedisPool.getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            return false;
        }
    }

    public JedisPool getJedisPool() {
        return jedisPool;
    }

    public void shutdown() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            log.info("Redis connection pool shut down.");
        }
    }
}
