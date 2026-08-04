package net.minedrop.core.session;

import net.minedrop.core.MDNCore;
import net.minedrop.core.cache.PlayerCache;
import net.minedrop.core.redis.RedisManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tracks online player sessions, handles reconnects and server transfers.
 * <p>
 * Runs primarily on Velocity. Paper servers query session state through Redis.
 * Stale sessions (players who disconnected without a clean event) are
 * automatically cleaned up every 5 minutes (fixes M-9).
 */
public final class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    /** Max time a session can be "stale" (no activity) before cleanup. */
    private static final long SESSION_STALE_MS = 60_000; // 1 minute
    /** Max time a transfer can stay pending before timeout. */
    private static final long TRANSFER_TIMEOUT_MS = 30_000; // 30 seconds

    private final RedisManager redisManager;
    private final PlayerCache playerCache;
    private final Map<UUID, PlayerSession> activeSessions = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cleanupScheduler
            = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mdn-session-cleanup");
        t.setDaemon(true);
        return t;
    });

    public SessionManager(RedisManager redisManager, PlayerCache playerCache) {
        this.redisManager = redisManager;
        this.playerCache = playerCache;

        // Periodic cleanup of stale sessions (fixes M-9)
        cleanupScheduler.scheduleAtFixedRate(this::cleanupStaleSessions,
                5, 5, TimeUnit.MINUTES);
    }

    /**
     * Registers a new player session on join.
     */
    public PlayerSession createSession(UUID uuid, String username, String serverName) {
        PlayerSession session = new PlayerSession(uuid, username, serverName);
        activeSessions.put(uuid, session);

        // Update player cache
        var cached = playerCache.getPlayer(uuid);
        cached.setUsername(username);
        if (serverName != null) cached.setCurrentServer(serverName);
        playerCache.updatePlayer(uuid, cached);

        log.info("Session created: {} on {}", username, serverName != null ? serverName : "unknown");
        return session;
    }

    /**
     * Transfers a player session to a new server.
     */
    public void transferSession(UUID uuid, String targetServer) {
        PlayerSession session = activeSessions.get(uuid);
        if (session != null) {
            session.setCurrentServer(targetServer);
            session.setLastTransferTime(System.currentTimeMillis());
            session.setTransferring(false);

            var cached = playerCache.getPlayer(uuid);
            cached.setCurrentServer(targetServer);
            playerCache.updatePlayer(uuid, cached);

            log.info("Session transferred: {} -> {}", session.getUsername(), targetServer);
        }
    }

    /**
     * Removes a session when the player disconnects.
     */
    public void removeSession(UUID uuid) {
        PlayerSession session = activeSessions.remove(uuid);
        if (session != null) {
            playerCache.invalidate(uuid);
            log.info("Session ended: {} (was on {})", session.getUsername(), session.getCurrentServer());
        }
    }

    /**
     * Cleans up stale sessions — players who disconnected without a clean
     * disconnect event (crash, timeout, network loss). Fixes M-9.
     */
    private void cleanupStaleSessions() {
        long now = System.currentTimeMillis();
        int removed = 0;

        var it = activeSessions.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            PlayerSession session = entry.getValue();

            // Remove sessions stuck in transfer for too long
            if (session.isTransferring()
                    && (now - session.getLastTransferTime()) > TRANSFER_TIMEOUT_MS) {
                it.remove();
                playerCache.invalidate(entry.getKey());
                log.warn("Stale transfer session removed: {} (transfer timed out)", session.getUsername());
                removed++;
                continue;
            }

            // Remove sessions with no activity in the stale window
            if ((now - session.getLastTransferTime()) > SESSION_STALE_MS
                    && (now - session.getLoginTime()) > SESSION_STALE_MS) {
                it.remove();
                playerCache.invalidate(entry.getKey());
                log.warn("Stale session removed: {} (no activity)", session.getUsername());
                removed++;
            }
        }

        if (removed > 0) {
            log.info("Cleaned up {} stale sessions ({} remaining)",
                    removed, activeSessions.size());
        }
    }

    public Optional<PlayerSession> getSession(UUID uuid) {
        return Optional.ofNullable(activeSessions.get(uuid));
    }

    public boolean isOnline(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }

    public int getOnlineCount() {
        return activeSessions.size();
    }

    /**
     * Represents an active player session.
     */
    public static class PlayerSession {
        private final UUID uuid;
        private final String username;
        private String currentServer;
        private final long loginTime;
        private long lastTransferTime;
        private boolean isTransferring;

        public PlayerSession(UUID uuid, String username, String currentServer) {
            this.uuid = uuid;
            this.username = username;
            this.currentServer = currentServer;
            this.loginTime = System.currentTimeMillis();
            this.lastTransferTime = System.currentTimeMillis();
        }

        public UUID getUuid() { return uuid; }
        public String getUsername() { return username; }
        public String getCurrentServer() { return currentServer; }
        public void setCurrentServer(String currentServer) { this.currentServer = currentServer; }
        public long getLoginTime() { return loginTime; }
        public long getLastTransferTime() { return lastTransferTime; }
        public void setLastTransferTime(long lastTransferTime) { this.lastTransferTime = lastTransferTime; }
        public boolean isTransferring() { return isTransferring; }
        public void setTransferring(boolean transferring) { isTransferring = transferring; }
    }
}
