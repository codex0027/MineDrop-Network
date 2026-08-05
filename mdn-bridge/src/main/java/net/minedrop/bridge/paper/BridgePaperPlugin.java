package net.minedrop.bridge.paper;

import net.minedrop.bridge.BridgeManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Paper-side entry point for MDN-Bridge.
 * <p>
 * On load, it reads config.yml, registers itself with BridgeManager,
 * and initiates the Velocity handshake with async retry logic. If the handshake
 * fails after all retries, the server is shut down to prevent isolated
 * unverified instances.
 * <p>
 * The handshake is a true cross-server challenge-response via Redis:
 * Paper publishes a challenge → Velocity reads it, computes HMAC, publishes
 * response → Paper validates and establishes a session token.
 */
public final class BridgePaperPlugin extends JavaPlugin {

    private static final String HANDSHAKE_CHALLENGE_CHANNEL = "mdn:bridge:handshake";
    private static final String HANDSHAKE_RESPONSE_CHANNEL = "mdn:bridge:handshake:response";

    private BridgeManager bridgeManager;
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingHandshakes
            = new ConcurrentHashMap<>();

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
        // Handshake is triggered by CorePaperPlugin after Redis transport is ready.
        // Deferring until then — see triggerHandshake() below.
        getLogger().info("MDN-Bridge enabled. Waiting for Core to inject Redis transport...");
    }

    /**
     * Called by CorePaperPlugin after injecting the handshake transport.
     * This solves the timing problem: Bridge.onEnable() runs before Core.onEnable(),
     * so Redis isn't ready when Bridge first tries the handshake.
     */
    public void triggerHandshake() {
        getLogger().info("Redis transport ready — initiating Velocity handshake (retries: 3)...");
        attemptHandshakeAsync(1);
    }

    @Override
    public void onDisable() {
        // Cancel any pending handshake futures
        for (var future : pendingHandshakes.values()) {
            future.cancel(true);
        }
        pendingHandshakes.clear();
        getLogger().info("MDN-Bridge disabled.");
    }

    /**
     * Attempts the Velocity handshake asynchronously using the Bukkit scheduler.
     * On failure, schedules the next retry 3 seconds later on an async thread.
     * This avoids blocking the server main thread (fixes H-1).
     *
     * @param attempt current attempt number (1-based)
     */
    private void attemptHandshakeAsync(int attempt) {
        int maxRetries = 3;

        // No API key = skip handshake (development only)
        if (bridgeManager.getSecretApiKey() == null || bridgeManager.getSecretApiKey().isEmpty()) {
            getLogger().warning("No secret API key configured — handshake SKIPPED");
            bridgeManager.setActiveSessionToken("unverified-session");
            return;
        }

        getLogger().info("Handshake attempt " + attempt + "/" + maxRetries + "...");

        // If transport not ready yet, wait and retry more frequently
        // (MDN-Core may still be initializing Redis)
        if (!bridgeManager.isRedisReady()) {
            getLogger().warning("Redis transport not ready — retrying in 1s...");
            if (attempt < maxRetries) {
                Bukkit.getScheduler().runTaskLaterAsynchronously(this,
                        () -> attemptHandshakeAsync(attempt + 1), 20L); // 20 ticks = 1 second
            } else {
                getLogger().severe("Handshake FAILED after " + maxRetries + " attempts — transport never ready!");
                handleHandshakeFailure();
            }
            return;
        }

        // Perform the actual cross-server handshake via Redis
        performCrossServerHandshake().thenAcceptAsync(result -> {
            if (result) {
                getLogger().info("Velocity handshake SUCCESS on attempt " + attempt);
            } else if (attempt < maxRetries) {
                getLogger().warning("Handshake attempt " + attempt + " failed. Retrying in 3s (async)...");
                Bukkit.getScheduler().runTaskLaterAsynchronously(this,
                        () -> attemptHandshakeAsync(attempt + 1), 60L);
            } else {
                getLogger().severe("Handshake FAILED after " + maxRetries + " attempts!");
                handleHandshakeFailure();
            }
        }).exceptionally(ex -> {
            getLogger().warning("Handshake attempt " + attempt + " error: " + ex.getMessage());
            if (attempt < maxRetries) {
                Bukkit.getScheduler().runTaskLaterAsynchronously(this,
                        () -> attemptHandshakeAsync(attempt + 1), 60L);
            } else {
                handleHandshakeFailure();
            }
            return null;
        });
    }

    /**
     * Performs a true cross-server handshake via Redis Pub/Sub.
     * <p>
     * Flow:
     * <ol>
     *   <li>Generate a random challenge (SHA-256 of UUID + timestamp)</li>
     *   <li>Publish challenge JSON to Redis channel {@code mdn:bridge:handshake}</li>
     *   <li>Subscribe to {@code mdn:bridge:handshake:response} and listen for the
     *       matching challenge's HMAC response from Velocity</li>
     *   <li>Validate HMAC — if correct, establish session token</li>
     * </ol>
     *
     * @return CompletableFuture that resolves to true if handshake succeeded
     */
    private CompletableFuture<Boolean> performCrossServerHandshake() {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                if (!bridgeManager.isRedisReady()) {
                    getLogger().warning("Redis not ready — handshake deferred");
                    result.complete(false);
                    return;
                }

                // Step 1: Generate challenge
                String challenge = bridgeManager.generateHandshakeChallenge();
                String expectedResponse = bridgeManager.computeHandshakeResponse(challenge);

                // Step 2: Build challenge JSON
                String challengeJson = String.format(
                        "{\"challenge\":\"%s\",\"server\":\"%s\",\"timestamp\":%d}",
                        challenge, bridgeManager.getServerIdentity(), System.currentTimeMillis());

                // Step 3: Register pending handshake future
                CompletableFuture<String> responseFuture = new CompletableFuture<>();
                pendingHandshakes.put(challenge, responseFuture);

                // Step 4: Subscribe to response channel — complete the future
                // when Velocity's response arrives with the matching challenge
                bridgeManager.subscribeHandshake(HANDSHAKE_RESPONSE_CHANNEL, raw -> {
                    try {
                        // Parse response JSON: {"challenge":"...","response":"...","server":"..."}
                        var node = BridgeManager.parseSimpleJson(raw);
                        String respChallenge = node.get("challenge");
                        String respValue = node.get("response");
                        if (respChallenge != null && respChallenge.equals(challenge)) {
                            CompletableFuture<String> f = pendingHandshakes.get(respChallenge);
                            if (f != null && !f.isDone()) {
                                f.complete(respValue);
                            }
                        }
                    } catch (Exception ignored) {
                        // Malformed handshake response — ignore
                    }
                });

                // Step 5: Publish challenge to Velocity
                bridgeManager.publishHandshake(HANDSHAKE_CHALLENGE_CHANNEL, challengeJson);
                getLogger().info("Handshake challenge published to Redis for server: "
                        + bridgeManager.getServerIdentity());

                // Step 6: Wait for Velocity's response
                try {
                    String velocityResponse = responseFuture.get(
                            bridgeManager.getHandshakeTimeoutSeconds(), TimeUnit.SECONDS);
                    pendingHandshakes.remove(challenge);

                    // Step 7: Validate HMAC
                    if (expectedResponse.equals(velocityResponse)) {
                        bridgeManager.setActiveSessionToken(velocityResponse);
                        getLogger().info("Handshake VERIFIED — session established with Velocity");
                        result.complete(true);
                    } else {
                        getLogger().warning("Handshake response MISMATCH — possible key mismatch");
                        result.complete(false);
                    }
                } catch (TimeoutException e) {
                    pendingHandshakes.remove(challenge);
                    getLogger().warning("Handshake timed out after "
                            + bridgeManager.getHandshakeTimeoutSeconds() + "s");
                    result.complete(false);
                }
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });

        return result;
    }

    /**
     * Handles the case where all handshake attempts have been exhausted.
     * Follows the configured action from config.yml.
     */
    private void handleHandshakeFailure() {
        String action = getConfig().getString("verification-failure.action", "SHUTDOWN");
        if ("SHUTDOWN".equalsIgnoreCase(action)) {
            getLogger().severe("Shutting down server due to failed handshake...");
            Bukkit.getScheduler().runTask(this, Bukkit::shutdown);
        } else {
            getLogger().severe("CRITICAL: Handshake FAILED but server will continue in unsecured mode!");
        }
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
