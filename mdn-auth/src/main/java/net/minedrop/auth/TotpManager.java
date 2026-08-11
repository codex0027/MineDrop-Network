package net.minedrop.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import net.minedrop.api.security.SecurityUtil;
import net.minedrop.core.redis.RedisManager;
import org.slf4j.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * TOTP (Time-based One-Time Password) manager — Google Authenticator / Authy compatible.
 * <p>
 * Generates RFC 6238 compliant TOTP secrets, stores them in Redis, and verifies
 * submitted codes with a ±1 time-step drift buffer (30 seconds tolerance).
 * <p>
 * Redis keys: {@code mdn:auth:totp:<uuid>} → JSON with secret, backup codes, ip_lock, created_at.
 */
public final class TotpManager {

    private static final String REDIS_KEY_PREFIX = "mdn:auth:totp:";
    private static final String REDIS_FAILED_ATTEMPTS = "mdn:auth:failed:";
    private static final String ISSUER = "MineDropNetwork";
    private static final String ALGORITHM = "HmacSHA1";
    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;
    private static final int DRIFT_STEPS = 1; // ±1 step = 30-second tolerance
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int FAILED_ATTEMPTS_TTL = 900; // 15 minutes

    private final RedisManager redisManager;
    private final ObjectMapper objectMapper;
    private final Logger logger;
    private final HikariDataSource dataSource;
    private final String encryptionKey;

    public TotpManager(RedisManager redisManager, ObjectMapper objectMapper, Logger logger,
                       HikariDataSource dataSource) {
        this.redisManager = redisManager;
        this.objectMapper = objectMapper;
        this.logger = logger;
        this.dataSource = dataSource;
        // Encryption key: env var → default. DO NOT use this default in production.
        this.encryptionKey = System.getenv().getOrDefault("MDN_TOTP_ENCRYPTION_KEY",
                "minedrop-dev-totp-key-2024-change-me");
    }

    // ── Secret generation ──

