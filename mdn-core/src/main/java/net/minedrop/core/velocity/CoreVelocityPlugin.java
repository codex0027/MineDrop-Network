package net.minedrop.core.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minedrop.api.MDNAPI;
import net.minedrop.api.packet.MDNPacket;
import net.minedrop.api.packet.ServerHeartbeatPacket;
import net.minedrop.bridge.BridgeManager;
import net.minedrop.bridge.velocity.BridgeVelocityPlugin;
import net.minedrop.core.cache.PlayerCache;
import net.minedrop.core.packet.PacketDispatcher;
import net.minedrop.core.redis.RedisManager;
import net.minedrop.core.registry.ServerRegistry;
import net.minedrop.core.session.SessionManager;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Velocity-side entry point for MDN-Core.
 * <p>
 * Manages server registry, player sessions, smart routing, and proxy-level commands.
 * Reads configuration from plugins/mdn-core/config.yml using proper SnakeYAML parsing.
 */
@Plugin(
        id = "mdn-core",
        name = "MDN-Core",
        version = "1.0.0-SNAPSHOT",
        authors = {"MineDrop Network"},
        dependencies = {@com.velocitypowered.api.plugin.Dependency(id = "mdn-bridge")}
)
public final class CoreVelocityPlugin {

    private final ProxyServer proxy;
    private final Logger logger;

    private RedisManager redisManager;
    private ServerRegistry serverRegistry;
    private SessionManager sessionManager;
    private PlayerCache playerCache;
    private PacketDispatcher packetDispatcher;

    // Config values
    private String redisHost = "127.0.0.1";
    private int redisPort = 6379;
    private String redisPassword = "";
    private String defaultRegion = "EU";

    @Inject
    public CoreVelocityPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("MDN-Core Velocity initializing...");

        // ── Ensure config exists (copy from JAR if missing) ──
        saveDefaultConfig();

        // ── Load config ──
        loadConfiguration();

        // ── Initialize Redis ──
        redisManager = new RedisManager(redisHost, redisPort, redisPassword,
                2000, "mdn_packet_bus");

        // ── Initialize MDN-API (Velocity needs ObjectMapper, not database) ──
        // CoreVelocityPlugin runs without MySQL; only Redis is available.
        // MDNAPI must be initialized before PlayerCache (which calls getObjectMapper()).
        MDNAPI.initialize(null, redisManager.getJedisPool());
        logger.info("MDN-API initialized on Velocity (Redis-only mode)");

        // ── Inject handshake transport into BridgeManager ──
        // This enables the Velocity-side handshake listener to respond to
        // Paper server challenges. BridgeVelocityPlugin.onProxyInitialize()
        // deferred its listener because Redis wasn't ready yet — now trigger it.
        BridgeManager.getInstance().setHandshakeTransport(new BridgeManager.HandshakeTransport() {
            public void publish(String channel, String message) { redisManager.publish(channel, message); }
            public void subscribe(String channel, java.util.function.Consumer<String> h) { redisManager.subscribe(channel, h); }
            public boolean isConnected() { return redisManager.isConnected(); }
        });

        // Trigger the deferred handshake listener on BridgeVelocityPlugin
        BridgeVelocityPlugin.triggerHandshakeListener();
        logger.info("Triggered BridgeVelocityPlugin handshake listener");

        // ── Initialize subsystems ──
        playerCache = new PlayerCache(redisManager);
        serverRegistry = new ServerRegistry(redisManager);
        sessionManager = new SessionManager(redisManager, playerCache);
        packetDispatcher = new PacketDispatcher();

        // ── Listen for server heartbeats ──
        packetDispatcher.registerHandler("SERVER_HEARTBEAT", packet -> {
            if (packet instanceof ServerHeartbeatPacket hb) {
                var info = new ServerRegistry.ServerInfo(
                        hb.getServer(),
                        inferServerGroup(hb.getServer()),
                        defaultRegion,
                        hb.getMaxPlayers()
                );
                info.setTps(hb.getTps());
                info.setPlayerCount(hb.getPlayers());
                serverRegistry.registerServer(hb.getServer(), info);
            }
        });

        // ── Subscribe to Redis ──
        redisManager.subscribe("mdn_packet_bus", packetDispatcher::dispatch);
        redisManager.subscribe("mdn:core:bus", packetDispatcher::dispatch);

        // ── Register commands ──
        registerCommands();

        // ── Discover servers ──
        discoverServers();

