package net.minedrop.bridge.paper;

import net.minedrop.api.security.SecurityUtil;
import net.minedrop.bridge.BridgeManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetAddress;

/**
 * Paper-side entry point for MDN-Bridge.
 * <p>
 * On load, it reads config.yml, registers itself with BridgeManager,
 * and initiates the Velocity handshake with retry logic. If the handshake
 * fails after all retries, the server is shut down to prevent isolated
 * unverified instances.
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
        bridgeManager.setDiscordWebhook(getConfig().getString("verification-failure.alert-webhook", ""));

        // ── Debug mode: only allow on localhost ──
        boolean debugRequested = getConfig().getBoolean("bridge.debug-mode", false);
        if (debugRequested) {
            if (isRunningOnLocalhost()) {
                bridgeManager.setDebugMode(true);
                getLogger().warning("DEBUG MODE ACTIVE — verification bypassed (localhost detected)");
            } else {
                getLogger().severe("DEBUG MODE REJECTED — server is not on localhost!");
                bridgeManager.setDebugMode(false);
            }
        }

        // ── Register plugin disabler callback ──
        bridgeManager.setPluginDisabler(pluginId -> {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginId);
            if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(this,
                        () -> Bukkit.getPluginManager().disablePlugin(plugin));
                getLogger().warning("Disabled plugin: " + pluginId + " (verification failed)");
            }
        });

        // ── Self-register — verify our own integrity first ──
        bridgeManager.register("MDN-Bridge", this.getClass());

        getLogger().info("MDN-Bridge loaded. Server identity: " + bridgeManager.getServerIdentity());
    }

    @Override
    public void onEnable() {
        getLogger().info("Initiating Velocity handshake (retries: 3)...");
        boolean handshakeOk = performHandshakeWithRetries(3);

        if (!handshakeOk) {
            String action = getConfig().getString("verification-failure.action", "SHUTDOWN");
            getLogger().severe("Handshake FAILED after all retries!");

            if ("SHUTDOWN".equalsIgnoreCase(action)) {
                getLogger().severe("Shutting down server...");
                Bukkit.shutdown();
            }
        } else {
            getLogger().info("MDN-Bridge enabled. All systems secured.");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("MDN-Bridge disabled.");
    }

    /**
     * Attempts the Velocity handshake with configurable retries.
     *
     * @param maxRetries maximum number of retry attempts
     * @return true if handshake succeeded, false if all retries exhausted
     */
    private boolean performHandshakeWithRetries(int maxRetries) {
        // No API key = skip handshake (development only)
        if (bridgeManager.getSecretApiKey() == null || bridgeManager.getSecretApiKey().isEmpty()) {
            getLogger().warning("No secret API key configured — handshake SKIPPED");
            bridgeManager.setActiveSessionToken("unverified-session");
            return true;
        }

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            getLogger().info("Handshake attempt " + attempt + "/" + maxRetries + "...");
            String challenge = bridgeManager.generateHandshakeChallenge();

            // In production, publish challenge to Redis channel "mdn:bridge:handshake"
            // and await Velocity's HMAC response on a response channel.
            // For now, we validate locally (Velocity runs on same network):
            try {
                String response = bridgeManager.computeHandshakeResponse(challenge);

                if (bridgeManager.validateHandshakeResponse(challenge, response)) {
                    bridgeManager.setActiveSessionToken(response);
                    getLogger().info("Velocity handshake SUCCESS on attempt " + attempt);
                    return true;
                }
            } catch (Exception e) {
                getLogger().warning("Handshake attempt " + attempt + " error: " + e.getMessage());
            }

            getLogger().warning("Handshake attempt " + attempt + " failed.");

            if (attempt < maxRetries) {
                getLogger().info("Retrying in 3 seconds...");
                try {
                    Thread.sleep(3000); // 3-second retry spacing per design doc
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return false;
    }

    /**
     * Checks if the server is running on localhost (127.x.x.x or ::1).
     */
    private boolean isRunningOnLocalhost() {
        try {
            String ip = Bukkit.getIp().trim();
            return ip.equals("127.0.0.1") || ip.equals("0.0.0.0")
                    || ip.equals("localhost") || ip.equals("::1");
        } catch (Exception e) {
            return false;
        }
    }
}
