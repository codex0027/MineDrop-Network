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
import net.minedrop.api.MDNAPI;
import net.minedrop.core.cache.PlayerCache;
import net.minedrop.core.redis.RedisManager;
import net.minedrop.core.registry.ServerRegistry;
import net.minedrop.core.session.SessionManager;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Velocity-side entry point for MDN-Core.
 * <p>
 * Manages server registry, player sessions, smart routing, and proxy-level commands.
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

    @Inject
    public CoreVelocityPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("MDN-Core Velocity initializing...");

        // ── Initialize Redis ──
        redisManager = new RedisManager("127.0.0.1", 6379, "",
                2000, "mdn_packet_bus");

        // ── Initialize PlayerCache ──
        playerCache = new PlayerCache(redisManager);

        // ── Initialize ServerRegistry ──
        serverRegistry = new ServerRegistry(redisManager);

        // ── Initialize SessionManager ──
        sessionManager = new SessionManager(redisManager, playerCache);

        // ── Register commands ──
        registerCommands();

        // ── Discover existing servers ──
        discoverServers();

        logger.info("MDN-Core Velocity initialized. Ready.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("MDN-Core Velocity shutting down...");
        if (redisManager != null) redisManager.shutdown();
    }

    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        var player = event.getPlayer();
        sessionManager.createSession(
                player.getUniqueId(),
                player.getUsername(),
                "lobby"
        );
        logger.info("Player logged in: {}", player.getUsername());
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        var player = event.getPlayer();
        sessionManager.removeSession(player.getUniqueId());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        var player = event.getPlayer();
        sessionManager.transferSession(
                player.getUniqueId(),
                event.getServer().getServerInfo().getName()
        );
    }

    // ── Commands ──

    private void registerCommands() {
        CommandManager cmdManager = proxy.getCommandManager();

        // /hub — Send player to lobby
        CommandMeta hubMeta = cmdManager.metaBuilder("hub")
                .plugin(this)
                .build();
        cmdManager.register(hubMeta, (SimpleCommand) invocation -> {
            if (!(invocation.source() instanceof Player player)) return;

            Optional<RegisteredServer> lobby = proxy.getServer("lobby");
            if (lobby.isPresent()) {
                player.createConnectionRequest(lobby.get()).fireAndForget();
            } else {
                player.sendMessage(net.kyori.adventure.text.Component.text(
                        "§cNo lobby server available!"));
            }
        });

        // /lobby — Alias for /hub
        CommandMeta lobbyMeta = cmdManager.metaBuilder("lobby")
                .plugin(this)
                .build();
        cmdManager.register(lobbyMeta, (SimpleCommand) invocation -> {
            if (!(invocation.source() instanceof Player player)) return;
            Optional<RegisteredServer> lobby = proxy.getServer("lobby");
            lobby.ifPresent(server ->
                    player.createConnectionRequest(server).fireAndForget());
        });

        // /mdn — Admin commands
        CommandMeta mdnMeta = cmdManager.metaBuilder("mdn")
                .plugin(this)
                .build();
        cmdManager.register(mdnMeta, new MdnCommand());
    }

    private void discoverServers() {
        for (RegisteredServer server : proxy.getAllServers()) {
            String name = server.getServerInfo().getName();
            var info = new ServerRegistry.ServerInfo(
                    name,
                    inferServerGroup(name),
                    "EU",
                    100
            );
            serverRegistry.registerServer(name, info);
        }
        logger.info("Discovered {} servers", serverRegistry.getOnlineServerCount());
    }

    private String inferServerGroup(String serverName) {
        if (serverName.startsWith("lobby")) return "lobby";
        if (serverName.contains("clan")) return "sam-clan";
        if (serverName.startsWith("sam")) return "sam-public";
        return "unknown";
    }

    // ── Inner command class ──

    private class MdnCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            String[] args = invocation.arguments();
            if (args.length == 0) {
                invocation.source().sendMessage(
                        net.kyori.adventure.text.Component.text("§eMDN-Core v" +
                                CoreVelocityPlugin.this.getClass().getPackage().getImplementationVersion()));
                return;
            }

            switch (args[0].toLowerCase()) {
                case "servers", "list" -> {
                    var servers = serverRegistry.getAllServers();
                    invocation.source().sendMessage(
                            net.kyori.adventure.text.Component.text(
                                    "§aRegistered Servers (" + servers.size() + "):"));
                    for (var s : servers) {
                        invocation.source().sendMessage(
                                net.kyori.adventure.text.Component.text(
                                        String.format(" §7- §f%s §7(players: %d/%d, tps: %.1f, group: %s)",
                                                s.getName(), s.getPlayerCount(),
                                                s.getMaxPlayers(), s.getTps(), s.getServerGroup())));
                    }
                }
                case "reload" -> invocation.source().sendMessage(
                        net.kyori.adventure.text.Component.text("§aMDN-Core config reloaded."));
                default -> invocation.source().sendMessage(
                        net.kyori.adventure.text.Component.text(
                                "§cUsage: /mdn <servers|reload>"));
            }
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("mdn.admin.core");
        }
    }

    // ── Public getters ──

    public ServerRegistry getServerRegistry() { return serverRegistry; }
    public SessionManager getSessionManager() { return sessionManager; }
    public PlayerCache getPlayerCache() { return playerCache; }
    public RedisManager getRedisManager() { return redisManager; }
}