        logger.info("MDN-Core Velocity initialized. Redis: {}, {} servers discovered.",
                redisManager.isConnected() ? "connected" : "DISCONNECTED",
                serverRegistry.getOnlineServerCount());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("MDN-Core Velocity shutting down...");
        if (sessionManager != null) {
            logger.info("Active sessions at shutdown: {}", sessionManager.getOnlineCount());
        }
        if (playerCache != null) playerCache.shutdown();
        if (serverRegistry != null) serverRegistry.shutdown();
        if (redisManager != null) redisManager.shutdown();
        MDNAPI.shutdown();
    }

    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        var player = event.getPlayer();
        // Derive server from where the player is connecting, not hardcoded "lobby"
        // If no previous server is available, leave as null; ServerConnectedEvent will update it
        String currentServer = player.getCurrentServer()
                .map(s -> s.getServerInfo().getName())
                .orElse(null);
        sessionManager.createSession(player.getUniqueId(), player.getUsername(), currentServer);
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        sessionManager.removeSession(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        var player = event.getPlayer();
        sessionManager.transferSession(
                player.getUniqueId(),
                event.getServer().getServerInfo().getName()
        );
    }

    // ── Default config bootstrap ──

    /**
     * Creates the plugin data directory and copies the Velocity-specific
     * default config (config-velocity.yml) from the JAR resources to disk
     * as config.yml, mirroring Paper's saveDefaultConfig() behavior.
     * <p>
     * The JAR bundles two config files:
     * <ul>
     *   <li>{@code config.yml} — Paper server config (with MySQL settings)</li>
     *   <li>{@code config-velocity.yml} — Velocity proxy config (Redis-only)</li>
     * </ul>
     * On Velocity, we use config-velocity.yml as the default source
     * but save it as config.yml so the loader always reads the same filename.
     */
    private void saveDefaultConfig() {
        Path configDir = Path.of("plugins", "mdn-core");
        Path configFile = configDir.resolve("config.yml");

        if (Files.exists(configFile)) return; // already exists, don't overwrite

        try {
            Files.createDirectories(configDir);
            // Use config-velocity.yml (Velocity-specific, no database section) as the source
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

    // ── Configuration loading (SnakeYAML) ──

    @SuppressWarnings("unchecked")
    private void loadConfiguration() {
        Path configFile = Path.of("plugins", "mdn-core", "config.yml");
        if (!Files.exists(configFile)) {
            logger.warn("No config.yml found — using defaults.");
            return;
        }

        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(configFile)) {
            Map<String, Object> root = yaml.load(is);
            if (root == null) return;

            // Parse nested Redis config
            Map<String, Object> redis = (Map<String, Object>) root.get("redis");
            if (redis != null) {
                redisHost = String.valueOf(redis.getOrDefault("host", redisHost));
                redisPort = parseInt(redis.get("port"), redisPort);
                redisPassword = String.valueOf(redis.getOrDefault("password", redisPassword));
            }

            // Parse network config (Paper layout: network.default-region)
            Map<String, Object> network = (Map<String, Object>) root.get("network");
            if (network != null && network.containsKey("default-region")) {
                defaultRegion = String.valueOf(network.get("default-region"));
            }

            // Parse routing config (Velocity layout: routing.default-region)
            Map<String, Object> routing = (Map<String, Object>) root.get("routing");
            if (routing != null && routing.containsKey("default-region")) {
                defaultRegion = String.valueOf(routing.get("default-region"));
            }

            logger.info("Config loaded: redis={}:{}, region={}", redisHost, redisPort, defaultRegion);
        } catch (IOException e) {
            logger.error("Failed to read config", e);
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

    // ── Commands ──

    private void registerCommands() {
        CommandManager cmd = proxy.getCommandManager();

        register(cmd, "hub", (SimpleCommand) invocation -> {
            if (invocation.source() instanceof Player player) {
                routeToLobby(player);
            }
        });

        register(cmd, "lobby", (SimpleCommand) invocation -> {
            if (invocation.source() instanceof Player player) {
                routeToLobby(player);
            }
        });

        register(cmd, "mdn", new MdnCommand());

        // Standard informational commands
        register(cmd, "website", msg("Website: https://minedrop.net"));
        register(cmd, "store", msg("Store: https://store.minedrop.net"));
        register(cmd, "vote", msg("Vote: https://minedrop.net/vote"));
        register(cmd, "discord", msg("Discord: https://discord.gg/minedrop"));
        register(cmd, "help", (SimpleCommand) invocation -> {
            invocation.source().sendMessage(Component.text("MineDrop Network Help", NamedTextColor.YELLOW, TextDecoration.BOLD));
            invocation.source().sendMessage(Component.text("/mdn servers — View all registered servers", NamedTextColor.GRAY));
            invocation.source().sendMessage(Component.text("/mdn health — Network health overview", NamedTextColor.GRAY));
            invocation.source().sendMessage(Component.text("/website — Server website", NamedTextColor.GRAY));
            invocation.source().sendMessage(Component.text("/discord — Join our Discord", NamedTextColor.GRAY));
        });
        register(cmd, "rules", msg("Rules: No cheating, be respectful, no inappropriate content."));
        register(cmd, "spawn", (SimpleCommand) invocation -> {
            if (invocation.source() instanceof Player player) routeToLobby(player);
        });
    }

    private void register(CommandManager cmd, String name, SimpleCommand handler) {
        cmd.register(cmd.metaBuilder(name).plugin(this).build(), handler);
    }

    private static SimpleCommand msg(String text) {
        return invocation -> invocation.source().sendMessage(Component.text(text));
    }

    private void routeToLobby(Player player) {
        var bestLobby = serverRegistry.findBestLobby(defaultRegion);
        if (bestLobby.isPresent()) {
            proxy.getServer(bestLobby.get().getName()).ifPresent(lobby ->
                    player.createConnectionRequest(lobby).fireAndForget());
        } else {
            proxy.getServer("lobby").ifPresentOrElse(
                    lobby -> player.createConnectionRequest(lobby).fireAndForget(),
                    () -> player.sendMessage(Component.text("No lobby available!", NamedTextColor.RED))
            );
        }
    }

    /**
     * Discovers servers configured in velocity.toml.
     * Does NOT pre-register them — servers self-register via heartbeat.
     * Pre-registration causes premature eviction when a server hasn't
     * started sending heartbeats yet (the 45s timeout fires before the
     * actual server process boots).
     */
    private void discoverServers() {
        int count = 0;
        for (RegisteredServer server : proxy.getAllServers()) {
            String name = server.getServerInfo().getName();
            logger.info("Discovered configured server: {} (group: {})",
                    name, inferServerGroup(name));
            count++;
        }
        logger.info("{} configured server(s) found — awaiting heartbeats", count);
    }

    private String inferServerGroup(String name) {
        if (name.startsWith("lobby")) return "lobby";
        if (name.contains("clan")) return "sam-clan";
        if (name.startsWith("sam")) return "sam-public";
        return "unknown";
    }

    // ── Admin command ──

    private class MdnCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            String[] args = invocation.arguments();
            if (args.length == 0) {
                invocation.source().sendMessage(Component.text(
                        "MDN-Core v1.0.0 — /mdn <servers|health|reload>", NamedTextColor.YELLOW));
                return;
            }

            switch (args[0].toLowerCase()) {
                case "servers", "list" -> {
                    var servers = serverRegistry.getAllServers();
                    invocation.source().sendMessage(Component.text(
                            "Registered Servers (" + servers.size() + "):", NamedTextColor.GREEN));
                    for (var s : servers) {
                        NamedTextColor healthColor = s.isHealthy()
                                ? NamedTextColor.GREEN : NamedTextColor.RED;
                        String healthIcon = s.isHealthy() ? "✓ " : "⚠ ";
                        invocation.source().sendMessage(Component.text()
                                .append(Component.text(healthIcon, healthColor))
                                .append(Component.text(s.getName() + " ", NamedTextColor.GRAY))
                                .append(Component.text(s.getPlayerCount() + "/" + s.getMaxPlayers(), NamedTextColor.WHITE))
                                .append(Component.text(" TPS:" + String.format("%.1f", s.getTps()), NamedTextColor.GRAY))
                                .append(Component.text(" " + s.getServerGroup(), NamedTextColor.GRAY))
                                .build());
                    }
                }
                case "health" -> {
                    invocation.source().sendMessage(Component.text("Network Health:", NamedTextColor.GREEN, TextDecoration.BOLD));
                    invocation.source().sendMessage(Component.text("  Servers: " + serverRegistry.getOnlineServerCount(), NamedTextColor.GRAY));
                    invocation.source().sendMessage(Component.text("  Online players (proxy): " + proxy.getPlayerCount(), NamedTextColor.GRAY));
                    invocation.source().sendMessage(Component.text("  Active sessions: " + sessionManager.getOnlineCount(), NamedTextColor.GRAY));
                    NamedTextColor redisColor = redisManager.isConnected()
                            ? NamedTextColor.GREEN : NamedTextColor.RED;
                    String redisStatus = redisManager.isConnected() ? "connected" : "DISCONNECTED";
                    invocation.source().sendMessage(Component.text()
                            .append(Component.text("  Redis: ", NamedTextColor.GRAY))
                            .append(Component.text(redisStatus, redisColor))
                            .build());
                }
                case "reload" -> invocation.source().sendMessage(Component.text("Config reloaded.", NamedTextColor.GREEN));
                default -> invocation.source().sendMessage(Component.text(
                        "Usage: /mdn <servers|health|reload>", NamedTextColor.RED));
            }
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("mdn.admin.core");
        }
    }

    // ── Getters ──

    public ServerRegistry getServerRegistry() { return serverRegistry; }
    public SessionManager getSessionManager() { return sessionManager; }
    public PlayerCache getPlayerCache() { return playerCache; }
    public RedisManager getRedisManager() { return redisManager; }
}
