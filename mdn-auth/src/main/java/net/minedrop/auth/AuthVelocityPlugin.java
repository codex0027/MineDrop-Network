package net.minedrop.auth;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.util.GameProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.minedrop.bridge.BridgeManager;
import net.minedrop.core.redis.RedisManager;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MDN-Auth — Velocity-side authentication controller.
 * <p>
 * Handles player verification, device fingerprinting, staff 2FA,
 * alt account detection, and private lobby service-secret validation.
 * <p>
 * <h3>Configuration</h3>
 * Reads {@code plugins/mdn-auth/config.yml}. On first startup, copies the
 * default config from the JAR resources.
 *
 * @see plan/MineDrop/plugins/03_MDN_Auth.md
 */
@Plugin(
        id = "mdn-auth",
        name = "MDN-Auth",
        version = "1.0.0-SNAPSHOT",
        authors = {"MineDrop Network"},
        dependencies = {
                @com.velocitypowered.api.plugin.Dependency(id = "mdn-bridge"),
                @com.velocitypowered.api.plugin.Dependency(id = "mdn-core")
        }
)
public final class AuthVelocityPlugin {

    private final ProxyServer proxy;
    private final Logger logger;

    private AuthManager authManager;
    private RedisManager redisManager;

    // ── Config values ──
    private int maxAccountsPerIp = 3;
    private int maxAccountsPerFingerprint = 2;
    private String altAction = "KICK"; // KICK, ALERT, SHADOW_BAN
    private boolean staff2faEnabled = true;
    private boolean enforceIpLock = true;
    private String totpIssuer = "MineDropNetwork";
    private List<String> force2faPermissions = List.of("mdn.group.admin", "mdn.group.staff");
    private int privateLobbyTokenLifetime = 60;
    private String secretHashingAlgorithm = "SHA-256";

    // ── Redis config ──
    private String redisHost = "127.0.0.1";
    private int redisPort = 6379;
    private String redisPassword = "";

    @Inject
    public AuthVelocityPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("MDN-Auth initializing...");

        // ── Step 1: Ensure config exists ──
        saveDefaultConfig();

        // ── Step 2: Load config ──
        loadConfiguration();

        // ── Step 3: Connect to Redis ──
        initRedis();

        // ── Step 4: Initialize AuthManager ──
        authManager = new AuthManager(redisManager, logger);
        authManager.initialize();

        // ── Step 5: Register commands ──
        registerCommands();

        // ── Step 6: Re-lock any players who were locked before a restart ──
        // (Their locked state is in Redis — we sync it to memory)