    /**
     * Generates a new TOTP secret for a player and returns the otpauth:// URL.
     *
     * @param playerUuid the player's UUID
     * @param username   the player's username (for the QR label)
     * @return the otpauth:// URL for QR code display
     */
    public String generateSecret(UUID playerUuid, String username) {
        // Generate 20 random bytes → Base32 secret
        byte[] secretBytes = new byte[20];
        new SecureRandom().nextBytes(secretBytes);
        String secret = base32Encode(secretBytes);

        // Generate 8 backup codes
        String[] backupCodes = new String[8];
        SecureRandom rng = new SecureRandom();
        for (int i = 0; i < 8; i++) {
            backupCodes[i] = String.format("%08d", rng.nextInt(100_000_000));
        }

        // Store in MySQL for persistence + Redis for fast reads
        TotpRecord record = new TotpRecord();
        record.secret = secret;
        record.backupCodes = String.join(",", backupCodes);
        record.ipLock = null;
        record.createdAt = Instant.now().getEpochSecond();

        // ── Encrypt secret + hash backup codes for secure storage (§52, §20) ──
        String encryptedSecret = encryptSecret(secret);
        String hashedBackupCodes = hashBackupCodes(backupCodes);

        // Store encrypted in MySQL + hashed in Redis for display purposes
        record.secret = secret; // Redis keeps plaintext for 24h TTL cache
        record.backupCodes = hashedBackupCodes; // Redis stores hashes too now

        // ── MySQL persistence (A-1) with encryption ──
        if (dataSource != null && !dataSource.isClosed()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO mdn_auth_totp (uuid, totp_secret, backup_codes, ip_lock, created_at) " +
                     "VALUES (?, ?, ?, NULL, NOW()) ON DUPLICATE KEY UPDATE totp_secret=?, backup_codes=?")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, encryptedSecret);
                ps.setString(3, hashedBackupCodes);
                ps.setString(4, encryptedSecret);
                ps.setString(5, hashedBackupCodes);
                ps.executeUpdate();
                logger.debug("TOTP record persisted to MySQL for {} (secret encrypted, codes hashed)", playerUuid);
            } catch (SQLException e) {
                logger.error("Failed to persist TOTP record to MySQL for {}", playerUuid, e);
            }
        }

        // ── Redis cache ──
        try {
            redisManager.setWithExpiry(
                    REDIS_KEY_PREFIX + playerUuid,
                    objectMapper.writeValueAsString(record),
                    86400 // 24h TTL — MySQL is the source of truth
            );
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize TOTP record for {}", playerUuid, e);
            return null;
        }

        // Build otpauth:// URL
        String label = URLEncoder.encode(ISSUER + ":" + username, StandardCharsets.UTF_8);
        return String.format("otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                label, secret, URLEncoder.encode(ISSUER, StandardCharsets.UTF_8), DIGITS, PERIOD_SECONDS);
    }

    // ── Verification ──

    /**
     * Verifies a 6-digit TOTP code against the stored secret.
     * Supports ±1 time-step drift (30 seconds of tolerance).
     *
     * @param playerUuid the player's UUID
     * @param code       the 6-digit code to verify
     * @return true if the code is valid
     */
    public boolean verifyCode(UUID playerUuid, int code) {
        TotpRecord record = getRecord(playerUuid);
        if (record == null || record.secret == null) {
            logger.debug("No TOTP secret found for {}", playerUuid);
            return false;
        }

        try {
            byte[] key = base32Decode(record.secret);
            long counter = Instant.now().getEpochSecond() / PERIOD_SECONDS;

            // Check with drift: current step ± DRIFT_STEPS
            for (int offset = -DRIFT_STEPS; offset <= DRIFT_STEPS; offset++) {
                int expected = generateTotp(key, counter + offset);
                if (expected == code) {
                    logger.debug("TOTP verification succeeded for {} (drift offset: {})", playerUuid, offset);
                    return true;
                }
            }

            logger.debug("TOTP verification failed for {} — invalid code", playerUuid);
            return false;
        } catch (Exception e) {
            logger.error("TOTP verification error for {}", playerUuid, e);
            return false;
        }
    }

    // ── Record management ──

    /**
     * Checks whether a TOTP secret already exists for a player.
     */
    public boolean hasExistingSecret(UUID playerUuid) {
        return getRecord(playerUuid) != null;
    }

    /**
     * Deletes a player's TOTP secret from both MySQL and Redis (admin reset).
     */
    public void deleteSecret(UUID playerUuid) {
        // Delete from Redis cache
        redisManager.delete(REDIS_KEY_PREFIX + playerUuid);

        // Delete from MySQL
        if (dataSource != null && !dataSource.isClosed()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM mdn_auth_totp WHERE uuid = ?")) {
                ps.setString(1, playerUuid.toString());
                int rows = ps.executeUpdate();
                logger.info("TOTP secret deleted for {} (MySQL rows: {}, Redis: ok)", playerUuid, rows);
            } catch (SQLException e) {
                logger.error("Failed to delete TOTP from MySQL for {} — Redis-only delete", playerUuid, e);
            }
        } else {
            logger.info("TOTP secret deleted for {} (Redis-only)", playerUuid);
        }
    }

    /**
     * Retrieves the stored TOTP record — Redis cache first, MySQL fallback.
     */
    private TotpRecord getRecord(UUID playerUuid) {
        // Try Redis cache first
        String json = redisManager.get(REDIS_KEY_PREFIX + playerUuid);
        if (json != null && !json.isEmpty()) {
            try {
                TotpRecord record = objectMapper.readValue(json, TotpRecord.class);
                if (record.secret != null) return record;
            } catch (JsonProcessingException e) {
                logger.debug("Corrupt Redis TOTP cache for {} — trying MySQL", playerUuid);
            }
        }

        // Fall back to MySQL
        if (dataSource != null && !dataSource.isClosed()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT totp_secret, backup_codes, ip_lock, UNIX_TIMESTAMP(created_at) " +
                     "FROM mdn_auth_totp WHERE uuid = ?")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        TotpRecord record = new TotpRecord();
                        record.secret = decryptSecret(rs.getString("totp_secret"));
                        record.backupCodes = rs.getString("backup_codes");
                        record.ipLock = rs.getString("ip_lock");
                        record.createdAt = rs.getLong(4);
                        // Repopulate Redis cache
                        redisManager.setWithExpiry(REDIS_KEY_PREFIX + playerUuid,
                                objectMapper.writeValueAsString(record), 86400);
                        return record;
                    }
                }
            } catch (SQLException | JsonProcessingException e) {
                logger.error("Failed to read TOTP from MySQL for {}", playerUuid, e);
            }
        }

        return null;
    }

    // ── IP lock enforcement (A-2) ──

    /**
     * Verifies a TOTP code AND enforces IP lock if configured.
     * <p>
     * If {@code enforceIpLock} is true and the stored record has an ipLock,
     * the player's current IP must match the stored ipLock.
     *
     * @param playerUuid  the player's UUID
     * @param code        the 6-digit code
     * @param currentIp   the player's current IP address
     * @param enforceIpLock whether to check IP lock
     * @return {@link IpVerifyResult} with success/failure and reason
     */
    public IpVerifyResult verifyCodeWithIpLock(UUID playerUuid, int code, String currentIp,
                                                boolean enforceIpLock) {
        // Rate-limit failed attempts
        String failKey = REDIS_FAILED_ATTEMPTS + playerUuid;
        String failCount = redisManager.get(failKey);
        int attempts = failCount != null ? Integer.parseInt(failCount) : 0;
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            logger.warn("TOTP brute-force lockout for {} ({} failed attempts)", playerUuid, attempts);
            return IpVerifyResult.RATE_LIMITED;
        }

        TotpRecord record = getRecord(playerUuid);
        if (record == null || record.secret == null) {
            return IpVerifyResult.NO_SECRET;
        }

        // ── IP lock check (A-2) ──
        if (enforceIpLock && record.ipLock != null && !record.ipLock.isEmpty()) {
            String storedIpPrefix = extractIpPrefix(record.ipLock);
            String currentIpPrefix = extractIpPrefix(currentIp);
            if (!storedIpPrefix.equals(currentIpPrefix)) {
                logger.warn("IP lock mismatch for {}: stored={}, current={}",
                        playerUuid, storedIpPrefix, currentIpPrefix);
                return IpVerifyResult.IP_MISMATCH;
            }
        }

        // ── TOTP code verification ──
        try {
            byte[] key = base32Decode(record.secret);
            long counter = Instant.now().getEpochSecond() / PERIOD_SECONDS;

            for (int offset = -DRIFT_STEPS; offset <= DRIFT_STEPS; offset++) {
                int expected = generateTotp(key, counter + offset);
                if (expected == code) {
                    // Success — clear failed attempts
                    redisManager.delete(failKey);
                    logger.debug("TOTP verification succeeded for {} (drift offset: {})", playerUuid, offset);
                    return IpVerifyResult.SUCCESS;
                }
            }

            // Failed — increment counter
            redisManager.setWithExpiry(failKey, String.valueOf(attempts + 1), FAILED_ATTEMPTS_TTL);
            logger.debug("TOTP verification failed for {} (attempt {}/{})",
                    playerUuid, attempts + 1, MAX_FAILED_ATTEMPTS);
            return IpVerifyResult.INVALID_CODE;
        } catch (Exception e) {
            logger.error("TOTP verification error for {}", playerUuid, e);
            return IpVerifyResult.ERROR;
        }
    }

    private static String extractIpPrefix(String ip) {
        if (ip == null) return "";
        int lastDot = ip.lastIndexOf('.');
        return lastDot > 0 ? ip.substring(0, lastDot) : ip;
    }

    // ── Backup code verification (A-5) ──

    /**
     * Verifies a backup recovery code against stored hashes (spec §20).
     * Backups are now SHA-256 hashed — we hash the input and compare.
     */
    public boolean verifyBackupCode(UUID playerUuid, String code) {
        TotpRecord record = getRecord(playerUuid);
        if (record == null || record.backupCodes == null) {
            return false;
        }

        String codeHash = sha256(code);
        Set<String> hashes = new HashSet<>(Arrays.asList(record.backupCodes.split(",")));
        if (!hashes.contains(codeHash)) {
            logger.debug("Invalid backup code attempt for {}", playerUuid);
            return false;
        }

        // Remove the used hash
        hashes.remove(codeHash);
        record.backupCodes = String.join(",", hashes);

        // Persist updated codes to both stores
        saveRecord(playerUuid, record);
        logger.info("Backup code consumed for {} — {} codes remaining", playerUuid, hashes.size());
        return true;
    }

    /**
     * Persists a TOTP record to both Redis and MySQL.
     */
    private void saveRecord(UUID playerUuid, TotpRecord record) {
        try {
            redisManager.setWithExpiry(REDIS_KEY_PREFIX + playerUuid,
                    objectMapper.writeValueAsString(record), 86400);
        } catch (JsonProcessingException e) {
            logger.error("Failed to save TOTP record to Redis for {}", playerUuid, e);
        }

        if (dataSource != null && !dataSource.isClosed()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "UPDATE mdn_auth_totp SET backup_codes = ?, ip_lock = ? WHERE uuid = ?")) {
                ps.setString(1, record.backupCodes);
                ps.setString(2, record.ipLock);
                ps.setString(3, playerUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Failed to update TOTP record in MySQL for {}", playerUuid, e);
            }
        }
    }

    /**
     * Updates the IP lock on a TOTP record (called on first successful 2FA verify).
     */
    public void updateIpLock(UUID playerUuid, String ipAddress) {
        TotpRecord record = getRecord(playerUuid);
        if (record != null) {
            record.ipLock = ipAddress;
            saveRecord(playerUuid, record);
            logger.info("IP lock set for {} → {}", playerUuid, extractIpPrefix(ipAddress));
        }
    }

    // ── TOTP algorithm (RFC 6238) ──

    /**
     * Generates a TOTP value for the given key and counter.
     */
    private int generateTotp(byte[] key, long counter)
            throws NoSuchAlgorithmException, InvalidKeyException {
        // Convert counter to big-endian bytes
        byte[] counterBytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            counterBytes[i] = (byte) (counter & 0xFF);
            counter >>= 8;
        }

        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(key, ALGORITHM));
        byte[] hash = mac.doFinal(counterBytes);

        // Dynamic truncation per RFC 4226
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);

        return binary % (int) Math.pow(10, DIGITS);
    }

    // ── Base32 encoding/decoding (RFC 4648) ──

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        // Padding to multiple of 8
        while (sb.length() % 8 != 0) {
            sb.append('=');
        }
        return sb.toString();
    }

    private byte[] base32Decode(String base32) {
        // Strip padding and uppercase
        base32 = base32.replace("=", "").toUpperCase();
        byte[] result = new byte[base32.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (char c : base32.toCharArray()) {
            int val = BASE32_ALPHABET.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return result;
    }

    /** Cleanup — Redis + MySQL pools managed externally. */
    public void shutdown() {
        // Resources managed by RedisManager and MDNAPI
    }

    // ── Encryption helpers (§52) ──

    private String encryptSecret(String secret) {
        try {
            return SecurityUtil.encryptAes(secret, encryptionKey);
        } catch (Exception e) {
            logger.error("Failed to encrypt TOTP secret — storing in plaintext as fallback", e);
            return "PLAINTEXT:" + secret; // Fallback marker so we know it's unencrypted
        }
    }

    private String decryptSecret(String encrypted) {
        if (encrypted == null) return null;
        if (encrypted.startsWith("PLAINTEXT:")) {
            return encrypted.substring(10); // Legacy unencrypted storage
        }
        try {
            return SecurityUtil.decryptAes(encrypted, encryptionKey);
        } catch (Exception e) {
            logger.error("Failed to decrypt TOTP secret — wrong key or corrupt data", e);
            return null;
        }
    }

    // ── Backup code hashing (§20) ──

    private String hashBackupCodes(String[] codes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < codes.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(sha256(codes[i]));
        }
        return sb.toString();
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ── IP Verify Result enum (A-2) ──

    public enum IpVerifyResult {
        SUCCESS,
        INVALID_CODE,
        IP_MISMATCH,
        RATE_LIMITED,
        NO_SECRET,
        ERROR
    }

    // ── Data classes ──

    /** Serialized TOTP record stored in Redis. */
    public static class TotpRecord {
        public String secret;
        public String backupCodes; // comma-separated
        public String ipLock;      // IP address to lock session to (null = disabled)
        public long createdAt;     // epoch seconds

        public TotpRecord() {}
    }
}
