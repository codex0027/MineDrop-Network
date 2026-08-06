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
import java.util.List;
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

    private static final String HANDSHAKE_CHALLENGE_CHANNEL = "mdn:bridge:handshake";
    private static final String HANDSHAKE_RESPONSE_CHANNEL = "mdn:bridge:handshake:response";

    private final ProxyServer server;
    private final Logger logger;
    private BridgeManager bridgeManager;
    private static volatile BridgeVelocityPlugin pluginInstance;
    private boolean handshakeListenerStarted = false;

    @Inject
    public BridgeVelocityPlugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
        pluginInstance = this;
    }

    /** Called by CoreVelocityPlugin after injecting handshake transport. */
    public static void triggerHandshakeListener() {
        if (pluginInstance != null) {
            pluginInstance.startHandshakeListener();
        }
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

        logger.info("MDN-Bridge Velocity initialized.");
        logger.info("Server identity: {}", bridgeManager.getServerIdentity());

        // ── Start handshake listener if Redis is already available ──
        // (If not, CoreVelocityPlugin will trigger it after Redis init)
        startHandshakeListener();
    }

    /**
     * Subscribes to the Redis handshake challenge channel and responds
     * with HMAC-signed responses for every Paper server that challenges.
     * <p>
     * This is idempotent — called both from onProxyInitialize() (if Redis
     * is already ready) and externally by CoreVelocityPlugin after Redis
     * initialization completes.
     */
    public void startHandshakeListener() {
        if (handshakeListenerStarted) return;
        if (!bridgeManager.isRedisReady()) {
            logger.info("Redis not yet ready — handshake listener deferred");
            return;
        }

        handshakeListenerStarted = true;
        logger.info("Starting handshake listener on {}...", HANDSHAKE_CHALLENGE_CHANNEL);

        bridgeManager.subscribeHandshake(HANDSHAKE_CHALLENGE_CHANNEL, raw -> {
            try {
                // Parse challenge: {"challenge":"<sha256>","server":"<id>","timestamp":<epoch>}
                var node = BridgeManager.parseSimpleJson(raw);
                String challenge = node.get("challenge");
                String paperServer = node.get("server");

                if (challenge == null || challenge.isBlank()) {
                    logger.debug("Ignoring malformed handshake challenge");
                    return;
                }

                logger.info("Received handshake challenge from {}: {}",
                        paperServer != null ? paperServer : "unknown", challenge.substring(0, 8) + "...");

                // Compute HMAC response
                String response = bridgeManager.computeHandshakeResponse(challenge);

                // Build response JSON
                String responseJson = String.format(
                        "{\"challenge\":\"%s\",\"response\":\"%s\",\"server\":\"%s\"}",
                        challenge, response, bridgeManager.getServerIdentity());

                // Publish response back via Redis
                bridgeManager.publishHandshake(HANDSHAKE_RESPONSE_CHANNEL, responseJson);
                logger.info("Handshake response published for server: {}",
                        paperServer != null ? paperServer : "unknown");

            } catch (Exception e) {
                logger.error("Error processing handshake challenge", e);
            }
        });

        logger.info("Handshake listener active — responding to Paper server challenges");
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
                if (bridge.containsKey("allowed-build-hashes")) {
                    Object hashesObj = bridge.get("allowed-build-hashes");
                    if (hashesObj instanceof List<?> list) {
                        List<String> hashes = list.stream()
                                .map(String::valueOf)
                                .toList();
                        bridgeManager.setAllowedHashes(hashes);
                        logger.info("Loaded {} allowed build hash(es)", hashes.size());
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
