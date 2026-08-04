package net.minedrop.core.paper;

import net.minedrop.api.ApiVersion;
import net.minedrop.api.MDNAPI;
import net.minedrop.core.cache.PlayerCache;
import net.minedrop.core.database.DatabaseManager;
import net.minedrop.core.packet.PacketDispatcher;
import net.minedrop.core.redis.RedisManager;
import net.minedrop.core.sync.DataSyncEngine;
import net.minedrop.core.sync.InventorySyncManager;
import net.minedrop.core.util.CircuitBreaker;
import net.minedrop.core.util.DeadLetterQueue;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.logging.Level;

/**
 * Paper-side entry point for MDN-Core.
 * <p>
 * Initializes and orchestrates the database, Redis, player cache, data sync engine,
 * packet dispatcher, circuit breakers, and periodic server heartbeat.
 * <p>
 * On startup, validates configuration by running health checks against MySQL and Redis.
 * Fails fast with clear error messages if critical services are unreachable.
 */
public final class CorePaperPlugin extends JavaPlugin implements Listener {

    // ── Infrastructure ──
    private DatabaseManager databaseManager;
    private RedisManager redisManager;
    private PlayerCache playerCache;
    private DataSyncEngine dataSyncEngine;
    private InventorySyncManager inventorySyncManager;
    private PacketDispatcher packetDispatcher;

    // ── Resilience ──
    private CircuitBreaker dbCircuitBreaker;
    private CircuitBreaker redisCircuitBreaker;
    private DeadLetterQueue deadLetterQueue;

    private int heartbeatTaskId = -1;

