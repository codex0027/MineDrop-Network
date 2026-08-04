package net.minedrop.core.paper;

import net.minedrop.api.MDNAPI;
import net.minedrop.core.cache.PlayerCache;
import net.minedrop.core.database.DatabaseManager;
import net.minedrop.core.redis.RedisManager;
import net.minedrop.core.sync.DataSyncEngine;
import net.minedrop.core.sync.InventorySyncManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Paper-side entry point for MDN-Core.
 * <p>
 * Initializes the database, Redis, player cache, and data sync engine.
 * All Paper plugins on the network connect through this infrastructure.
 */
public final class CorePaperPlugin extends JavaPlugin implements Listener {

    private DatabaseManager databaseManager;
    private RedisManager redisManager;
    private PlayerCache playerCache;
    private DataSyncEngine dataSyncEngine;
    private InventorySyncManager inventorySyncManager;

    @Override
    public void onLoad() {
        getLogger().info("MDN-Core Paper loading...");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        // ── Step 1: Initialize Database ──
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

        // Create tables
        databaseManager.initializeSchema();

        // ── Step 2: Initialize Redis ──
        getLogger().info("Connecting to Redis...");
        redisManager = new RedisManager(
                getConfig().getString("redis.host", "127.0.0.1"),
                getConfig().getInt("redis.port", 6379),
                getConfig().getString("redis.password", ""),
                getConfig().getInt("redis.connection-timeout-ms", 2000),
                getConfig().getString("redis.pubsub-channel", "mdn_packet_bus")
        );

        // ── Step 3: Initialize MDN-API ──
        MDNAPI.initialize(databaseManager.getDataSource(), redisManager.getJedisPool());

        // ── Step 4: Initialize PlayerCache ──
        playerCache = new PlayerCache(redisManager);

        // ── Step 5: Initialize Sync Engine ──
        inventorySyncManager = new InventorySyncManager();
        dataSyncEngine = new DataSyncEngine(redisManager, inventorySyncManager);

        // ── Step 6: Start periodic save task ──
        int saveInterval = getConfig().getInt("network.save-interval-seconds", 300);
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> dataSyncEngine.saveAll(),
                saveInterval * 20L, saveInterval * 20L);

        // ── Step 7: Register events ──
        getServer().getPluginManager().registerEvents(this, this);

        // ── Step 8: Subscribe to Redis packet bus ──
        redisManager.subscribe("mdn_packet_bus", message -> {
            // Relay incoming packets to the appropriate handler
            getLogger().fine("Received packet: " + message.substring(0, Math.min(100, message.length())));
        });

        getLogger().info("MDN-Core Paper enabled. All infrastructure ready.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MDN-Core Paper disabling...");
        if (dataSyncEngine != null) dataSyncEngine.shutdown();
        if (redisManager != null) redisManager.shutdown();
        if (databaseManager != null) databaseManager.shutdown();
        getLogger().info("MDN-Core Paper disabled. Goodbye!");
    }

    // ── Events ──

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        dataSyncEngine.savePlayerProfile(
                player.getUniqueId(),
                player.getName(),
                player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown"
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
        if (command.getName().equalsIgnoreCase("mdn")) {
            if (!sender.hasPermission("mdn.admin.core")) {
                sender.sendMessage("§cYou don't have permission for this command.");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage("§eMDN-Core v" + getDescription().getVersion());
                sender.sendMessage("§7/mdn reload §f- Reload config");
                sender.sendMessage("§7/mdn status §f- Show infrastructure status");
                sender.sendMessage("§7/mdn sync <player> §f- Force sync player data");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "reload":
                    reloadConfig();
                    sender.sendMessage("§aMDN-Core configuration reloaded.");
                    break;
                case "status":
                    sender.sendMessage("§aInfrastructure Status:");
                    sender.sendMessage("§7 Database: " + (databaseManager.isConnected() ? "§a✓ Connected" : "§c✗ Disconnected"));
                    sender.sendMessage("§7 Redis: " + (redisManager.isConnected() ? "§a✓ Connected" : "§c✗ Disconnected"));
                    break;
                case "sync":
                    if (args.length < 2) {
                        sender.sendMessage("§cUsage: /mdn sync <player>");
                        return true;
                    }
                    Player target = getServer().getPlayer(args[1]);
                    if (target != null) {
                        dataSyncEngine.savePlayerProfile(
                                target.getUniqueId(), target.getName(),
                                target.getAddress() != null ? target.getAddress().getAddress().getHostAddress() : "unknown"
                        );
                        sender.sendMessage("§aPlayer data synced for " + target.getName());
                    } else {
                        sender.sendMessage("§cPlayer not found.");
                    }
                    break;
                default:
                    sender.sendMessage("§cUnknown subcommand. Use: reload, status, sync");
            }
            return true;
        }

        // /hub — redirect to lobby (when running without Velocity)
        if (command.getName().equalsIgnoreCase("hub")) {
            if (!getConfig().getBoolean("commands.hub", true)) return true;
            sender.sendMessage("§7To return to the lobby, use the Velocity proxy.");
            return true;
        }

        return false;
    }

    // ── Public getters for other plugins ──

    public PlayerCache getPlayerCache() { return playerCache; }
    public RedisManager getRedisManager() { return redisManager; }
    public DataSyncEngine getDataSyncEngine() { return dataSyncEngine; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
}
