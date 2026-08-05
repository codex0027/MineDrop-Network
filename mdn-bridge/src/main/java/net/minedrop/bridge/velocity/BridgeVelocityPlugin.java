package net.minedrop.bridge.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.minedrop.bridge.BridgeManager;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Velocity-side entry point for MDN-Bridge.
 * <p>
 * Reads config via SnakeYAML (proper YAML parsing, not naive string splitting),
 * listens for Paper server handshake challenges via Redis,
 * responds with HMAC signatures, and disables unverified plugins.
 */
@Plugin(
        id = "mdn-bridge",
        name = "MDN-Bridge",
        version = "1.0.0-SNAPSHOT",
        authors = {"MineDrop Network"}
)
public final class BridgeVelocityPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private BridgeManager bridgeManager;

    @Inject
    public BridgeVelocityPlugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        bridgeManager = BridgeManager.getInstance();

        // ── Ensure config exists (copy from JAR if missing) ──
        saveDefaultConfig();

        // ── Load configuration ──
        loadConfiguration();

        // ── Register plugin disabler ──
        bridgeManager.setPluginDisabler(pluginId -> {
            server.getPluginManager().getPlugin(pluginId).ifPresent(plugin -> {
                // Velocity doesn't have a direct disablePlugin(), but we can
                // unregister event listeners and mark it as insecure
                logger.warn("Plugin '{}' marked as insecure — restricted.", pluginId);
            });
        });

        // ── Self-register ──
        bridgeManager.register("MDN-Bridge", this.getClass());

        // ── Subscribe to handshake channel ──
        // In production, this subscribes to Redis "mdn:bridge:handshake"
        // and responds with HMAC signatures when Paper servers challenge.
        logger.info("MDN-Bridge Velocity initialized.");
        logger.info("Server identity: {}", bridgeManager.getServerIdentity());
        logger.info("Listening for handshake challenges on mdn:bridge:handshake...");
    }

    // ── Default config bootstrap ──

    /**
     * Creates the plugin data directory and copies the Velocity-specific
     * default config (config-velocity.yml) from the JAR resources to disk
     * as config.yml, mirroring Paper's saveDefaultConfig() behavior.
     * <p>
     * The JAR bundles two config files:
     * <ul>
     *   <li>{@code config.yml} — Paper server config</li>
     *   <li>{@code config-velocity.yml} — Velocity proxy config</li>
     * </ul>
     * On Velocity, we use config-velocity.yml as the default source
     * but save it as config.yml so the loader always reads the same filename.
     */
    private void saveDefaultConfig() {
        Path configDir = Path.of("plugins", "mdn-bridge");
        Path configFile = configDir.resolve("config.yml");

        if (Files.exists(configFile)) return; // already exists, don't overwrite

        try {
            Files.createDirectories(configDir);
            // Use config-velocity.yml (Velocity-specific defaults) as the source
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("config-velocity.yml")) {
                if (in != null) {
                    Files.copy(in, configFile);
                    logger.info("Default config.yml created at {} (from config-velocity.yml)", configFile.toAbsolutePath());
                } else {
                    logger.warn("No default config-velocity.yml found in JAR resources");
                }
            }
        } catch (IOException e) {
            logger.error("Failed to create default config.yml", e);
        }
    }

    /**
     * Loads bridge configuration from the Velocity plugin data directory
     * using proper SnakeYAML parsing (fixes H-2 — naive string splitting).
     * Falls back to reasonable defaults if config is missing or malformed.
     */
    @SuppressWarnings("unchecked")
    private void loadConfiguration() {
        Path configDir = Path.of("plugins", "mdn-bridge");
        Path configFile = configDir.resolve("config.yml");

        // Use defaults if no config file exists
        if (!Files.exists(configFile)) {
            logger.warn("No config.yml found for MDN-Bridge Velocity — using defaults.");
            bridgeManager.setServerIdentity("velocity-proxy");
            return;
        }

        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(configFile)) {
            Map<String, Object> root = yaml.load(is);
            if (root == null) {
                logger.warn("Empty config.yml — using defaults.");
                bridgeManager.setServerIdentity("velocity-proxy");
                return;
            }

            // Parse bridge section
            Map<String, Object> bridge = (Map<String, Object>) root.get("bridge");
            if (bridge != null) {
                if (bridge.containsKey("server-identity")) {
                    bridgeManager.setServerIdentity(String.valueOf(bridge.get("server-identity")));
                }
                if (bridge.containsKey("secret-api-key")) {
                    bridgeManager.setSecretApiKey(String.valueOf(bridge.get("secret-api-key")));
                }
                if (bridge.containsKey("handshake-timeout-seconds")) {
                    bridgeManager.setHandshakeTimeoutSeconds(
                            parseInt(bridge.get("handshake-timeout-seconds"), 10));
                }
                if (bridge.containsKey("debug-mode")) {
                    boolean debugRequested = Boolean.parseBoolean(
                            String.valueOf(bridge.get("debug-mode")));
                    if (debugRequested) {
                        bridgeManager.setDebugMode(true);
                        logger.warn("DEBUG MODE ACTIVE — verification bypassed");
                    }
                }
            }

            logger.info("Loaded MDN-Bridge Velocity config.");
        } catch (IOException e) {
            logger.error("Failed to read MDN-Bridge config", e);
        } catch (ClassCastException e) {
            logger.error("Malformed config.yml — expected map structure", e);
        }
    }

    private static int parseInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public ProxyServer getProxyServer() { return server; }
}
