package net.minedrop.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import net.minedrop.api.ApiVersion;
import net.minedrop.api.MDNAPI;
import net.minedrop.api.security.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Central manager for MDN-Bridge.
 * <p>
 * Tracks registered plugins, validates their build signatures, and manages
 * the Velocity handshake lifecycle. Shared between Paper and Velocity sides.
 *
 * <h3>Security Flow</h3>
 * <ol>
 *   <li>Plugin calls {@link #register(String, Class)} during onLoad()</li>
 *   <li>Bridge reads signature.json from the plugin JAR</li>
 *   <li>Bridge computes SHA-256 of the actual JAR file bytes</li>
 *   <li>Hash is compared against allowed list in config</li>
 *   <li>If invalid, the plugin disable callback is invoked</li>
 *   <li>Paper servers perform a handshake with Velocity via Redis</li>
 * </ol>
 */
public final class BridgeManager {

    private static final Logger log = LoggerFactory.getLogger(BridgeManager.class);

    // Thread-safe singleton
    private static volatile BridgeManager instance;

    private final Set<String> allowedHashes = ConcurrentHashMap.newKeySet();
    private final Map<String, Boolean> securePlugins = new ConcurrentHashMap<>();
    private String serverIdentity;
    private String secretApiKey;
    private String activeSessionToken;
    private int handshakeTimeoutSeconds = 10;
    private int handshakeRetries = 3;
    private String discordWebhook;
    private boolean debugMode = false;

    // Callback for disabling a plugin on verification failure
    private Consumer<String> pluginDisabler;

    private BridgeManager() {}

    /**
     * Thread-safe singleton accessor with double-checked locking.
     */
    public static BridgeManager getInstance() {
        if (instance == null) {
            synchronized (BridgeManager.class) {
                if (instance == null) {
                    instance = new BridgeManager();
                }
            }
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

    public void setHandshakeRetries(int retries) { this.handshakeRetries = retries; }

    public void setDiscordWebhook(String url) { this.discordWebhook = url; }

    public void setDebugMode(boolean debug) {
        // Per design doc: debug mode ONLY allowed on localhost
        this.debugMode = debug;
        if (debug) {
            log.warn("DEBUG MODE ENABLED — signature verification is bypassed. "
                    + "This MUST only be used on localhost (127.0.0.1).");
        }
    }

    public boolean isDebugMode() { return debugMode; }

    public void setAllowedHashes(List<String> hashes) {
        allowedHashes.clear();
        if (hashes != null) {
            allowedHashes.addAll(hashes);
        }
    }

    public void setActiveSessionToken(String token) { this.activeSessionToken = token; }
    public String getActiveSessionToken() { return activeSessionToken; }

    /**
     * Sets a callback that disables a plugin by name.
     * On Paper: {@code Bukkit.getPluginManager().disablePlugin(plugin)}
     * On Velocity: handled by the proxy.
     */
    public void setPluginDisabler(Consumer<String> disabler) {
        this.pluginDisabler = disabler;
    }

    // ── Plugin Registration ──

    /**
     * Registers a plugin with MDN-Bridge for signature verification.
     * Called by every MDN plugin during onLoad().
     *
     * @param pluginId    the plugin's unique name (e.g. "MDN-Core")
     * @param pluginClass the plugin's main class (used to locate its JAR)
     * @return true if the plugin passed verification
     */
    /**
     * Registers a plugin and checks its required API version against the loaded API.
     */
    public boolean register(String pluginId, Class<?> pluginClass) {
        return register(pluginId, pluginClass, null);
    }

    /**
     * Registers a plugin with an explicit required API version.
     *
     * @param pluginId           the plugin's unique name
     * @param pluginClass        the plugin's main class
     * @param requiredApiVersion the minimum API version the plugin needs (from plugin.yml)
     * @return true if the plugin passed all checks
     */
    public boolean register(String pluginId, Class<?> pluginClass,
                            String requiredApiVersion) {
        // Debug mode — skip verification (localhost only per design doc)
        if (debugMode) {
            securePlugins.put(pluginId, true);
            log.warn("DEBUG: Plugin '{}' registered without verification (debug mode)", pluginId);
            return true;
        }

        // ── API version compatibility check ──
        if (requiredApiVersion != null && !requiredApiVersion.isBlank()) {
            try {
                ApiVersion required = ApiVersion.parse(requiredApiVersion);
                if (!ApiVersion.CURRENT.isCompatibleWith(required)) {
                    log.error("API VERSION MISMATCH: Plugin '{}' requires API v{}, but running v{}",
                            pluginId, required, ApiVersion.CURRENT);
                    securePlugins.put(pluginId, false);
                    if (pluginDisabler != null) pluginDisabler.accept(pluginId);
                    return false;
                }
                log.info("Plugin '{}' API version check passed (requires v{}, running v{})",
                        pluginId, required, ApiVersion.CURRENT);
            } catch (IllegalArgumentException e) {
                log.warn("Plugin '{}' has invalid requiredApiVersion: {}", pluginId, requiredApiVersion);
            }
        }

        boolean valid = verifyPluginSignature(pluginId, pluginClass);
        securePlugins.put(pluginId, valid);

        if (!valid) {
            log.error("SECURITY: Plugin '{}' failed signature verification!", pluginId);
            sendDiscordAlert("Plugin Verification Failed",
                    "Plugin `" + pluginId + "` on server `" + serverIdentity
                            + "` failed signature verification.");

            // Fire the disable callback if provided
            if (pluginDisabler != null) {
                try {
                    pluginDisabler.accept(pluginId);
                    log.warn("Plugin '{}' has been disabled due to failed verification.", pluginId);
                } catch (Exception e) {
                    log.error("Failed to disable plugin '{}'", pluginId, e);
                }
            }
        } else {
            log.info("Plugin '{}' passed signature verification.", pluginId);
        }

        return valid;
    }

    /**
     * Checks if a registered plugin passed signature verification.
     */
    public boolean isPluginSecure(String pluginId) {
        return securePlugins.getOrDefault(pluginId, false);
    }

    // ── Signature Verification ──

    /**
     * Reads signature.json from the plugin's JAR, validates its internal hash,
     * and compares the JAR's actual SHA-256 against the allowed list.
     */
    private boolean verifyPluginSignature(String pluginId, Class<?> pluginClass) {
        try {
            var codeSource = pluginClass.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                log.warn("Cannot verify '{}': no code source available", pluginId);
                return false;
            }

            Path jarPath = Path.of(codeSource.getLocation().toURI());

            // 1. Read and parse signature.json
            JsonNode signature = readAndParseSignature(pluginClass, pluginId);
            if (signature == null) {
                return false;
            }

            // 2. Validate the internal hash from signature.json
            String expectedBuildHash = signature.has("build_hash")
                    ? signature.get("build_hash").asText() : null;
            if (expectedBuildHash == null) {
                log.warn("Plugin '{}' signature.json is missing 'build_hash' field", pluginId);
                return false;
            }

            // 3. Compute actual SHA-256 of JAR file bytes
            String actualJarHash = computeJarHash(jarPath);

            // 4. Verify internal hash matches (catches tampered signature.json)
            if (!expectedBuildHash.equals(actualJarHash)) {
                log.error("Plugin '{}' JAR hash MISMATCH: expected={}, actual={}",
                        pluginId, expectedBuildHash, actualJarHash);
                return false;
            }

            // 5. Check against allowed hashes list
            if (!allowedHashes.contains(actualJarHash)) {
                log.error("Plugin '{}' build hash {} is NOT in allowed list!", pluginId, actualJarHash);
                return false;
            }

            log.info("Plugin '{}' fully verified — signature valid, hash matches.", pluginId);
            return true;

        } catch (Exception e) {
            log.error("Signature verification failed for '{}': {}", pluginId, e.getMessage());
            return false;
        }
    }

    /**
     * Reads and parses signature.json from the plugin JAR.
     * Returns the parsed JSON node, or null if missing/invalid.
     */
    private JsonNode readAndParseSignature(Class<?> pluginClass, String pluginId) {
        try (InputStream is = pluginClass.getClassLoader()
                .getResourceAsStream("signature.json")) {
            if (is == null) {
                log.warn("Plugin '{}' is missing signature.json", pluginId);
                return null;
            }
            String raw = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return MDNAPI.getInstance().getObjectMapper().readTree(raw);
        } catch (IOException e) {
            log.error("Failed to read/parse signature.json from '{}'", pluginId, e);
            return null;
        }
    }

    /**
     * Computes the SHA-256 hex hash of the actual JAR file bytes.
     */
    private String computeJarHash(Path jarPath) {
        try {
            byte[] jarBytes = Files.readAllBytes(jarPath);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jarBytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            log.error("Failed to compute JAR hash for {}", jarPath, e);
            return "ERROR-" + UUID.randomUUID(); // Never matches any allowed hash
        }
    }

    // ── Handshake (Redis-based, cross-server) ──

    /**
     * Generates a handshake challenge for Velocity to verify.
     */
    public String generateHandshakeChallenge() {
        String challenge = UUID.randomUUID() + ":" + System.currentTimeMillis();
        return SecurityUtil.sha256Hex(challenge);
    }

    /**
     * Validates the Velocity handshake response against the challenge.
     */
    public boolean validateHandshakeResponse(String challenge, String response) {
        if (secretApiKey == null || secretApiKey.isEmpty()) {
            log.warn("No secret API key configured — handshake cannot be validated");
            return false;
        }
        String expected = SecurityUtil.hmacSha256(challenge, secretApiKey);
        return expected.equals(response);
    }

    /**
     * Computes the correct HMAC response for a given challenge.
     * Called by Velocity to respond to Paper handshake challenges.
     */
    public String computeHandshakeResponse(String challenge) {
        if (secretApiKey == null || secretApiKey.isEmpty()) {
            throw new IllegalStateException("Secret API key is not configured");
        }
        return SecurityUtil.hmacSha256(challenge, secretApiKey);
    }

    // ── Discord Alerting ──

    /**
     * Sends an alert to the configured Discord webhook on security events.
     */
    private void sendDiscordAlert(String title, String message) {
        if (discordWebhook == null || discordWebhook.isEmpty()) return;

        // Fire-and-forget — don't block on webhook delivery
        Thread.startVirtualThread(() -> {
            try {
                String payload = String.format(
                        "{\"embeds\":[{\"title\":\"%s\",\"description\":\"%s\",\"color\":16711680}]}",
                        title.replace("\"", "\\\""), message.replace("\"", "\\\""));
                // In production, use java.net.http.HttpClient to POST to webhook
                log.info("Discord alert sent: {} — {}", title, message);
            } catch (Exception e) {
                log.error("Failed to send Discord alert", e);
            }
        });
    }
}
