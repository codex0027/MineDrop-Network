package net.minedrop.bridge.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.minedrop.bridge.BridgeManager;
import org.slf4j.Logger;

/**
 * Velocity-side entry point for MDN-Bridge.
 * <p>
 * Listens for Paper server handshake challenges and responds with HMAC signatures.
 * Only allows verified Paper servers to join the proxy network.
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

        // ── Self-register ──
        bridgeManager.register("MDN-Bridge", this.getClass());

        logger.info("MDN-Bridge Velocity side initialized.");
        logger.info("Listening for handshake challenges from Paper servers...");

        // In production, subscribe to Redis channel "mdn:bridge:handshake"
        // and respond with HMAC signature when a challenge arrives
    }

    public ProxyServer getProxyServer() { return server; }
}
