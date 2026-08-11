package net.minedrop.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import net.minedrop.api.security.SecurityUtil;
import net.minedrop.auth.command.AuthCommand;
import net.minedrop.auth.command.LoginCommand;
import net.minedrop.auth.command.PasswordCommand;
import net.minedrop.auth.command.RegisterCommand;
import net.minedrop.auth.command.TwoFactorCommand;
import net.minedrop.bridge.BridgeManager;
import net.minedrop.core.redis.RedisManager;
import org.slf4j.Logger;

import java.util.Base64;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
    private final PasswordHasher passwordHasher;
    private final SessionManager sessionManager;
    private final RedisManager redisManager;
    private final ObjectMapper objectMapper;
    private final Logger logger;
    private final HikariDataSource dataSource;

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
        this.dataSource = dataSource;
        this.passwordHasher = new PasswordHasher(logger);
        this.sessionManager = new SessionManager(redisManager, objectMapper, logger);
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
        passwordHasher.shutdown();
        sessionManager.shutdown();
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

    // ── Account management (password-based auth) ──

    /**
     * Checks whether an MDN account exists for this UUID.
     */
    public boolean isRegistered(UUID playerUuid) {
        if (dataSource == null || dataSource.isClosed()) return false;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT 1 FROM mdn_accounts WHERE uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.debug("Account lookup for {} failed (DB unavailable): {}", playerUuid, e.getMessage());
            return false;
        }
    }

    /**
     * Registers a new MDN account with Argon2id password hash.
     */
    public RegistrationResult register(UUID playerUuid, String username, char[] password, String ip) {
        if (dataSource == null || dataSource.isClosed()) {
            return RegistrationResult.DATABASE_ERROR;
        }

        // Check for duplicate
        if (isRegistered(playerUuid)) {
            return RegistrationResult.ALREADY_REGISTERED;
        }

        String hash = passwordHasher.hash(password);
        if (hash == null) {
            return RegistrationResult.DATABASE_ERROR;
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO mdn_accounts (uuid, username, status, password_hash, last_ip) " +
                 "VALUES (?, ?, 'ACTIVE', ?, ?)")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, username);
            ps.setString(3, hash);
            ps.setString(4, ip);
            ps.executeUpdate();
            logger.info("Account registered: {} ({})", username, playerUuid);
            return RegistrationResult.SUCCESS;
        } catch (SQLException e) {
            // Check for duplicate key violation
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                return RegistrationResult.ALREADY_REGISTERED;
            }
            logger.error("Failed to register account for {}", playerUuid, e);
            return RegistrationResult.DATABASE_ERROR;
        }
    }

    /**
     * Verifies a password against the stored Argon2id hash.
     */
    public LoginResult verifyPassword(UUID playerUuid, char[] password, String ip) {
        if (dataSource == null || dataSource.isClosed()) {
            return LoginResult.DATABASE_ERROR;
        }

        // Check rate limit
        if (isLoginRateLimited(playerUuid, ip)) {
            return LoginResult.RATE_LIMITED;
        }

        // Load account
        String storedHash = null;
        String status = null;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT password_hash, status FROM mdn_accounts WHERE uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    storedHash = rs.getString("password_hash");
                    status = rs.getString("status");
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load account for {}", playerUuid, e);
            return LoginResult.DATABASE_ERROR;
        }

        if (storedHash == null) {
            return LoginResult.INVALID_CREDENTIALS;
        }

        // Check account status
        if ("SUSPENDED".equals(status)) {
            return LoginResult.ACCOUNT_SUSPENDED;
        }

        // Verify password
        if (!passwordHasher.verify(storedHash, password)) {
            return LoginResult.INVALID_CREDENTIALS;
        }

        // Update last login
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE mdn_accounts SET last_login_at = NOW(), last_ip = ? WHERE uuid = ?")) {
            ps.setString(1, ip);
            ps.setString(2, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to update last_login for {}", playerUuid, e);
        }

        return LoginResult.SUCCESS;
    }

    /**
     * Checks whether 2FA is required for this player.
     * True if: player has TOTP configured OR is in a force-2FA permission group.
     *
     * @param playerUuid          the player's UUID
     * @param hasForcePermission  function that returns true if player has a force-2FA permission
     */
    public boolean isTotpRequired(UUID playerUuid, java.util.function.BooleanSupplier hasForcePermission) {
        return hasTotpConfigured(playerUuid) || hasForcePermission.getAsBoolean();
    }

    /**
     * Creates an authenticated session and publishes AUTH_UPDATE.
     */
    public SessionManager.Session createAuthenticatedSession(UUID playerUuid, String ip) {
        SessionManager.Session session = sessionManager.createSession(playerUuid, ip);
        if (session != null) {
            sessionManager.publishAuthUpdate(playerUuid, true);
        }
        return session;
    }

    /**
     * Checks if a player has an active authenticated session.
     */
    public boolean hasActiveSession(UUID playerUuid) {
        return sessionManager.hasActiveSession(playerUuid);
    }

    /**
     * Revokes all sessions for a player (used on password change/reset/suspend).
     */
    public void revokeAllSessions(UUID playerUuid, String reason) {
        sessionManager.revokeAllSessions(playerUuid, reason);
        sessionManager.publishAuthUpdate(playerUuid, false);
    }

    // ── Login rate limiting ──

    private static final String RATE_LOGIN_KEY = "mdn:auth:rate:login:";
    private static final String RATE_LOGIN_IP_KEY = "mdn:auth:rate:login-ip:";
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final int LOGIN_FAILURE_TTL = 300; // 5 min

    public boolean isLoginRateLimited(UUID playerUuid, String ip) {
        String uuidCount = redisManager.get(RATE_LOGIN_KEY + playerUuid);
        String ipCount = redisManager.get(RATE_LOGIN_IP_KEY + ip);
        int uuidFails = uuidCount != null ? Integer.parseInt(uuidCount) : 0;
        int ipFails = ipCount != null ? Integer.parseInt(ipCount) : 0;
        return uuidFails >= MAX_LOGIN_FAILURES || ipFails >= MAX_LOGIN_FAILURES;
    }

    public void recordFailedLogin(UUID playerUuid, String ip) {
        incrementRate(RATE_LOGIN_KEY + playerUuid, LOGIN_FAILURE_TTL);
        incrementRate(RATE_LOGIN_IP_KEY + ip, LOGIN_FAILURE_TTL);
    }

    private void incrementRate(String key, int ttl) {
        String val = redisManager.get(key);
        int count = val != null ? Integer.parseInt(val) : 0;
        redisManager.setWithExpiry(key, String.valueOf(count + 1), ttl);
    }

    // ── Account suspension ──

    /**
     * Suspends an account — prevents login, revokes sessions.
     */
    public boolean suspendAccount(UUID playerUuid, String reason) {
        if (dataSource == null || dataSource.isClosed()) return false;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE mdn_accounts SET status = 'SUSPENDED' WHERE uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            int rows = ps.executeUpdate();
            if (rows > 0) {
                revokeAllSessions(playerUuid, "Account suspended: " + reason);
                logger.warn("Account suspended: {} — {}", playerUuid, reason);
                return true;
            }
            return false;
        } catch (SQLException e) {
            logger.error("Failed to suspend account {}", playerUuid, e);
            return false;
        }
    }

    /**
     * Unsuspends an account.
     */
    public boolean unsuspendAccount(UUID playerUuid) {
        if (dataSource == null || dataSource.isClosed()) return false;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE mdn_accounts SET status = 'ACTIVE' WHERE uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            int rows = ps.executeUpdate();
            if (rows > 0) {
                logger.info("Account unsuspended: {}", playerUuid);
                return true;
            }
            return false;
        } catch (SQLException e) {
            logger.error("Failed to unsuspend account {}", playerUuid, e);
            return false;
        }
    }

    /**
     * Changes a password (requires current password).
     */
    public boolean changePassword(UUID playerUuid, char[] currentPassword, char[] newPassword) {
        if (dataSource == null || dataSource.isClosed()) return false;

        // Verify current password first
        String storedHash = null;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT password_hash FROM mdn_accounts WHERE uuid = ? AND status = 'ACTIVE'")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    storedHash = rs.getString("password_hash");
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load password for change: {}", playerUuid, e);
            return false;
        }

        if (storedHash == null || !passwordHasher.verify(storedHash, currentPassword)) {
            return false;
        }

        // Hash and update new password
        String newHash = passwordHasher.hash(newPassword);
        if (newHash == null) return false;

        return updatePasswordHash(playerUuid, newHash);
    }

    /**
     * Hashes a password (exposed for PasswordCommand recovery flow).
     */
    public String hashPassword(char[] password) {
        return passwordHasher.hash(password);
    }

    /**
     * Updates the password hash in MySQL directly (recovery flow, no current password needed).
     */
    public boolean updatePasswordHash(UUID playerUuid, String newHash) {
        if (dataSource == null || dataSource.isClosed()) return false;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE mdn_accounts SET password_hash = ?, password_changed_at = NOW(), password_version = password_version + 1 WHERE uuid = ?")) {
            ps.setString(1, newHash);
            ps.setString(2, playerUuid.toString());
            int rows = ps.executeUpdate();
            if (rows > 0) {
                revokeAllSessions(playerUuid, "Password reset — all sessions revoked");
                logger.info("Password reset (force) for {}", playerUuid);
                return true;
            }
            return false;
        } catch (SQLException e) {
            logger.error("Failed to update password for {}", playerUuid, e);
            return false;
        }
    }

    // ── Admin recovery (spec §56-57) ──

    /**
     * Generates a one-time recovery token for manual admin password reset.
     * Token expires in 15 minutes. Returns the raw token (show to admin, never log).
     */
    public String generateRecoveryToken(UUID playerUuid, String adminName) {
        SecureRandom rng = new SecureRandom();
        byte[] bytes = new byte[16];
        rng.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = SecurityUtil.sha256Hex(rawToken);

        // Store token hash in Redis with 15-min TTL
        redisManager.setWithExpiry("mdn:auth:recovery:" + playerUuid, tokenHash, 900);

        logger.info("Recovery token generated for {} by {} (expires in 15 min)", playerUuid, adminName);
        return rawToken;
    }

    /**
     * Validates a recovery token and resets the password.
     */
    public boolean validateRecoveryToken(UUID playerUuid, String rawToken, char[] newPassword) {
        String storedHash = redisManager.get("mdn:auth:recovery:" + playerUuid);
        if (storedHash == null) return false;

        String inputHash = SecurityUtil.sha256Hex(rawToken);
        if (!inputHash.equals(storedHash)) return false;

        // Token is valid — delete it (one-time use)
        redisManager.delete("mdn:auth:recovery:" + playerUuid);

        // Reset password
        String newHash = passwordHasher.hash(newPassword);
        if (newHash == null) return false;

        if (updatePasswordHash(playerUuid, newHash)) {
            // Also clear TOTP (spec: recovery resets all factors)
            totpManager.deleteSecret(playerUuid);
            logger.info("Recovery completed for {} — password reset + TOTP cleared", playerUuid);
            return true;
        }
        return false;
    }

    // ── Audit logging (spec §80-81) ──

    /**
     * Records an audit event in the mdn_auth_audit table.
     * Never logs passwords, secrets, or tokens.
     */
    public void auditEvent(UUID playerUuid, String eventType, String sourceIp, String metadata) {
        if (dataSource == null || dataSource.isClosed()) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO mdn_auth_audit (uuid, event_type, source_ip, metadata_json) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, eventType);
            ps.setString(3, sourceIp);
            ps.setString(4, metadata);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.debug("Audit event failed (non-critical): {} for {}", eventType, playerUuid);
        }
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

    /**
     * Clears all alt tracking data for an IP address.
     * Deletes the alt list AND removes the whitelist entry.
     *
     * @return the number of UUIDs that were cleared
     */
    public long clearIp(String ipAddress) {
        return altDetector.clearIp(ipAddress);
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
    public SessionManager getSessionManager() { return sessionManager; }
    public Map<UUID, Boolean> getLockedPlayers() { return lockedPlayers; }

    /** Creates the {@link TwoFactorCommand} wired to this manager, with post-verify callback + proxy access. */
    public TwoFactorCommand createTwoFactorCommand(
            java.util.function.Consumer<com.velocitypowered.api.proxy.Player> onVerified,
            java.util.function.Function<String, Optional<com.velocitypowered.api.proxy.Player>> playerResolver,
            boolean enforceIpLock) {
        return new TwoFactorCommand(this, logger, onVerified, playerResolver, enforceIpLock);
    }

    /** Creates the {@link AuthCommand} wired to this manager, with player resolver for suspend/unsuspend. */
    public AuthCommand createAuthCommand(
            java.util.function.Function<String, Optional<com.velocitypowered.api.proxy.Player>> playerResolver) {
        return new AuthCommand(this, logger, playerResolver);
    }

    /** Creates the {@link RegisterCommand} with post-auth callback. */
    public RegisterCommand createRegisterCommand(
            java.util.function.Consumer<com.velocitypowered.api.proxy.Player> onAuthenticated) {
        return new RegisterCommand(this, logger, onAuthenticated);
    }

    /** Creates the {@link PasswordCommand} for /password change|reset. */
    public PasswordCommand createPasswordCommand(boolean enforceIpLock, Runnable onPasswordChanged) {
        return new PasswordCommand(this, logger, enforceIpLock, onPasswordChanged);
    }

    /** Creates the {@link LoginCommand} with post-auth + post-password-verify + 2FA check callbacks. */
    public LoginCommand createLoginCommand(
            java.util.function.Consumer<com.velocitypowered.api.proxy.Player> onAuthenticated,
            java.util.function.Consumer<com.velocitypowered.api.proxy.Player> onPasswordVerified,
            java.util.function.Predicate<com.velocitypowered.api.proxy.Player> isForce2fa) {
        return new LoginCommand(this, logger, onAuthenticated, onPasswordVerified, isForce2fa);
    }

    // ── Result enums ──

    public enum RegistrationResult {
        SUCCESS,
        ALREADY_REGISTERED,
        ACCOUNT_SUSPENDED,
        DATABASE_ERROR
    }

    public enum LoginResult {
        SUCCESS,
        INVALID_CREDENTIALS,
        ACCOUNT_SUSPENDED,
        RATE_LIMITED,
        DATABASE_ERROR
    }
}
