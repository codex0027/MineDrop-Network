package net.minedrop.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.minedrop.api.packet.AuthUpdatePacket;
import net.minedrop.core.redis.RedisManager;
import org.slf4j.Logger;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Manages authenticated sessions with Redis-backed storage.
 * <p>
 * <h3>Session lifecycle:</h3>
 * <pre>
 *   CONNECTED → PASSWORD_REQUIRED → PASSWORD_VERIFIED → TOTP_REQUIRED → AUTHENTICATED → DISCONNECTED
 * </pre>
 * <p>
 * <h3>Duplicate login policy:</h3>
 * New connection can't kick old session until successfully authenticated.
 * Once authenticated, old session is revoked and old player disconnected.
 * <p>
 * <h3>Redis keys:</h3>
 * <ul>
 *   <li>{@code mdn:auth:session:<sessionId>} → Session JSON (TTL: 30 min)</li>
 *   <li>{@code mdn:auth:session:uuid:<uuid>} → sessionId (for lookup)</li>
 *   <li>{@code mdn:auth:login-lock:<uuid>} → connectionId (login coordination)</li>
 * </ul>
 */
public final class SessionManager {

    private static final String SESSION_PREFIX = "mdn:auth:session:";
    private static final String SESSION_UUID_PREFIX = "mdn:auth:session:uuid:";
    private static final String LOGIN_LOCK_PREFIX = "mdn:auth:login-lock:";
    private static final int SESSION_TTL_SECONDS = 1800; // 30 minutes
    private static final int LOGIN_LOCK_TTL_SECONDS = 30;

    private final RedisManager redisManager;
    private final ObjectMapper objectMapper;
    private final Logger logger;
    private final SecureRandom secureRandom;

