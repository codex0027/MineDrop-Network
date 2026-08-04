package net.minedrop.core.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
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
import net.minedrop.core.cache.PlayerCache;
import net.minedrop.core.packet.PacketDispatcher;
import net.minedrop.core.redis.RedisManager;
import net.minedrop.core.registry.ServerRegistry;
import net.minedrop.core.session.SessionManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Velocity-side entry point for MDN-Core.
 * <p>
 * Manages server registry, player sessions, smart routing, and proxy-level commands.
 * Reads configuration from plugins/mdn-core/config.yml.
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

        // ── Load config ──
        loadConfiguration();

        // ── Initialize Redis ──
        redisManager = new RedisManager(redisHost, redisPort, redisPassword,
                2000, "mdn_packet_bus");

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
        sessionManager.createSession(player.getUniqueId(), player.getUsername(), "lobby");
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

    // ── Configuration loading ──

    private void loadConfiguration() {
        Path configFile = Path.of("plugins", "mdn-core", "config.yml");
        if (!Files.exists(configFile)) {
            logger.warn("No config.yml found — using defaults.");
            return;
        }
        try {
            for (String line : Files.readAllLines(configFile)) {
                line = line.trim();
                if (line.startsWith("host:")) {
                    redisHost = extractValue(line);
                } else if (line.startsWith("port:")) {
                    try { redisPort = Integer.parseInt(extractValue(line)); } catch (NumberFormatException ignored) {}
                } else if (line.startsWith("password:")) {
                    redisPassword = extractValue(line);
                } else if (line.startsWith("default-region:")) {
                    defaultRegion = extractValue(line);
                }
            }
            logger.info("Config loaded: redis={}:{}, region={}", redisHost, redisPort, defaultRegion);
        } catch (IOException e) {
            logger.error("Failed to read config", e);
        }
    }

    private static String extractValue(String line) {
        return line.substring(line.indexOf(':') + 1).trim().replace("\"", "").replace("'", "");
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
        register(cmd, "website", msg("§bWebsite: §fhttps://minedrop.net"));
        register(cmd, "store", msg("§6Store: §fhttps://store.minedrop.net"));
        register(cmd, "vote", msg("§aVote: §fhttps://minedrop.net/vote"));
        register(cmd, "discord", msg("§9Discord: §fhttps://discord.gg/minedrop"));
        register(cmd, "help", (SimpleCommand) invocation -> {
            invocation.source().sendMessage(Component.text("MineDrop Network Help", NamedTextColor.YELLOW, TextDecoration.BOLD));
            invocation.source().sendMessage(Component.text("/mdn servers — View all registered servers", NamedTextColor.GRAY));
            invocation.source().sendMessage(Component.text("/mdn health — Network health overview", NamedTextColor.GRAY));
            invocation.source().sendMessage(Component.text("/website — Server website", NamedTextColor.GRAY));
            invocation.source().sendMessage(Component.text("/discord — Join our Discord", NamedTextColor.GRAY));
        });
        register(cmd, "rules", msg("§c§lRules: §7No cheating, be respectful, no inappropriate content."));
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

    private void discoverServers() {
        for (RegisteredServer server : proxy.getAllServers()) {
            String name = server.getServerInfo().getName();
            serverRegistry.registerServer(name, new ServerRegistry.ServerInfo(
                    name, inferServerGroup(name), defaultRegion,
                    100
            ));
        }
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
                        String health = s.isHealthy() ? "§a✓" : "§c⚠";
                        invocation.source().sendMessage(Component.text(String.format(
                                " %s §7%s §f%d/%d §7TPS:%.1f §7%s",
                                health, s.getName(), s.getPlayerCount(),
                                s.getMaxPlayers(), s.getTps(), s.getServerGroup())));
                    }
                }
                case "health" -> {
                    invocation.source().sendMessage(Component.text("Network Health:", NamedTextColor.GREEN, TextDecoration.BOLD));
                    invocation.source().sendMessage(Component.text("  Servers: " + serverRegistry.getOnlineServerCount(), NamedTextColor.GRAY));
                    invocation.source().sendMessage(Component.text("  Online players (proxy): " + proxy.getPlayerCount(), NamedTextColor.GRAY));
                    invocation.source().sendMessage(Component.text("  Active sessions: " + sessionManager.getOnlineCount(), NamedTextColor.GRAY));
                    invocation.source().sendMessage(Component.text("  Redis: " + (redisManager.isConnected() ? "§aconnected" : "§cDISCONNECTED"), NamedTextColor.GRAY));
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
