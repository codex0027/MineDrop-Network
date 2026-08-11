package net.minedrop.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import net.minedrop.auth.command.AuthCommand;
import net.minedrop.auth.command.TwoFactorCommand;
import net.minedrop.bridge.BridgeManager;
import net.minedrop.core.redis.RedisManager;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central coordinator for all authentication operations.
 * <p>
 * Owns the {@link TotpManager}, {@link DeviceFingerprinter}, and {@link AltDetector}
 * subsystems and presents a single facade for the Velocity plugin to interact with.
 * <p>
 * Also tracks which players are currently in a pre-auth locked state (awaiting 2FA).
 */
public final class AuthManager {

    private final TotpManager totpManager;
    private final DeviceFingerprinter deviceFingerprinter;
    private final AltDetector altDetector;
    private final RedisManager redisManager;
    private final ObjectMapper objectMapper;
    private final Logger logger;

    /** Players currently locked awaiting 2FA (UUID → true). In-memory for speed. */
    private final Map<UUID, Boolean> lockedPlayers = new ConcurrentHashMap<>();

    public AuthManager(RedisManager redisManager, Logger logger) {
        this(redisManager, logger, null);
    }

    /**
     * Full constructor with optional MySQL datasource for TOTP persistence.
     */
    public AuthManager(RedisManager redisManager, Logger logger, HikariDataSource dataSource) {
        this.redisManager = redisManager;
        this.logger = logger;
        this.objectMapper = new ObjectMapper();
        this.totpManager = new TotpManager(redisManager, objectMapper, logger, dataSource);
        this.deviceFingerprinter = new DeviceFingerprinter();
        this.altDetector = new AltDetector(redisManager, logger);
    }

    // ── Lifecycle ──

    /** Registers with BridgeManager and initializes subsystems. */
    public void initialize() {
        // Register for signature verification
        BridgeManager.getInstance().register("MDN-Auth", getClass());
        logger.info("AuthManager initialized — subsystems ready.");
    }

    /** Clean shutdown. */
    public void shutdown() {
        lockedPlayers.clear();
        totpManager.shutdown();
        altDetector.shutdown();
        logger.info("AuthManager shut down.");
    }

    // ── TOTP / 2FA ──

    /**
     * Generates a new TOTP secret and returns the otpauth:// URL for QR code display.
     *
     * @param playerUuid the player's UUID
     * @param username   the player's username
     * @return the otpauth:// URL, or null if 2FA is already set up
     */
    public String setupTotp(UUID playerUuid, String username) {
        if (totpManager.hasExistingSecret(playerUuid)) {
            return null; // already set up
        }
        return totpManager.generateSecret(playerUuid, username);
    }

    /**
     * Verifies a TOTP code against the stored secret (simple — no IP lock).
     *
     * @param playerUuid the player's UUID
     * @param code       the 6-digit code
     * @return true if the code is valid (within ±1 time step drift)
     */
    public boolean verifyTotp(UUID playerUuid, int code) {
        return totpManager.verifyCode(playerUuid, code);
    }

    /**
     * Verifies a TOTP code with IP lock enforcement (A-2).
     *
     * @param playerUuid   the player's UUID
     * @param code         the 6-digit code
     * @param currentIp    the player's current IP address
     * @param enforceIpLock whether to check IP lock
     * @return the verification result
     */
    public TotpManager.IpVerifyResult verifyTotpWithIpLock(UUID playerUuid, int code,
                                                            String currentIp, boolean enforceIpLock) {
        return totpManager.verifyCodeWithIpLock(playerUuid, code, currentIp, enforceIpLock);
    }

    /**
     * Verifies a backup recovery code (A-5).
     *
     * @param playerUuid the player's UUID
     * @param code       the 8-digit backup code
     * @return true if valid and consumed
     */
    public boolean verifyBackupCode(UUID playerUuid, String code) {
        return totpManager.verifyBackupCode(playerUuid, code);
    }

    /**
     * Updates the IP lock on a TOTP record after first successful 2FA.
     */
    public void updateTotpIpLock(UUID playerUuid, String ipAddress) {
        totpManager.updateIpLock(playerUuid, ipAddress);
    }

    /**
     * Resets a player's TOTP secret (admin operation).
     */
    public void resetTotp(UUID playerUuid) {
        totpManager.deleteSecret(playerUuid);
    }