    public SessionManager(RedisManager redisManager, ObjectMapper objectMapper, Logger logger) {
        this.redisManager = redisManager;
        this.objectMapper = objectMapper;
        this.logger = logger;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Creates a new session for a player.
     *
     * @param uuid the player's UUID
     * @param ip   the player's IP address
     * @return the session, or null on failure
     */
    public Session createSession(UUID uuid, String ip) {
        Session session = new Session();
        session.sessionId = generateSessionId();
        session.uuid = uuid.toString();
        session.ip = ip;
        session.state = SessionState.AUTHENTICATED.name();
        session.createdAt = Instant.now().getEpochSecond();
        session.lastActivityAt = session.createdAt;

        // Check for existing session → revoke it
        String oldSessionId = redisManager.get(SESSION_UUID_PREFIX + uuid);
        if (oldSessionId != null) {
            revokeSession(oldSessionId, "Duplicate login — replaced by new session");
        }

        // Store session
        try {
            String json = objectMapper.writeValueAsString(session);
            redisManager.setWithExpiry(SESSION_PREFIX + session.sessionId, json, SESSION_TTL_SECONDS);
            redisManager.setWithExpiry(SESSION_UUID_PREFIX + uuid, session.sessionId, SESSION_TTL_SECONDS);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize session for {}", uuid, e);
            return null;
        }

        logger.info("Session created for {} (id={}, ip={})", uuid,
                session.sessionId.substring(0, 12), ip);
        return session;
    }

    /**
     * Gets the active session for a UUID.
     *
     * @return the session or null if no active session exists
     */
    public Session getSession(UUID uuid) {
        String sessionId = redisManager.get(SESSION_UUID_PREFIX + uuid);
        if (sessionId == null) return null;

        String json = redisManager.get(SESSION_PREFIX + sessionId);
        if (json == null) return null;

        try {
            Session s = objectMapper.readValue(json, Session.class);
            // Update last activity
            s.lastActivityAt = Instant.now().getEpochSecond();
            redisManager.setWithExpiry(SESSION_PREFIX + sessionId,
                    objectMapper.writeValueAsString(s), SESSION_TTL_SECONDS);
            return s;
        } catch (JsonProcessingException e) {
            logger.debug("Corrupt session data for {}", uuid);
            return null;
        }
    }

    /**
     * Checks if a player has an active authenticated session.
     */
    public boolean hasActiveSession(UUID uuid) {
        Session s = getSession(uuid);
        return s != null && SessionState.AUTHENTICATED.name().equals(s.state);
    }

    /**
     * Revokes a session by its ID.
     */
    public void revokeSession(String sessionId, String reason) {
        String json = redisManager.get(SESSION_PREFIX + sessionId);
        if (json != null) {
            try {
                Session s = objectMapper.readValue(json, Session.class);
                redisManager.delete(SESSION_UUID_PREFIX + s.uuid);
                logger.info("Session {} revoked: {}", sessionId.substring(0, 12), reason);
            } catch (JsonProcessingException ignored) {}
        }
        redisManager.delete(SESSION_PREFIX + sessionId);
    }

    /**
     * Revokes all sessions for a UUID (e.g., on password reset).
     */
    public void revokeAllSessions(UUID uuid, String reason) {
        String sessionId = redisManager.get(SESSION_UUID_PREFIX + uuid);
        if (sessionId != null) {
            revokeSession(sessionId, reason);
        }
        logger.info("All sessions revoked for {}: {}", uuid, reason);
    }

    /**
     * Publishes an AUTH_UPDATE packet via Redis pub/sub.
     *
     * @param uuid      the player's UUID
     * @param authenticated true if the player is now authenticated, false if deauthenticated
     */
    public void publishAuthUpdate(UUID uuid, boolean authenticated) {
        try {
            AuthUpdatePacket packet = new AuthUpdatePacket(uuid, authenticated, uuid);
            String json = objectMapper.writeValueAsString(packet);
            redisManager.publish("mdn_auth", json);
            logger.debug("AUTH_UPDATE published: uuid={}, status={}", uuid, authenticated);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize AUTH_UPDATE for {}", uuid, e);
        }
    }

    // ── Login lock (prevents race conditions during /login) ──

    /**
     * Acquires a login lock for a UUID.
     * Uses the in-memory map as a fast mutex for login coordination on the same proxy instance.
     * Redis key provides cross-proxy safety.
     *
     * @return the lock token if acquired, null if already locked
     */
    public String acquireLoginLock(UUID uuid) {
        // Fast path: check in-memory map (avoids Redis round-trip for same-proxy races)
        if (loginLocks.containsKey(uuid)) return null;

        String token = generateSessionId().substring(0, 16);
        String key = LOGIN_LOCK_PREFIX + uuid;

        // Redis-level lock for cross-proxy safety
        String existing = redisManager.get(key);
        if (existing != null) return null;
        redisManager.setWithExpiry(key, token, LOGIN_LOCK_TTL_SECONDS);

        // In-memory tracking for fast duplicate check
        loginLocks.put(uuid, token);
        return token;
    }

    /**
     * Releases a login lock.
     */
    public void releaseLoginLock(UUID uuid, String token) {
        loginLocks.remove(uuid, token);
        String key = LOGIN_LOCK_PREFIX + uuid;
        redisManager.delete(key);
    }

    /** In-memory login lock tracker — prevents same-proxy race conditions. */
    private final java.util.Map<UUID, String> loginLocks = new java.util.concurrent.ConcurrentHashMap<>();

    // ── Helpers ──

    private String generateSessionId() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Cleanup — Redis pool managed externally. */
    public void shutdown() {
        // No persistent state
    }

    // ── Types ──

    /** Authentication state machine states. */
    public enum SessionState {
        CONNECTED,
        PASSWORD_REQUIRED,
        PASSWORD_VERIFIED,
        TOTP_REQUIRED,
        AUTHENTICATED,
        DISCONNECTED
    }

    /** Session data stored in Redis. */
    public static class Session {
        public String sessionId;
        public String uuid;
        public String ip;
        public String state;
        public long createdAt;
        public long lastActivityAt;

        public Session() {}
    }
}
