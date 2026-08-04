package net.minedrop.api.security;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Cryptographic utilities shared across all MineDrop plugins.
 * <p>
 * Used by {@code MDN-Bridge} for plugin signature verification,
 * handshake HMAC computation, and secure key exchange.
 */
public final class SecurityUtil {

    private SecurityUtil() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    /** ThreadLocal MessageDigest to avoid reinstantiation on every call (fixes M-2). */
    private static final ThreadLocal<MessageDigest> SHA256_DIGEST =
            ThreadLocal.withInitial(() -> {
                try {
                    return MessageDigest.getInstance("SHA-256");
                } catch (Exception e) {
                    throw new RuntimeException("SHA-256 not available", e);
                }
            });

    /**
     * Computes the SHA-256 hex hash of an input string.
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = SHA256_DIGEST.get();
            digest.reset();
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hashing failed", e);
        }
    }

    /**
     * Computes an HMAC-SHA256 signature for a message using a secret key.
     */
    public static String hmacSha256(String message, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 computation failed", e);
        }
    }

    /**
     * AES/GCM decrypts a Base64-encoded ciphertext (prepended with 12-byte IV).
     * Format: Base64(IV || ciphertext)
     */
    public static String decryptAes(String encryptedBase64, String secret) throws Exception {
        byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8));
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

        byte[] combined = Base64.getDecoder().decode(encryptedBase64);
        byte[] iv = new byte[12];
        System.arraycopy(combined, 0, iv, 0, 12);
        byte[] ciphertext = new byte[combined.length - 12];
        System.arraycopy(combined, 12, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new javax.crypto.spec.GCMParameterSpec(128, iv));
        byte[] decrypted = cipher.doFinal(ciphertext);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * AES/GCM encrypts a plaintext string and returns Base64-encoded (IV + ciphertext).
     */
    public static String encryptAes(String plaintext, String secret) throws Exception {
        byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8));
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(combined);
    }
}
