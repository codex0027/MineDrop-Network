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
    private static final String KEY_SHADOW_BANNED = "mdn:auth:shadow_banned";
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
                fingerprint != null ? "present" : "null", fpCount, maxPerFp);

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
     * Sets 24-hour TTL on all tracking keys (A-6 fix).
     */
    public void recordLogin(UUID playerUuid, String ipAddress, String fingerprint) {
        // Track by IP with TTL (A-6 — was missing TTL, now auto-expires after 24h)
        String ipKey = KEY_IP_PREFIX + ipAddress;
        redisManager.lpush(ipKey, playerUuid.toString());
        redisManager.expire(ipKey, TTL_SECONDS);

        // Track by fingerprint with TTL
        if (fingerprint != null && !fingerprint.isEmpty()) {
            String fpKey = KEY_FP_PREFIX + fingerprint;
            redisManager.lpush(fpKey, playerUuid.toString());
            redisManager.expire(fpKey, TTL_SECONDS);
        }

        logger.debug("Recorded login: uuid={}, ip={}, fp={} (TTL: {}h)",
                playerUuid, ipAddress, fingerprint != null ? "present" : "null",
                TTL_SECONDS / 3600);
    }

    /**
     * Unblocks an IP from alt restrictions (permanent whitelist).
     */
    public void unblockIp(String ipAddress) {
        redisManager.setWithExpiry(KEY_UNBLOCKED_PREFIX + ipAddress, "true", Integer.MAX_VALUE);
        logger.info("IP unblocked from alt restrictions: {}", ipAddress);
    }

    /**
     * Clears all alt tracking data for an IP address.
     * Deletes both the alt account list AND the whitelist entry for this IP.
     * This is more aggressive than unblock — it wipes the slate clean.
     *
     * @param ipAddress the IP to clear
     * @return the number of UUIDs that were cleared from the alt list
     */
    public long clearIp(String ipAddress) {
        String ipKey = KEY_IP_PREFIX + ipAddress;
        long count = redisManager.llen(ipKey);

        // Delete alt tracking data
        redisManager.delete(ipKey);

        // Also remove from whitelist (so it can be re-tracked)
        redisManager.delete(KEY_UNBLOCKED_PREFIX + ipAddress);

        logger.info("IP alt tracking cleared for {} — {} UUIDs removed", ipAddress, count);
        return count;
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

    // ── Shadow ban (A-4) ──

    /**
     * Adds a player to the shadow-ban set (silent flagging).
     * Shadow-banned players can connect but are tracked for staff review.
     */
    public void shadowBan(UUID playerUuid) {
        redisManager.sadd(KEY_SHADOW_BANNED, playerUuid.toString());
        logger.warn("Player {} has been shadow-banned (silent flag)", playerUuid);
    }

    /**
     * Checks if a player is shadow-banned.
     */
    public boolean isShadowBanned(UUID playerUuid) {
        return redisManager.sismember(KEY_SHADOW_BANNED, playerUuid.toString());
    }

    /**
     * Returns the count of shadow-banned players.
     */
    public long getShadowBanCount() {
        return redisManager.scard(KEY_SHADOW_BANNED);
    }

    /** Cleanup — Redis pool managed externally. */
    public void shutdown() {
        // No persistent state
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
