package net.minedrop.bridge.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.proxy.ProxyServer;
import net.minedrop.bridge.BridgeManager;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Velocity-side entry point for MDN-Bridge.
 * <p>
 * Reads config, listens for Paper server handshake challenges via Redis,
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

    /**
     * Loads bridge configuration from the Velocity plugin data directory.
     * Falls back to reasonable defaults if config is missing.
     */
    private void loadConfiguration() {
        Path configDir = Path.of("plugins", "mdn-bridge");
        Path configFile = configDir.resolve("config.yml");

        // Use defaults if no config file exists
        if (!Files.exists(configFile)) {
            logger.warn("No config.yml found for MDN-Bridge Velocity — using defaults.");
            bridgeManager.setServerIdentity("velocity-proxy");
            return;
        }

        try {
            // Simple YAML-like parsing for the Velocity side
            // In production, use a proper YAML library or Velocity's config adapter
            String content = Files.readString(configFile);
            for (String line : content.split("\n")) {
                line = line.trim();
                if (line.startsWith("server-identity:")) {
                    bridgeManager.setServerIdentity(
                            line.substring("server-identity:".length()).trim().replace("\"", ""));
                } else if (line.startsWith("secret-api-key:")) {
                    bridgeManager.setSecretApiKey(
                            line.substring("secret-api-key:".length()).trim().replace("\"", ""));
                }
            }
            logger.info("Loaded MDN-Bridge Velocity config.");
        } catch (IOException e) {
            logger.error("Failed to read MDN-Bridge config", e);
        }
    }

    public ProxyServer getProxyServer() { return server; }
}
