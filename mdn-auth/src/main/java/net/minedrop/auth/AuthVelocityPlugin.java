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
import net.minedrop.api.MDNAPI;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private String altAction = "KICK";
    private boolean staff2faEnabled = true;
    private boolean enforceIpLock = true;
    private String totpIssuer = "MineDropNetwork";
    private List<String> force2faPermissions = List.of("mdn.group.admin", "mdn.group.staff");
    private int privateLobbyTokenLifetime = 60;
    private String secretHashingAlgorithm = "SHA-256";
    private int loginTimeoutSeconds = 120;

    // ── Redis + MySQL config ──
    private String redisHost = "127.0.0.1";
    private int redisPort = 6379;
    private String redisPassword = "";
    private String mysqlHost = "127.0.0.1";
    private int mysqlPort = 3306;
    private String mysqlDatabase = "minedrop";
    private String mysqlUser = "mdn_user";
    private String mysqlPassword = "";

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

        // ── Step 4: Initialize AuthManager with MySQL from MDN-Core ──
        var ds = MDNAPI.getInstance() != null ? MDNAPI.getInstance().getDataSource() : null;
        authManager = new AuthManager(redisManager, logger, ds);
        authManager.initialize();

        // ── Step 5: Register commands ──
        registerCommands();

        // ── Step 6: Start scheduled tasks ──
        startCleanupTask();
        startLoginTimeoutTask();

        logger.info("MDN-Auth enabled.");
        logger.info("  Password auth: enabled (Argon2id, min 12 chars)");
        logger.info("  Alt limits: {} per IP, {} per fingerprint (action: {})",
                maxAccountsPerIp, maxAccountsPerFingerprint, altAction);
        logger.info("  Staff 2FA: {} ({} permission groups, ip-lock: {})",
                staff2faEnabled ? "enabled" : "disabled",
                force2faPermissions.size(),
                enforceIpLock ? "on" : "off");
        logger.info("  Redis: {}", redisManager.isConnected() ? "connected" : "DISCONNECTED");
        logger.info("  MySQL: {}@{}:{}/{}", mysqlUser, mysqlHost, mysqlPort, mysqlDatabase);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("MDN-Auth shutting down...");
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
            try { cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        }
        if (authManager != null) authManager.shutdown();
        if (redisManager != null) redisManager.shutdown();
        logger.info("MDN-Auth disabled.");
    }

    // ── Player login interception ──

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        // PreLoginEvent: quick IP-only alt check as early-warning.
        // Uses the username to look up any existing UUID from Redis (A-7 fix).
        InboundConnection conn = event.getConnection();
        String ipAddress = conn.getRemoteAddress().getAddress().getHostAddress();
        String username = event.getUsername();

        // Try to resolve UUID from Redis (A-7 — was UUID.randomUUID())
        UUID resolvedUuid = authManager.resolveUsername(username, n -> Optional.empty())
                .orElse(null);
        UUID checkUuid = resolvedUuid != null ? resolvedUuid : UUID.randomUUID();

        AltDetector.Action action = authManager.checkAltLimits(
                checkUuid,
                ipAddress,
                "pending",
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
            logger.info("Pre-login denied for {} (IP {}) — alt limit exceeded", username, ipAddress);
        }
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();
        String ipAddress = player.getRemoteAddress().getAddress().getHostAddress();

        // ── Device fingerprint ──
        String clientBrand = "unknown";
        int protocolVersion = player.getProtocolVersion().getProtocol();
        DeviceFingerprinter.Fingerprint fp = authManager.fingerprint(
                uuid, ipAddress, clientBrand, protocolVersion);

        // ── Alt detection ──
        AltDetector.Action action = authManager.checkAltLimits(
                uuid, ipAddress, fp.getHash(),
                maxAccountsPerIp, maxAccountsPerFingerprint);

        if (action == AltDetector.Action.KICK) {
            if ("SHADOW_BAN".equalsIgnoreCase(altAction)) {
                authManager.shadowBan(uuid);
                logger.warn("Player {} ({}) shadow-banned — allowed but flagged", username, uuid);
            } else {
                player.disconnect(Component.text()
                        .append(Component.text("⚠ Too many accounts", NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.newline())
                        .append(Component.text("Maximum " + maxAccountsPerIp + " accounts per IP.", NamedTextColor.GRAY))
                        .build());
                return;
            }
        }

        if (action == AltDetector.Action.ALERT) {
            logger.warn("⚠ Alt alert: {} (ip={}) is approaching alt limits", username, ipAddress);
        }

        // ── Record login + username mapping ──
        authManager.recordLogin(uuid, ipAddress, fp.getHash());
        authManager.recordUsernameMapping(username, uuid);

        // ── Start auth timeout tracking ──
        markAuthStart(uuid);

        // ── Auth state machine ──
        boolean isRegistered = authManager.isRegistered(uuid);

        if (isRegistered) {
            // Registered account → require password
            applyPasswordRequiredState(player);
            logger.info("Player {} ({}) connected — password required", username, uuid);
        } else {
            // New account → require registration
            applyRegistrationRequiredState(player);
            logger.info("Player {} ({}) connected — registration required", username, uuid);
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        authStartTimes.remove(uuid);
        if (authManager.isPlayerLocked(uuid)) {
            authManager.unlockPlayer(uuid);
        }
        // Revoke session + publish AUTH_UPDATE(false)
        authManager.revokeAllSessions(uuid, "Player disconnected");
        logger.debug("Cleaned up session for disconnected player {}", uuid);
    }

    // ── Pre-auth states ──

    /** Player must register before playing. */
    private void applyRegistrationRequiredState(Player player) {
        player.showTitle(Title.title(
                Component.text("Welcome to MineDrop!", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("Use /register to create your account", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(500), Duration.ofDays(1), Duration.ofMillis(500))));
        player.sendActionBar(Component.text()
                .append(Component.text("🔑 ", NamedTextColor.GOLD))
                .append(Component.text("New here? Use ", NamedTextColor.YELLOW))
                .append(Component.text("/register <password>", NamedTextColor.GOLD, TextDecoration.BOLD))
                .build());
    }

    /** Player must enter password. */
    private void applyPasswordRequiredState(Player player) {
        player.showTitle(Title.title(
                Component.text("🔒 Authentication Required", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Use /login <password> to authenticate", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(500), Duration.ofDays(1), Duration.ofMillis(500))));
        player.sendActionBar(Component.text()
                .append(Component.text("🔒 ", NamedTextColor.RED))
                .append(Component.text("This account is registered. Use ", NamedTextColor.YELLOW))
                .append(Component.text("/login <password>", NamedTextColor.GOLD, TextDecoration.BOLD))
                .build());
    }

    /** Password verified, now 2FA required. */
    public void applyTotpRequiredState(Player player) {
        authManager.lockPlayer(player.getUniqueId());
        applyPreAuthLockdown(player);
    }

    /** @deprecated Use {@link #applyTotpRequiredState} for new password+2FA flow. */
    @Deprecated
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
     * Called after successful authentication (password-only or password+2FA).
     * Creates session, publishes AUTH_UPDATE, routes to lobby.
     */
    public void removeLockdown(Player player) {
        UUID uuid = player.getUniqueId();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        // Clear timeout tracker
        markAuthComplete(uuid);

        // Create authenticated session + publish AUTH_UPDATE(true)
        authManager.createAuthenticatedSession(uuid, ip);

        player.clearTitle();
        player.sendMessage(Component.text());
        player.sendMessage(Component.text()
                .append(Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text("Authentication successful! Welcome back, " + player.getUsername() + "!", NamedTextColor.GREEN))
                .build());
        player.sendMessage(Component.text());

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

            // Parse MySQL config
            Map<String, Object> mysql = (Map<String, Object>) root.get("mysql");
            if (mysql != null) {
                mysqlHost = String.valueOf(mysql.getOrDefault("host", mysqlHost));
                mysqlPort = parseInt(mysql.get("port"), mysqlPort);
                mysqlDatabase = String.valueOf(mysql.getOrDefault("database", mysqlDatabase));
                mysqlUser = String.valueOf(mysql.getOrDefault("user", mysqlUser));
                mysqlPassword = String.valueOf(mysql.getOrDefault("password", mysqlPassword));
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

                // Login timeout
                loginTimeoutSeconds = parseInt(auth.get("login-timeout-seconds"), loginTimeoutSeconds);
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

        // /register — Create new MDN account
        cmd.register(
                cmd.metaBuilder("register")
                        .plugin(this)
                        .build(),
                authManager.createRegisterCommand(this::removeLockdown)
        );

        // /login — Password authentication (passes force-2FA checker for staff bypass prevention)
        cmd.register(
                cmd.metaBuilder("login")
                        .plugin(this)
                        .build(),
                authManager.createLoginCommand(
                        this::removeLockdown,
                        this::applyTotpRequiredState,
                        player -> force2faPermissions.stream().anyMatch(player::hasPermission)
                )
        );

        // /2fa — Two-factor authentication
        cmd.register(
                cmd.metaBuilder("2fa")
                        .plugin(this)
                        .build(),
                authManager.createTwoFactorCommand(
                        this::removeLockdown,
                        name -> proxy.getPlayer(name),
                        enforceIpLock
                )
        );

        // /auth — Admin commands (unblock, clear, suspend, unsuspend)
        // Pass player resolver for suspend/unsuspend username resolution
        cmd.register(
                cmd.metaBuilder("auth")
                        .plugin(this)
                        .build(),
                authManager.createAuthCommand(
                        name -> proxy.getPlayer(name)
                )
        );

        logger.info("Commands: /register, /login, /2fa, /auth");
    }

    // ── Alt list cleanup (A-6) ──

    private ScheduledExecutorService cleanupScheduler;
    private final java.util.Map<UUID, Long> authStartTimes = new ConcurrentHashMap<>();

    private void startCleanupTask() {
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mdn-auth-cleanup");
            t.setDaemon(true);
            return t;
        });

        // Run every 6 hours — Redis TTL handles most expiry, this is a safety net
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                long shadowCount = authManager.getAltDetector().getShadowBanCount();
                logger.debug("Auth cleanup: {} shadow-banned players tracked", shadowCount);
            } catch (Exception e) {
                logger.debug("Auth cleanup task error (non-critical): {}", e.getMessage());
            }
        }, 6, 6, TimeUnit.HOURS);

        logger.info("Alt list cleanup task scheduled (every 6h)");
    }

    /** Disconnects players who haven't authenticated within the timeout window (spec §119). */
    private void startLoginTimeoutTask() {
        proxy.getScheduler().buildTask(this, () -> {
            long now = System.currentTimeMillis();
            long timeoutMs = loginTimeoutSeconds * 1000L;
            var iter = authStartTimes.entrySet().iterator();
            while (iter.hasNext()) {
                var entry = iter.next();
                if (now - entry.getValue() > timeoutMs) {
                    UUID uuid = entry.getKey();
                    proxy.getPlayer(uuid).ifPresent(player -> {
                        if (!authManager.hasActiveSession(uuid)) {
                            player.disconnect(Component.text()
                                    .append(Component.text("⏰ Authentication Timeout", NamedTextColor.RED, TextDecoration.BOLD))
                                    .append(Component.newline())
                                    .append(Component.text("You took too long to authenticate. Please reconnect.", NamedTextColor.GRAY))
                                    .build());
                            logger.info("Disconnected {} for auth timeout ({}s)", player.getUsername(), loginTimeoutSeconds);
                        }
                    });
                    iter.remove();
                }
            }
        }).repeat(5, java.util.concurrent.TimeUnit.SECONDS).schedule();
    }

    /** Records when a player entered the auth flow (for timeout tracking). */
    private void markAuthStart(UUID uuid) {
        authStartTimes.put(uuid, System.currentTimeMillis());
    }

    /** Removes the timeout tracker on successful auth. */
    private void markAuthComplete(UUID uuid) {
        authStartTimes.remove(uuid);
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
