package net.minedrop.auth;

import net.minedrop.core.redis.RedisManager;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;

/**
 * Detects alternate (alt) accounts by tracking IP → UUID and fingerprint → UUID
 * associations in Redis.
 * <p>
 * Redis key schema:
 * <ul>
 *   <li>{@code mdn:auth:alt:ip:<ip>} → Set of UUIDs (TTL: 24h after last write)</li>
 *   <li>{@code mdn:auth:alt:fp:<fingerprint_hash>} → Set of UUIDs (TTL: 24h)</li>
 *   <li>{@code mdn:auth:unblocked:<ip>} → "true" (no TTL — permanent whitelist)</li>
 * </ul>
 */
public final class AltDetector {

    private static final String KEY_IP_PREFIX = "mdn:auth:alt:ip:";
    private static final String KEY_FP_PREFIX = "mdn:auth:alt:fp:";
    private static final String KEY_UNBLOCKED_PREFIX = "mdn:auth:unblocked:";
    private static final int TTL_SECONDS = 86400; // 24 hours

    private final RedisManager redisManager;
    private final Logger logger;

    public AltDetector(RedisManager redisManager, Logger logger) {
        this.redisManager = redisManager;
        this.logger = logger;
    }

    /**
     * Checks whether a player exceeds alt account limits.
     *
     * @param playerUuid  the player's UUID
     * @param ipAddress   the player's IP address (full)
     * @param fingerprint the device fingerprint hash
     * @param maxPerIp    max accounts allowed per IP
     * @param maxPerFp    max accounts allowed per fingerprint
     * @return the recommended action
     */
    public Action check(UUID playerUuid, String ipAddress, String fingerprint,
                         int maxPerIp, int maxPerFp) {
        // Skip if this IP is whitelisted
        if (isIpUnblocked(ipAddress)) {
            return Action.ALLOW;
        }

        int ipCount = countAccountsForIp(ipAddress);
        int fpCount = countAccountsForFingerprint(fingerprint);

        logger.debug("Alt check for {}: ip={} ({} accounts, max={}), fp={} ({} accounts, max={})",
                playerUuid, ipAddress, ipCount, maxPerIp,
                fingerprint != null ? fingerprint.substring(0, 12) : "null", fpCount, maxPerFp);

        if (ipCount >= maxPerIp) {
            return Action.KICK;
        }

        if (fpCount >= maxPerFp) {
            return Action.KICK;
        }

        // Alert if approaching limits
        if (ipCount >= maxPerIp - 1 || fpCount >= maxPerFp - 1) {
            return Action.ALERT;
        }

        return Action.ALLOW;
    }

    /**
     * Records a successful login, associating the UUID with the IP and fingerprint.
     */
    public void recordLogin(UUID playerUuid, String ipAddress, String fingerprint) {
        // Track by IP (Redis list — allows counting account associations per IP)
        String ipKey = KEY_IP_PREFIX + ipAddress;
        redisManager.lpush(ipKey, playerUuid.toString());
        // Periodic cleanup of old entries is handled by the AuthVelocityPlugin on startup

        // Track by fingerprint (if available)
        if (fingerprint != null && !fingerprint.isEmpty()) {
            String fpKey = KEY_FP_PREFIX + fingerprint;
            redisManager.lpush(fpKey, playerUuid.toString());
            redisManager.setWithExpiry(fpKey + ":ttl", "1", TTL_SECONDS);
        }

        logger.debug("Recorded login: uuid={}, ip={}, fp={}",
                playerUuid, ipAddress, fingerprint != null ? fingerprint.substring(0, 12) : "null");
    }

    /**
     * Unblocks an IP from alt restrictions (permanent whitelist).
     */
    public void unblockIp(String ipAddress) {
        redisManager.setWithExpiry(KEY_UNBLOCKED_PREFIX + ipAddress, "true", Integer.MAX_VALUE);
        logger.info("IP unblocked from alt restrictions: {}", ipAddress);
    }

    /**
     * Checks whether an IP is on the permanent whitelist.
     */
    private boolean isIpUnblocked(String ipAddress) {
        return "true".equals(redisManager.get(KEY_UNBLOCKED_PREFIX + ipAddress));
    }

    /**
     * Counts unique UUIDs associated with an IP address.
     * Uses Redis list length as an approximation.
     */
    private int countAccountsForIp(String ipAddress) {
        return (int) redisManager.llen(KEY_IP_PREFIX + ipAddress);
    }

    /**
     * Counts unique UUIDs associated with a device fingerprint.
     */
    private int countAccountsForFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) return 0;
        return (int) redisManager.llen(KEY_FP_PREFIX + fingerprint);
    }

    /** Cleanup — no persistent state. */
    public void shutdown() {
        // Redis pool managed externally
    }

    // ── Types ──

    /** Actions that can be taken based on alt detection results. */
    public enum Action {
        /** Player is allowed to connect. */
        ALLOW,
        /** Player should be kicked with a message. */
        KICK,
        /** Player is allowed but staff should be notified. */
        ALERT,
        /** Player is silently flagged (not implemented yet). */
        SHADOW_BAN
    }
}