    /**
     * Resolves a username to UUID for admin operations (A-3).
     * Tries online players first, then a Redis username→UUID mapping.
     *
     * @param username     the target username (case-insensitive)
     * @param proxyGetter  function to look up online players by name
     * @return the resolved UUID, or empty if not found
     */
    public Optional<UUID> resolveUsername(String username,
                                           java.util.function.Function<String, Optional<com.velocitypowered.api.proxy.Player>> proxyGetter) {
        // Try online players first
        var online = proxyGetter.apply(username);
        if (online.isPresent()) {
            return Optional.of(online.get().getUniqueId());
        }

        // Try Redis username→UUID mapping (populated on every login)
        String uuidStr = redisManager.get("mdn:auth:username:" + username.toLowerCase());
        if (uuidStr != null && !uuidStr.isEmpty()) {
            try {
                return Optional.of(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException e) {
                logger.warn("Corrupt UUID in Redis for username '{}': {}", username, uuidStr);
            }
        }

        return Optional.empty();
    }

    /**
     * Records a username→UUID mapping in Redis for offline UUID resolution.
     */
    public void recordUsernameMapping(String username, UUID uuid) {
        redisManager.setWithExpiry("mdn:auth:username:" + username.toLowerCase(),
                uuid.toString(), 86400 * 30); // 30 days
    }

    /**
     * Checks whether a player has 2FA configured.
     */
    public boolean hasTotpConfigured(UUID playerUuid) {
        return totpManager.hasExistingSecret(playerUuid);
    }

    // ── Pre-auth lockdown ──

    /**
     * Locks a player in the pre-auth state.
     * The plugin should blind and freeze the player after calling this.
     */
    public void lockPlayer(UUID playerUuid) {
        lockedPlayers.put(playerUuid, true);
        redisManager.setWithExpiry("mdn:auth:locked:" + playerUuid, "true", 300);
    }

    /**
     * Unlocks a player after successful 2FA verification.
     */
    public void unlockPlayer(UUID playerUuid) {
        lockedPlayers.remove(playerUuid);
        redisManager.delete("mdn:auth:locked:" + playerUuid);
    }

    /**
     * Checks whether a player is currently in pre-auth lock state.
     */
    public boolean isPlayerLocked(UUID playerUuid) {
        return lockedPlayers.containsKey(playerUuid)
                || "true".equals(redisManager.get("mdn:auth:locked:" + playerUuid));
    }

    // ── Device fingerprinting ──

    /**
     * Creates a device fingerprint for the connecting player.
     *
     * @param playerUuid    the player's UUID
     * @param ipAddress     the player's IP address
     * @param clientBrand   the client brand string (from handshake)
     * @param protocolVersion the client protocol version
     * @return a {@link DeviceFingerprinter.Fingerprint}
     */
    public DeviceFingerprinter.Fingerprint fingerprint(
            UUID playerUuid, String ipAddress, String clientBrand, int protocolVersion) {
        return deviceFingerprinter.create(playerUuid, ipAddress, clientBrand, protocolVersion);
    }

    // ── Shadow ban (A-4) ──

    /**
     * Shadow-bans a player (silent flagging).
     */
    public void shadowBan(UUID playerUuid) {
        altDetector.shadowBan(playerUuid);
    }

    /**
     * Checks if a player is shadow-banned.
     */
    public boolean isShadowBanned(UUID playerUuid) {
        return altDetector.isShadowBanned(playerUuid);
    }

    // ── Alt detection ──

    /**
     * Checks whether a player exceeds alt account limits and returns the recommended action.
     *
     * @param playerUuid  the player's UUID
     * @param ipAddress   the player's IP address
     * @param fingerprint the device fingerprint string
     * @param maxPerIp    max accounts allowed per IP
     * @param maxPerFp    max accounts allowed per fingerprint
     * @return the action to take: ALLOW, KICK, or ALERT
     */
    public AltDetector.Action checkAltLimits(
            UUID playerUuid, String ipAddress, String fingerprint,
            int maxPerIp, int maxPerFp) {
        return altDetector.check(playerUuid, ipAddress, fingerprint, maxPerIp, maxPerFp);
    }

    /**
     * Registers a successful login for alt tracking.
     */
    public void recordLogin(UUID playerUuid, String ipAddress, String fingerprint) {
        altDetector.recordLogin(playerUuid, ipAddress, fingerprint);
    }

    /**
     * Unblocks an IP address from alt restrictions.
     */
    public void unblockIp(String ipAddress) {
        altDetector.unblockIp(ipAddress);
    }

    // ── Service secrets (private lobbies) ──

    /**
     * Validates a service-secret token for private lobby access.
     * Currently a stub — always returns false until private lobby system is built.
     */
    public boolean validateServiceSecret(String teamSecret, String serverToken) {
        // TODO: Implement when private lobby system is built (MDN-SAM)
        logger.debug("Service secret validation requested (stub)");
        return false;
    }

    // ── Getters ──

    public TotpManager getTotpManager() { return totpManager; }
    public AltDetector getAltDetector() { return altDetector; }
    public Map<UUID, Boolean> getLockedPlayers() { return lockedPlayers; }

    /** Creates the {@link TwoFactorCommand} wired to this manager, with post-verify callback + proxy access. */
    public TwoFactorCommand createTwoFactorCommand(
            java.util.function.Consumer<com.velocitypowered.api.proxy.Player> onVerified,
            java.util.function.Function<String, Optional<com.velocitypowered.api.proxy.Player>> playerResolver,
            boolean enforceIpLock) {
        return new TwoFactorCommand(this, logger, onVerified, playerResolver, enforceIpLock);
    }

    /** Creates the {@link AuthCommand} wired to this manager. */
    public AuthCommand createAuthCommand() {
        return new AuthCommand(this, logger);
    }
}
