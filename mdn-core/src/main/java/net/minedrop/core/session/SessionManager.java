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

/**
 * Tracks online player sessions, handles reconnects and server transfers.
 * <p>
 * Runs primarily on Velocity. Paper servers query session state through Redis.
 */
public final class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final RedisManager redisManager;
    private final PlayerCache playerCache;
    private final Map<UUID, PlayerSession> activeSessions = new ConcurrentHashMap<>();

    public SessionManager(RedisManager redisManager, PlayerCache playerCache) {
        this.redisManager = redisManager;
        this.playerCache = playerCache;
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
        cached.setCurrentServer(serverName);
        playerCache.updatePlayer(uuid, cached);

        log.info("Session created: {} on {}", username, serverName);
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
