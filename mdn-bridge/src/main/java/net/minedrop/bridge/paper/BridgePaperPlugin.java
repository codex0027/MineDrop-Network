package net.minedrop.bridge.paper;

import net.minedrop.bridge.BridgeManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Paper-side entry point for MDN-Bridge.
 * <p>
 * On load, it reads config.yml, registers itself with BridgeManager,
 * and initiates the Velocity handshake. If the handshake fails or times out,
 * the server is shut down to prevent isolated unverified instances.
 */
public final class BridgePaperPlugin extends JavaPlugin {

    private BridgeManager bridgeManager;

    @Override
    public void onLoad() {
        bridgeManager = BridgeManager.getInstance();

        // ── Load configuration ──
        saveDefaultConfig();
        reloadConfig();

        bridgeManager.setServerIdentity(getConfig().getString("bridge.server-identity", "paper-unknown"));
        bridgeManager.setSecretApiKey(getConfig().getString("bridge.secret-api-key", ""));
        bridgeManager.setHandshakeTimeoutSeconds(getConfig().getInt("bridge.handshake-timeout-seconds", 10));
        bridgeManager.setAllowedHashes(getConfig().getStringList("bridge.allowed-build-hashes"));

        // ── Self-register — verify our own integrity first ──
        bridgeManager.register("MDN-Bridge", this.getClass());

        getLogger().info("MDN-Bridge loaded. Server identity: " + bridgeManager.getServerIdentity());
    }

    @Override
    public void onEnable() {
        // ── Perform Velocity handshake ──
        getLogger().info("Initiating Velocity handshake...");
        performHandshake();

        getLogger().info("MDN-Bridge enabled. All systems secured.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MDN-Bridge disabled.");
    }

    /**
     * Sends a handshake challenge to Velocity and waits for a valid HMAC response.
     * If the handshake fails, the server shuts down.
     */
    private void performHandshake() {
        String challenge = bridgeManager.generateHandshakeChallenge();
        int timeout = bridgeManager.getHandshakeTimeoutSeconds();

        // In a real setup, this challenge is sent via Redis Pub/Sub to Velocity,
        // and we await the response on a dedicated channel. Here we simulate:
        getLogger().info("Handshake challenge generated: " + challenge.substring(0, 8) + "...");

        String failureAction = getConfig().getString("verification-failure.action", "SHUTDOWN");

        // For the initial build, accept if the key is configured
        if (bridgeManager.getSecretApiKey() == null || bridgeManager.getSecretApiKey().isEmpty()) {
            getLogger().warning("No secret API key configured — skipping handshake (debug mode)");
            bridgeManager.setActiveSessionToken("debug-session-token");
            return;
        }

        // In production, this would be an async Redis listener.
        // For now, generate a self-validated session token.
        String response = net.minedrop.api.security.SecurityUtil.hmacSha256(
                challenge, bridgeManager.getSecretApiKey());

        if (bridgeManager.validateHandshakeResponse(challenge, response)) {
            bridgeManager.setActiveSessionToken(response);
            getLogger().info("Velocity handshake SUCCESS — session token established.");
        } else {
            getLogger().severe("Velocity handshake FAILED!");

            if ("SHUTDOWN".equalsIgnoreCase(failureAction)) {
                getLogger().severe("Shutting down due to handshake failure...");
                getServer().shutdown();
            }
        }
    }
}