    @Override
    public void onLoad() {
        getLogger().info("MDN-Core Paper loading...");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        // ── Log API version ──
        getLogger().info("MDN-API version: " + ApiVersion.CURRENT);

        // ── Step 1: Validate config ──
        if (!validateConfiguration()) {
            getLogger().severe("Configuration validation FAILED. Plugin will not enable.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // ── Step 2: Initialize Database ──
        initDatabase();

        // ── Step 3: Initialize Redis ──
        initRedis();

        // ── Step 4: Startup health checks ──
        if (!performHealthChecks()) {
            getLogger().severe("Startup health checks FAILED. Check MySQL and Redis connectivity.");
            // Don't disable — allow degraded mode
        }

        // ── Step 5: Initialize Circuit Breakers ──
        dbCircuitBreaker = new CircuitBreaker("mysql", 5, Duration.ofSeconds(30));
        redisCircuitBreaker = new CircuitBreaker("redis", 5, Duration.ofSeconds(30));

        // ── Step 6: Initialize MDN-API ──
        MDNAPI.initialize(databaseManager.getDataSource(), redisManager.getJedisPool());
        getLogger().info("MDN-API initialized (instance: " + MDNAPI.getInstance().getInstanceId() + ")");

        // ── Step 7: Initialize subsystems (create PacketDispatcher FIRST) ──
        // Order matters: DeadLetterQueue's retry lambda captures packetDispatcher,
        // so packetDispatcher MUST exist before deadLetterQueue is created.
        playerCache = new PlayerCache(redisManager);
        inventorySyncManager = new InventorySyncManager();
        dataSyncEngine = new DataSyncEngine(redisManager, inventorySyncManager);
        packetDispatcher = new PacketDispatcher();

        // ── Step 8: Initialize Dead Letter Queue ──
        // Now safe: packetDispatcher is already assigned and ready for retry dispatch
        deadLetterQueue = new DeadLetterQueue(redisManager, rawJson -> {
            // Retry handler: re-dispatch through the packet dispatcher
            packetDispatcher.dispatch(rawJson);
        });
        packetDispatcher.setDeadLetterQueue(deadLetterQueue);

        // ── Step 9: Start periodic tasks ──
        startPeriodicSave();
        startHeartbeat();

        // ── Step 10: Register events ──
        getServer().getPluginManager().registerEvents(this, this);

        // ── Step 11: Subscribe to Redis packet bus ──
        redisManager.subscribe("mdn_packet_bus", packetDispatcher::dispatch);

        getLogger().info("MDN-Core Paper enabled. All systems ready.");
        getLogger().info("  API: v" + ApiVersion.CURRENT
                + " | DB: " + (databaseManager.isConnected() ? "✓" : "✗")
                + " | Redis: " + (redisManager.isConnected() ? "✓" : "✗")
                + " | CB-DB: " + dbCircuitBreaker.getState()
                + " | CB-Redis: " + redisCircuitBreaker.getState());
    }

    @Override
    public void onDisable() {
        getLogger().info("MDN-Core Paper shutting down...");

        if (heartbeatTaskId >= 0) {
            getServer().getScheduler().cancelTask(heartbeatTaskId);
        }

        if (deadLetterQueue != null) deadLetterQueue.shutdown();
        if (dataSyncEngine != null) dataSyncEngine.shutdown();
        if (playerCache != null) playerCache.shutdown();
        // Shut down Redis BEFORE MDNAPI — RedisManager owns the subscriber threads
        // and must clean them up before the pool is closed by MDNAPI
        if (redisManager != null) redisManager.shutdown();
        MDNAPI.shutdown();
        if (databaseManager != null) databaseManager.shutdown();

        getLogger().info("MDN-Core Paper disabled. Goodbye!");
    }

    // ── Config Validation ──

    /**
     * Validates that all required config fields are present and have sensible values.
     * Returns false if critical configuration is missing.
     */
    private boolean validateConfiguration() {
        boolean valid = true;

        // Database
        String dbHost = getConfig().getString("database.host");
        if (dbHost == null || dbHost.isBlank()) {
            getLogger().severe("CONFIG ERROR: database.host is not set!");
            valid = false;
        }
        String dbName = getConfig().getString("database.database");
        if (dbName == null || dbName.isBlank()) {
            getLogger().severe("CONFIG ERROR: database.database is not set!");
            valid = false;
        }

        // Redis
        String redisHost = getConfig().getString("redis.host");
        if (redisHost == null || redisHost.isBlank()) {
            getLogger().severe("CONFIG ERROR: redis.host is not set!");
            valid = false;
        }

        return valid;
    }

    /**
     * Runs connectivity tests against MySQL and Redis on startup.
     * Logs warnings but doesn't prevent startup (allows degraded mode).
     */
    private boolean performHealthChecks() {
        boolean allOk = true;

        // MySQL
        if (databaseManager.isConnected()) {
            getLogger().info("MySQL health check: PASSED");
        } else {
            getLogger().warning("MySQL health check: FAILED — running in degraded mode");
            allOk = false;
        }

        // Redis
        if (redisManager.isConnected()) {
            getLogger().info("Redis health check: PASSED");
        } else {
            getLogger().warning("Redis health check: FAILED — running in degraded mode");
            allOk = false;
        }

        return allOk;
    }

    // ── Initialization helpers ──

    private void initDatabase() {
        getLogger().info("Connecting to MySQL...");
        databaseManager = new DatabaseManager(
                getConfig().getString("database.host", "127.0.0.1"),
                getConfig().getInt("database.port", 3306),
                getConfig().getString("database.database", "minedrop"),
                getConfig().getString("database.username", "mdn_user"),
                getConfig().getString("database.password", ""),
                getConfig().getInt("database.pool-settings.maximum-pool-size", 10),
                getConfig().getInt("database.pool-settings.minimum-idle", 2),
                getConfig().getLong("database.pool-settings.connection-timeout-ms", 30000),
                getConfig().getLong("database.pool-settings.idle-timeout-ms", 600000)
        );
        databaseManager.initializeSchema();
    }

    private void initRedis() {
        getLogger().info("Connecting to Redis...");
        redisManager = new RedisManager(
                getConfig().getString("redis.host", "127.0.0.1"),
                getConfig().getInt("redis.port", 6379),
                getConfig().getString("redis.password", ""),
                getConfig().getInt("redis.connection-timeout-ms", 2000),
                getConfig().getString("redis.pubsub-channel", "mdn_packet_bus")
        );
    }

    private void startPeriodicSave() {
        int saveInterval = getConfig().getInt("network.save-interval-seconds", 300);
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> dataSyncEngine.saveAll(),
                saveInterval * 20L, saveInterval * 20L);
    }

    private void startHeartbeat() {
        heartbeatTaskId = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (!MDNAPI.isInitialized()) return;
            var packet = new net.minedrop.api.packet.ServerHeartbeatPacket(
                    getConfig().getString("network.server-group", "paper-server"),
                    getServer().getTPS()[0],
                    getServer().getOnlinePlayers().size(),
                    getServer().getMaxPlayers(),
                    null
            );
            redisCircuitBreaker.executeVoid(() ->
                    redisManager.publishPacket(packet.serialize()));
        }, 100L, 100L).getTaskId();
    }

    // ── Events ──

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        getLogger().info("[corr=" + MDNAPI.getInstance().getInstanceId()
                + "] Player joined: " + player.getName());
        dataSyncEngine.savePlayerProfile(
                player.getUniqueId(), player.getName(),
                player.getAddress() != null
                        ? player.getAddress().getAddress().getHostAddress() : "unknown"
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        playerCache.invalidate(player.getUniqueId());
    }

    // ── Commands ──

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String cmdName = command.getName().toLowerCase();

        if ("mdn".equals(cmdName)) {
            return handleMdnCommand(sender, args);
        }

        if (!getConfig().getBoolean("commands." + cmdName, true)) return true;

        return switch (cmdName) {
            case "hub", "lobby" -> msg(sender, "§7Use /hub or /lobby on the Velocity proxy.");
            case "spawn" -> msg(sender, "§7Use /spawn on the Velocity proxy.");
            case "website" -> msg(sender, "§bWebsite: §fhttps://minedrop.net");
            case "store" -> msg(sender, "§6Store: §fhttps://store.minedrop.net");
            case "vote" -> msg(sender, "§aVote: §fhttps://minedrop.net/vote");
            case "discord" -> msg(sender, "§9Discord: §fhttps://discord.gg/minedrop");
            case "help" -> { showHelp(sender); yield true; }
            case "rules" -> { showRules(sender); yield true; }
            default -> false;
        };
    }

    private boolean msg(CommandSender s, String m) { s.sendMessage(m); return true; }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§e§lMineDrop Network Help");
        sender.sendMessage("§7/mdn status §f- Infrastructure status");
        sender.sendMessage("§7/mdn health §f- Detailed health report");
        sender.sendMessage("§7/website §f- Server website");
        sender.sendMessage("§7/discord §f- Join our Discord");
    }

    private void showRules(CommandSender sender) {
        sender.sendMessage("§c§lServer Rules:");
        sender.sendMessage("§7  1. No cheating or exploiting");
        sender.sendMessage("§7  2. Be respectful to other players");
        sender.sendMessage("§7  3. No inappropriate content");
    }

    private boolean handleMdnCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mdn.admin.core")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§eMDN-Core v" + getDescription().getVersion());
            sender.sendMessage("§7  API: v" + ApiVersion.CURRENT
                    + " | Instance: " + MDNAPI.getInstance().getInstanceId());
            sender.sendMessage("§7/mdn status §f- Infrastructure health");
            sender.sendMessage("§7/mdn health §f- Detailed health report");
            sender.sendMessage("§7/mdn reload §f- Reload config");
            sender.sendMessage("§7/mdn sync <player> §f- Force sync profile");
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "status" -> { showStatus(sender); yield true; }
            case "health" -> { showHealth(sender); yield true; }
            case "reload" -> {
                reloadConfig();
                sender.sendMessage("§aConfiguration reloaded.");
                yield true;
            }
            case "sync" -> {
                if (args.length < 2) { sender.sendMessage("§c/mdn sync <player>"); yield true; }
                Player target = getServer().getPlayer(args[1]);
                if (target != null) {
                    dataSyncEngine.savePlayerProfile(target.getUniqueId(), target.getName(),
                            target.getAddress() != null
                                    ? target.getAddress().getAddress().getHostAddress() : "unknown");
                    sender.sendMessage("§aSynced " + target.getName());
                } else { sender.sendMessage("§cPlayer not found."); }
                yield true;
            }
            default -> msg(sender, "§cUnknown. Use: status, health, reload, sync");
        };
    }

    private void showStatus(CommandSender sender) {
        sender.sendMessage("§a§lInfrastructure Status");
        sender.sendMessage("§7  DB: " + statusIcon(databaseManager.isConnected())
                + " §7[CB: " + dbCircuitBreaker.getState() + "]");
        sender.sendMessage("§7  Redis: " + statusIcon(redisManager.isConnected())
                + " §7[CB: " + redisCircuitBreaker.getState() + "]");
        sender.sendMessage("§7  DLQ: " + deadLetterQueue.getPendingCount()
                + " pending, " + deadLetterQueue.getPermanentCount() + " permanent");
        sender.sendMessage("§7  MDN-API: " + statusIcon(MDNAPI.isInitialized())
                + " §7v" + ApiVersion.CURRENT);
    }

    private void showHealth(CommandSender sender) {
        sender.sendMessage("§a§lHealth Report");
        sender.sendMessage("§7  API: v" + ApiVersion.CURRENT
                + " | Instance: " + MDNAPI.getInstance().getInstanceId());
        sender.sendMessage("§7  DB: " + databaseManager.isConnected()
                + " | Circuit: " + dbCircuitBreaker.getState());
        sender.sendMessage("§7  Redis: " + redisManager.isConnected()
                + " | Circuit: " + redisCircuitBreaker.getState());
        sender.sendMessage("§7  DLQ: " + deadLetterQueue.getPendingCount()
                + " pending / " + deadLetterQueue.getPermanentCount() + " permanent");
        sender.sendMessage("§7  TPS: " + String.format("%.1f", getServer().getTPS()[0]));
        sender.sendMessage("§7  Players: " + getServer().getOnlinePlayers().size()
                + "/" + getServer().getMaxPlayers());
        Runtime rt = Runtime.getRuntime();
        sender.sendMessage("§7  Memory: "
                + ((rt.totalMemory() - rt.freeMemory()) / 1024 / 1024)
                + " MB / " + (rt.maxMemory() / 1024 / 1024) + " MB");
    }

    private static String statusIcon(boolean ok) { return ok ? "§a✓" : "§c✗"; }

    // ── Public getters ──

    public PlayerCache getPlayerCache() { return playerCache; }
    public RedisManager getRedisManager() { return redisManager; }
    public DataSyncEngine getDataSyncEngine() { return dataSyncEngine; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public PacketDispatcher getPacketDispatcher() { return packetDispatcher; }
    public CircuitBreaker getDbCircuitBreaker() { return dbCircuitBreaker; }
    public CircuitBreaker getRedisCircuitBreaker() { return redisCircuitBreaker; }
    public DeadLetterQueue getDeadLetterQueue() { return deadLetterQueue; }
}
