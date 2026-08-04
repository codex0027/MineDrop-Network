package net.minedrop.core.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.minedrop.api.MDNAPI;
import net.minedrop.core.MDNCore;
import net.minedrop.core.redis.RedisManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Redis-backed cache for frequently accessed player data with automatic eviction.
 * <p>
 * Entries are evicted from the local in-memory cache after 10 minutes of inactivity.
 * This prevents memory leaks from disconnected players whose invalidate() call was missed.
 */
public final class PlayerCache {

    private static final Logger log = LoggerFactory.getLogger(PlayerCache.class);

    /** Local cache entries expire after 10 minutes of inactivity. */
    private static final long LOCAL_CACHE_TTL_MS = 10 * 60 * 1000;

    private final RedisManager redisManager;
    private final ObjectMapper objectMapper;

    // Local in-memory cache with last-access timestamps
    private final Map<UUID, CacheEntry> localCache = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cleanupScheduler
            = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mdn-player-cache-cleanup");
        t.setDaemon(true);
        return t;
    });

    public PlayerCache(RedisManager redisManager) {
        this.redisManager = redisManager;
        this.objectMapper = MDNAPI.getInstance().getObjectMapper();

        // Evict stale entries every 5 minutes
        cleanupScheduler.scheduleAtFixedRate(this::evictStaleEntries,
                5, 5, TimeUnit.MINUTES);
    }

    /**
     * Loads a player's cached data, preferring local cache over Redis.
     */
    public CachedPlayer getPlayer(UUID uuid) {
        CacheEntry entry = localCache.computeIfAbsent(uuid, k -> new CacheEntry(loadFromRedis(uuid)));
        entry.touch();
        return entry.player;
    }

    /**
     * Updates a player's cached data and syncs to Redis.
     */
    public void updatePlayer(UUID uuid, CachedPlayer player) {
        localCache.put(uuid, new CacheEntry(player));
        saveToRedis(uuid, player);
    }

    /**
     * Removes a player from cache (on graceful disconnect).
     */
    public void invalidate(UUID uuid) {
        localCache.remove(uuid);
        redisManager.delete(MDNCore.KEY_PLAYER_CACHE + uuid);
    }

    /**
     * Evicts entries that haven't been accessed within the TTL window.
     */
    private void evictStaleEntries() {
        long cutoff = System.currentTimeMillis() - LOCAL_CACHE_TTL_MS;
        int removed = 0;
        var it = localCache.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getValue().lastAccess < cutoff) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("Evicted {} stale player cache entries", removed);
        }
    }

    private CachedPlayer loadFromRedis(UUID uuid) {
        String json = redisManager.get(MDNCore.KEY_PLAYER_CACHE + uuid);
        if (json != null) {
            try {
                return objectMapper.readValue(json, CachedPlayer.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize player cache for {}", uuid);
            }
        }
        return CachedPlayer.createDefault(uuid);
    }

    private void saveToRedis(UUID uuid, CachedPlayer player) {
        try {
            String json = objectMapper.writeValueAsString(player);
            redisManager.setWithExpiry(MDNCore.KEY_PLAYER_CACHE + uuid, json, 1200);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize player cache for {}", uuid, e);
        }
    }

    public void shutdown() {
        cleanupScheduler.shutdown();
        localCache.clear();
    }

    // ── Inner types ──

    /** Wrapper that tracks last access time for eviction. */
    private static class CacheEntry {
        final CachedPlayer player;
        volatile long lastAccess;

        CacheEntry(CachedPlayer player) {
            this.player = player;
            this.lastAccess = System.currentTimeMillis();
        }

        void touch() {
            this.lastAccess = System.currentTimeMillis();
        }
    }

    /**
     * Lightweight player data object cached in Redis and memory.
     */
    public static class CachedPlayer {
        private UUID uuid;
        private String username;
        private double coins;
        private String clanId;
        private String currentServer;
        private String permissionGroup;
        private int prestigeLevel;
        private boolean hasActiveBoost;
        private boolean protectionActive;

        public CachedPlayer() {}

        public static CachedPlayer createDefault(UUID uuid) {
            CachedPlayer p = new CachedPlayer();
            p.uuid = uuid;
            p.coins = 1000.0;
            p.permissionGroup = "default";
            return p;
        }

        // ── Getters & Setters ──

        public UUID getUuid() { return uuid; }
        public void setUuid(UUID uuid) { this.uuid = uuid; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public double getCoins() { return coins; }
        public void setCoins(double coins) { this.coins = coins; }

        public String getClanId() { return clanId; }
        public void setClanId(String clanId) { this.clanId = clanId; }

        public String getCurrentServer() { return currentServer; }
        public void setCurrentServer(String currentServer) { this.currentServer = currentServer; }

        public String getPermissionGroup() { return permissionGroup; }
        public void setPermissionGroup(String permissionGroup) { this.permissionGroup = permissionGroup; }

        public int getPrestigeLevel() { return prestigeLevel; }
        public void setPrestigeLevel(int prestigeLevel) { this.prestigeLevel = prestigeLevel; }

        public boolean isHasActiveBoost() { return hasActiveBoost; }
        public void setHasActiveBoost(boolean hasActiveBoost) { this.hasActiveBoost = hasActiveBoost; }

        public boolean isProtectionActive() { return protectionActive; }
        public void setProtectionActive(boolean protectionActive) { this.protectionActive = protectionActive; }
    }
}
