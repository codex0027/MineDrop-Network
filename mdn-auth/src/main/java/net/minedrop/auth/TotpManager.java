package net.minedrop.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.minedrop.core.redis.RedisManager;
import org.slf4j.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
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
    private static final String ISSUER = "MineDropNetwork";
    private static final String ALGORITHM = "HmacSHA1";
    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;
    private static final int DRIFT_STEPS = 1; // ±1 step = 30-second tolerance

    private final RedisManager redisManager;
    private final ObjectMapper objectMapper;
    private final Logger logger;

    public TotpManager(RedisManager redisManager, ObjectMapper objectMapper, Logger logger) {
        this.redisManager = redisManager;
        this.objectMapper = objectMapper;
        this.logger = logger;
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

        // Store in Redis (persistent — no TTL, survives restarts)
        TotpRecord record = new TotpRecord();
        record.secret = secret;
        record.backupCodes = String.join(",", backupCodes);
        record.ipLock = null;
        record.createdAt = Instant.now().getEpochSecond();

        try {
            redisManager.setWithExpiry(
                    REDIS_KEY_PREFIX + playerUuid,
                    objectMapper.writeValueAsString(record),
                    Integer.MAX_VALUE // effectively persistent
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
     * Deletes a player's TOTP secret (admin reset).
     */
    public void deleteSecret(UUID playerUuid) {
        redisManager.delete(REDIS_KEY_PREFIX + playerUuid);
        logger.info("TOTP secret deleted for {}", playerUuid);
    }

    /**
     * Retrieves the stored TOTP record from Redis.
     */
    private TotpRecord getRecord(UUID playerUuid) {
        String json = redisManager.get(REDIS_KEY_PREFIX + playerUuid);
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, TotpRecord.class);
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize TOTP record for {}", playerUuid, e);
            return null;
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

    /** Cleanup — no-op currently. */
    public void shutdown() {
        // Redis pool managed externally
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
