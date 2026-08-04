package net.minedrop.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilTest {

    @Test
    @DisplayName("SHA-256 should produce consistent 64-char hex hash")
    void sha256HexConsistency() {
        String hash = SecurityUtil.sha256Hex("hello world");
        assertEquals(64, hash.length());
        assertEquals(hash, SecurityUtil.sha256Hex("hello world"));
    }

    @Test
    @DisplayName("SHA-256 of different inputs should differ")
    void sha256HexDifferentInputs() {
        String hash1 = SecurityUtil.sha256Hex("hello");
        String hash2 = SecurityUtil.sha256Hex("world");
        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("HMAC-SHA256 should produce consistent signatures")
    void hmacSha256Consistency() {
        String sig1 = SecurityUtil.hmacSha256("message", "secret");
        String sig2 = SecurityUtil.hmacSha256("message", "secret");
        assertEquals(sig1, sig2);
    }

    @Test
    @DisplayName("HMAC-SHA256 with different secrets should differ")
    void hmacSha256DifferentSecrets() {
        String sig1 = SecurityUtil.hmacSha256("message", "secret1");
        String sig2 = SecurityUtil.hmacSha256("message", "secret2");
        assertNotEquals(sig1, sig2);
    }

    @Test
    @DisplayName("AES/GCM encrypt-decrypt roundtrip")
    void aesRoundtrip() throws Exception {
        String plaintext = "Hello, MineDrop Network! This is a secret message.";
        String encrypted = SecurityUtil.encryptAes(plaintext, "my-password");
        String decrypted = SecurityUtil.decryptAes(encrypted, "my-password");
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("AES/GCM with wrong password should fail")
    void aesWrongPassword() {
        assertThrows(Exception.class, () -> {
            String encrypted = SecurityUtil.encryptAes("secret", "password1");
            SecurityUtil.decryptAes(encrypted, "wrong-password");
        });
    }

    @Test
    @DisplayName("AES/GCM tampered ciphertext should fail")
    void aesTamperedCiphertext() {
        assertThrows(Exception.class, () -> {
            String encrypted = SecurityUtil.encryptAes("secret", "password");
            SecurityUtil.decryptAes(encrypted + "tampered", "password");
        });
    }

    @Test
    @DisplayName("AES/GCM empty string roundtrip")
    void aesEmptyString() throws Exception {
        String encrypted = SecurityUtil.encryptAes("", "password");
        String decrypted = SecurityUtil.decryptAes(encrypted, "password");
        assertEquals("", decrypted);
    }
}