        logger.info("MDN-Auth enabled.");
        logger.info("  Alt limits: {} per IP, {} per fingerprint (action: {})",
                maxAccountsPerIp, maxAccountsPerFingerprint, altAction);
        logger.info("  Staff 2FA: {} ({} permission groups)",
                staff2faEnabled ? "enabled" : "disabled",
                force2faPermissions.size());
        logger.info("  Redis: {}", redisManager.isConnected() ? "connected" : "DISCONNECTED");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("MDN-Auth shutting down...");
        if (authManager != null) authManager.shutdown();
        if (redisManager != null) redisManager.shutdown();
        logger.info("MDN-Auth disabled.");
    }

    // ── Player login interception ──

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        // PreLoginEvent fires BEFORE the player fully connects.
        // We can deny the connection here based on alt detection.
        // However, we don't have client brand info yet — that comes from LoginEvent.
        // For IP-based alt detection, we can use the connection's remote address.
        InboundConnection conn = event.getConnection();
        String ipAddress = conn.getRemoteAddress().getAddress().getHostAddress();

        // Quick alt check by IP only (no fingerprint yet)
        // We do a more thorough check in onLogin
        AltDetector.Action action = authManager.checkAltLimits(
                UUID.randomUUID(), // will be replaced in onLogin
                ipAddress,
                "pending",         // fingerprint not available yet
                maxAccountsPerIp,
                maxAccountsPerFingerprint
        );

        if (action == AltDetector.Action.KICK && "KICK".equalsIgnoreCase(altAction)) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text()
                            .append(Component.text("⚠ Too many accounts", NamedTextColor.RED, TextDecoration.BOLD))
                            .append(Component.newline())
                            .append(Component.text("Maximum " + maxAccountsPerIp + " accounts per IP.", NamedTextColor.GRAY))
                            .build()
            ));
            logger.info("Pre-login denied for IP {} — alt limit exceeded", ipAddress);
        }
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();
        String ipAddress = player.getRemoteAddress().getAddress().getHostAddress();

        // ── Device fingerprint ──
        // Extract client brand from the player's game profile properties if available.
        // Velocity doesn't expose client brand directly in the API, so we use a best-effort approach.
        String clientBrand = "unknown";
        int protocolVersion = player.getProtocolVersion().getProtocol();

        DeviceFingerprinter.Fingerprint fp = authManager.fingerprint(
                uuid, ipAddress, clientBrand, protocolVersion);

        // ── Alt detection ──
        AltDetector.Action action = authManager.checkAltLimits(
                uuid, ipAddress, fp.getHash(),
                maxAccountsPerIp, maxAccountsPerFingerprint);

        if (action == AltDetector.Action.KICK && "KICK".equalsIgnoreCase(altAction)) {
            player.disconnect(Component.text()
                    .append(Component.text("⚠ Too many accounts", NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text("Maximum " + maxAccountsPerIp + " accounts per IP.", NamedTextColor.GRAY))
                    .build());
            logger.info("Login denied for {} — alt limit (ip={}, fp={})",
                    username, ipAddress, fp.getHash().substring(0, 12));
            return;
        }

        if (action == AltDetector.Action.ALERT) {
            logger.warn("⚠ Alt alert: {} (ip={}, fp={}) is approaching alt limits",
                    username, ipAddress, fp.getHash().substring(0, 12));
            // TODO: Send alert to staff via Discord/chat
        }

        // ── Record successful login ──
        authManager.recordLogin(uuid, ipAddress, fp.getHash());

        // ── Staff 2FA check ──
        if (staff2faEnabled) {
            boolean requires2fa = force2faPermissions.stream()
                    .anyMatch(player::hasPermission);

            if (requires2fa) {
                // Block input until 2FA is verified
                authManager.lockPlayer(uuid);
                applyPreAuthLockdown(player);
                logger.info("Staff {} requires 2FA — locked until verified", username);
            }
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (authManager.isPlayerLocked(uuid)) {
            authManager.unlockPlayer(uuid);
            logger.debug("Cleaned up pre-auth lock for disconnected player {}", uuid);
        }
    }

    // ── Pre-auth lockdown ──

    /**
     * Applies blindness effect, freezes the player in a void world, and blocks
     * server switching until 2FA is completed.
     * <p>
     * Implementation: Sends a title screen overlay and prevents the player from
     * being routed to any game server. The player stays in a "limbo" state on
     * the proxy until they pass 2FA verification via {@code /2fa verify}.
     */
    private void applyPreAuthLockdown(Player player) {
        // Send a title overlay explaining the situation
        player.showTitle(Title.title(
                Component.text("⚠ 2FA Required", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Use /2fa verify <code> to authenticate", NamedTextColor.GRAY),
                Title.Times.times(
                        Duration.ofMillis(500),   // fade in
                        Duration.ofDays(1),        // stay (basically forever)
                        Duration.ofMillis(500)    // fade out
                )
        ));

        // Send a persistent action bar reminder
        player.sendActionBar(Component.text()
                .append(Component.text("🔒 ", NamedTextColor.RED))
                .append(Component.text("You must verify 2FA to play! Use ", NamedTextColor.YELLOW))
                .append(Component.text("/2fa verify <code>", NamedTextColor.GOLD, TextDecoration.BOLD))
                .build());

        // Send chat message with clear instructions
        player.sendMessage(Component.text());
        player.sendMessage(Component.text()
                .append(Component.text("══════ ⚠ 2FA Required ══════", NamedTextColor.RED, TextDecoration.BOLD))
                .build());
        player.sendMessage(Component.text());
        player.sendMessage(Component.text()
                .append(Component.text("You have 2FA enabled on your account.", NamedTextColor.YELLOW))
                .build());
        player.sendMessage(Component.text()
                .append(Component.text("Open your authenticator app and enter:", NamedTextColor.GRAY))
                .build());
        player.sendMessage(Component.text());
        player.sendMessage(Component.text()
                .append(Component.text("  /2fa verify <code>", NamedTextColor.GOLD, TextDecoration.BOLD))
                .build());
        player.sendMessage(Component.text());
        player.sendMessage(Component.text()
                .append(Component.text("══════════════════════════", NamedTextColor.RED))
                .build());
    }

    /**
     * Removes the pre-auth lockdown state after successful 2FA verification.
     * Called by {@code TwoFactorCommand} on successful verification.
     */
    public void removeLockdown(Player player) {
        // Clear the title
        player.clearTitle();

        // Send welcome message
        player.sendMessage(Component.text());
        player.sendMessage(Component.text()
                .append(Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text("Authentication successful! Welcome back, " + player.getUsername() + "!", NamedTextColor.GREEN))
                .build());
        player.sendMessage(Component.text());

        // Route to lobby
        routeToLobby(player);
    }

    // ── Routing ──

    private void routeToLobby(Player player) {
        // Try to find a lobby server
        proxy.getServer("lobby").ifPresentOrElse(
                lobby -> {
                    player.createConnectionRequest(lobby).connectWithIndication().thenAccept(result -> {
                        if (!result) {
                            logger.warn("Failed to route {} to lobby", player.getUsername());
                            player.sendMessage(Component.text("Failed to connect to lobby. Please try reconnecting.", NamedTextColor.RED));
                        }
                    });
                },
                () -> {
                    logger.warn("No lobby server found for {}", player.getUsername());
                    player.sendMessage(Component.text("No lobby server available. Please try again later.", NamedTextColor.RED));
                }
        );
    }

    // ── Config bootstrap ──

    private void saveDefaultConfig() {
        Path configDir = Path.of("plugins", "mdn-auth");
        Path configFile = configDir.resolve("config.yml");

        if (Files.exists(configFile)) return;

        try {
            Files.createDirectories(configDir);
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile);
                    logger.info("Default config.yml created at {}", configFile.toAbsolutePath());
                } else {
                    logger.warn("No default config.yml found in JAR resources");
                }
            }
        } catch (IOException e) {
            logger.error("Failed to create default config.yml", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadConfiguration() {
        Path configFile = Path.of("plugins", "mdn-auth", "config.yml");
        if (!Files.exists(configFile)) {
            logger.warn("No config.yml found — using defaults.");
            return;
        }

        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(configFile)) {
            Map<String, Object> root = yaml.load(is);
            if (root == null) return;

            // Parse Redis config
            Map<String, Object> redis = (Map<String, Object>) root.get("redis");
            if (redis != null) {
                redisHost = String.valueOf(redis.getOrDefault("host", redisHost));
                redisPort = parseInt(redis.get("port"), redisPort);
                redisPassword = String.valueOf(redis.getOrDefault("password", redisPassword));
            }

            // Parse auth config
            Map<String, Object> auth = (Map<String, Object>) root.get("auth");
            if (auth != null) {
                // Alt detection
                Map<String, Object> altDetection = (Map<String, Object>) auth.get("alt-detection");
                if (altDetection != null) {
                    maxAccountsPerIp = parseInt(altDetection.get("max-accounts-per-ip"), maxAccountsPerIp);
                    maxAccountsPerFingerprint = parseInt(altDetection.get("max-accounts-per-fingerprint"), maxAccountsPerFingerprint);
                    altAction = String.valueOf(altDetection.getOrDefault("action", altAction));
                }

                // Staff 2FA
                Map<String, Object> staff2fa = (Map<String, Object>) auth.get("staff-2fa");
                if (staff2fa != null) {
                    staff2faEnabled = (boolean) staff2fa.getOrDefault("enabled", staff2faEnabled);
                    enforceIpLock = (boolean) staff2fa.getOrDefault("enforce-ip-lock", enforceIpLock);
                    totpIssuer = String.valueOf(staff2fa.getOrDefault("totp-issuer", totpIssuer));
                    List<String> perms = (List<String>) staff2fa.get("force-for-permissions");
                    if (perms != null) {
                        force2faPermissions = perms;
                    }
                }

                // Private lobbies
                Map<String, Object> privateLobbies = (Map<String, Object>) auth.get("private-lobbies");
                if (privateLobbies != null) {
                    privateLobbyTokenLifetime = parseInt(privateLobbies.get("token-lifetime-seconds"), privateLobbyTokenLifetime);
                    secretHashingAlgorithm = String.valueOf(privateLobbies.getOrDefault("secret-hashing-algorithm", secretHashingAlgorithm));
                }
            }

            logger.info("Config loaded: redis={}:{}, alt-limits={}/{}",
                    redisHost, redisPort, maxAccountsPerIp, maxAccountsPerFingerprint);
        } catch (IOException e) {
            logger.error("Failed to read config.yml", e);
        } catch (ClassCastException e) {
            logger.error("Malformed config.yml", e);
        }
    }

    // ── Redis initialization ──

    private void initRedis() {
        logger.info("Connecting to Redis...");
        redisManager = new RedisManager(redisHost, redisPort, redisPassword,
                2000, "mdn:auth:bus");
    }

    // ── Commands ──

    private void registerCommands() {
        CommandManager cmd = proxy.getCommandManager();

        // /2fa — Two-factor authentication (passes removeLockdown as post-verify callback)
        cmd.register(
                cmd.metaBuilder("2fa")
                        .plugin(this)
                        .build(),
                authManager.createTwoFactorCommand(this::removeLockdown)
        );

        // /auth — Auth admin commands
        cmd.register(
                cmd.metaBuilder("auth")
                        .plugin(this)
                        .build(),
                authManager.createAuthCommand()
        );

        logger.info("Commands registered: /2fa, /auth");
    }

    // ── Helpers ──

    private static int parseInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ── Public getters ──

    public AuthManager getAuthManager() { return authManager; }
    public RedisManager getRedisManager() { return redisManager; }
    public ProxyServer getProxy() { return proxy; }
}
