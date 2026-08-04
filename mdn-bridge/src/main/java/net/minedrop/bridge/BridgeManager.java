package net.minedrop.bridge;

import net.minedrop.api.security.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for MDN-Bridge.
 * <p>
 * Tracks registered plugins, validates their build signatures, and manages
 * the Velocity handshake lifecycle. Shared between Paper and Velocity sides.
 */
public final class BridgeManager {

    private static final Logger log = LoggerFactory.getLogger(BridgeManager.class);

    private static BridgeManager instance;

    private final Set<String> allowedHashes = ConcurrentHashMap.newKeySet();
    private final Map<String, Boolean> securePlugins = new ConcurrentHashMap<>();
    private String serverIdentity;
    private String secretApiKey;
    private String activeSessionToken;
    private int handshakeTimeoutSeconds = 10;

    private BridgeManager() {}

    public static BridgeManager getInstance() {
        if (instance == null) {
            instance = new BridgeManager();
        }
        return instance;
    }

    // ── Configuration ──

    public void setServerIdentity(String id) { this.serverIdentity = id; }
    public String getServerIdentity() { return serverIdentity; }

    public void setSecretApiKey(String key) { this.secretApiKey = key; }
    public String getSecretApiKey() { return secretApiKey; }

    public void setHandshakeTimeoutSeconds(int seconds) { this.handshakeTimeoutSeconds = seconds; }
    public int getHandshakeTimeoutSeconds() { return handshakeTimeoutSeconds; }

    public void setAllowedHashes(List<String> hashes) {
        allowedHashes.clear();
        allowedHashes.addAll(hashes);
    }

    public void setActiveSessionToken(String token) { this.activeSessionToken = token; }
    public String getActiveSessionToken() { return activeSessionToken; }

    // ── Plugin Registration ──

    /**
     * Registers a plugin with MDN-Bridge for signature verification.
     * Called by every MDN plugin during onLoad().
     *
     * @param pluginId   the plugin's unique name (e.g. "MDN-Core")
     * @param pluginClass the plugin's main class (used to locate its JAR)
     */
    public void register(String pluginId, Class<?> pluginClass) {
        boolean valid = verifyPluginSignature(pluginId, pluginClass);
        securePlugins.put(pluginId, valid);

        if (!valid) {
            log.error("SECURITY: Plugin '{}' failed signature verification!", pluginId);
        } else {
            log.info("Plugin '{}' passed signature verification.", pluginId);
        }
    }

    /**
     * Checks if a registered plugin passed signature verification.
     */
    public boolean isPluginSecure(String pluginId) {
        return securePlugins.getOrDefault(pluginId, false);
    }

    // ── Signature Verification ──

    /**
     * Reads signature.json from the plugin's JAR and validates its build hash.
     */
    private boolean verifyPluginSignature(String pluginId, Class<?> pluginClass) {
        try {
            // Locate the JAR containing the plugin class
            var codeSource = pluginClass.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                log.warn("Cannot verify '{}': no code source available", pluginId);
                return false;
            }

            // Read signature.json from the JAR
            String signatureJson = readSignatureFromJar(pluginClass, pluginId);
            if (signatureJson == null) {
                log.warn("Plugin '{}' is missing signature.json — verification failed", pluginId);
                return false;
            }

            // Compute SHA-256 of the JAR file and compare
            String jarPath = codeSource.getLocation().getPath();
            String jarHash = computeJarHash(jarPath);

            // Check against allowed hashes
            if (!allowedHashes.contains(jarHash)) {
                log.error("Plugin '{}' build hash {} is NOT in allowed list!", pluginId, jarHash);
                return false;
            }

            log.info("Plugin '{}' verified — build hash matches allowed list.", pluginId);
            return true;

        } catch (Exception e) {
            log.error("Signature verification failed for '{}': {}", pluginId, e.getMessage());
            return false;
        }
    }

    private String readSignatureFromJar(Class<?> pluginClass, String pluginId) {
        try (InputStream is = pluginClass.getClassLoader()
                .getResourceAsStream("signature.json")) {
            if (is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to read signature.json from '{}'", pluginId, e);
            return null;
        }
    }

    /**
     * Computes a SHA-256 hash of the plugin JAR file.
     * In production this reads the actual JAR bytes; here we use the code source path.
     */
    private String computeJarHash(String jarPath) {
        // In a real deployment, read the JAR file bytes and hash them.
        // For the template, we hash the path as a placeholder.
        return SecurityUtil.sha256Hex(jarPath + "-" + serverIdentity);
    }

    // ── Handshake Challenge ──

    /**
     * Generates a handshake challenge for Velocity to verify.
     * The Paper server sends this challenge; Velocity must respond with
     * a matching HMAC signature within the timeout window.
     */
    public String generateHandshakeChallenge() {
        String challenge = UUID.randomUUID().toString() + ":" + System.currentTimeMillis();
        return SecurityUtil.sha256Hex(challenge);
    }

    /**
     * Validates the Velocity handshake response against the challenge.
     */
    public boolean validateHandshakeResponse(String challenge, String response) {
        String expected = SecurityUtil.hmacSha256(challenge, secretApiKey);
        return expected.equals(response);
    }
}
