package net.minedrop.core.registry;

import net.minedrop.core.MDNCore;
import net.minedrop.core.redis.RedisManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Tracks all registered game servers, their status, load, and capacity.
 * <p>
 * Servers heartbeat periodically via Redis. The registry evicts servers
 * that haven't heartbeated within the timeout window (default 45 seconds).
 */
public final class ServerRegistry {

    private static final Logger log = LoggerFactory.getLogger(ServerRegistry.class);

    /** Maximum seconds without a heartbeat before a server is evicted. */
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 45;

    private final RedisManager redisManager;
    private final Map<String, ServerInfo> servers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupScheduler
            = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mdn-server-cleanup");
        t.setDaemon(true);
        return t;
    });

    public ServerRegistry(RedisManager redisManager) {
        this.redisManager = redisManager;

        // Periodic heartbeat timeout cleanup — every 15 seconds
        cleanupScheduler.scheduleAtFixedRate(this::evictDeadServers,
                15, 15, TimeUnit.SECONDS);
    }

    /**
     * Registers or updates a server in the registry.
     * Automatically updates the lastHeartbeat timestamp.
     */
    public void registerServer(String serverName, ServerInfo info) {
        info.setLastHeartbeat(System.currentTimeMillis());
        info.setOnline(true);
        servers.put(serverName, info);
        log.debug("Server heartbeat: {} (players: {}/{}, tps: {})",
                serverName, info.getPlayerCount(), info.getMaxPlayers(), info.getTps());
    }

    /**
     * Removes a server from the registry (graceful shutdown).
     */
    public void unregisterServer(String serverName) {
        servers.remove(serverName);
        log.info("Server removed from registry: {}", serverName);
    }

    /**
     * Evicts servers that haven't heartbeated within the timeout window.
     */
    private void evictDeadServers() {
        long cutoff = System.currentTimeMillis() - (HEARTBEAT_TIMEOUT_SECONDS * 1000);
        var deadServers = servers.values().stream()
                .filter(s -> s.getLastHeartbeat() < cutoff)
                .toList();

        for (var server : deadServers) {
            servers.remove(server.getName());
            log.warn("Server EVICTED (no heartbeat for {}s): {}",
                    HEARTBEAT_TIMEOUT_SECONDS, server.getName());
        }
    }

    // ── Routing methods with health scoring ──

    /**
     * Returns the best available lobby server for a player.
     * Uses health scoring: lower player count + higher TPS = better.
     */
    public Optional<ServerInfo> findBestLobby(String region) {
        return servers.values().stream()
                .filter(s -> "lobby".equals(s.getServerGroup()))
                .filter(s -> s.isOnline() && s.isHealthy() && s.getPlayerCount() < s.getMaxPlayers())
                .filter(s -> region == null || region.equalsIgnoreCase(s.getRegion()))
                .min(Comparator.comparingDouble(ServerInfo::getHealthScore));
    }

    /**
     * Returns the best public SAM game server.
     */
    public Optional<ServerInfo> findBestPublicGame() {
        return servers.values().stream()
                .filter(s -> "sam-public".equals(s.getServerGroup()))
                .filter(s -> s.isOnline() && s.isHealthy() && s.getPlayerCount() < s.getMaxPlayers())
                .min(Comparator.comparingDouble(ServerInfo::getHealthScore));
    }

    /**
     * Finds a clan-specific private server by clan name.
     */
    public Optional<ServerInfo> findClanServer(String clanName) {
        return servers.values().stream()
                .filter(s -> ("sam-clan-" + clanName).equals(s.getServerGroup()))
                .filter(s -> s.isOnline() && s.isHealthy())
                .findFirst();
    }

    public List<ServerInfo> getAllServers() {
        return List.copyOf(servers.values());
    }

    public int getOnlineServerCount() {
        return (int) servers.values().stream().filter(ServerInfo::isOnline).count();
    }

    public void shutdown() {
        cleanupScheduler.shutdown();
        servers.clear();
    }

    /**
     * Represents a single registered game server.
     */
    public static class ServerInfo {
        private String name;
        private String serverGroup;
        private String region;
        private double tps = 20.0;
        private int playerCount;
        private int maxPlayers;
        private boolean online;
        private long lastHeartbeat;

        public ServerInfo() {}

        public ServerInfo(String name, String serverGroup, String region, int maxPlayers) {
            this.name = name;
            this.serverGroup = serverGroup;
            this.region = region;
            this.maxPlayers = maxPlayers;
            this.online = true;
            this.lastHeartbeat = System.currentTimeMillis();
        }

        /**
         * Returns true if TPS is above the playable threshold.
         */
        public boolean isHealthy() {
            return tps >= 17.0;
        }

        /**
         * Composite health score for routing decisions.
         * Lower is better: factors in TPS (inverted) + player load + time since last heartbeat.
         */
        public double getHealthScore() {
            double tpsScore = Math.max(0, 20.0 - tps);       // 0-3 range
            double loadScore = (double) playerCount / Math.max(1, maxPlayers) * 10; // 0-10 range
            double stalenessScore = (System.currentTimeMillis() - lastHeartbeat) / 1000.0 * 0.01; // tiny factor
            return tpsScore + loadScore + stalenessScore;
        }

        // ── Getters & Setters ──

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getServerGroup() { return serverGroup; }
        public void setServerGroup(String serverGroup) { this.serverGroup = serverGroup; }

        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }

        public double getTps() { return tps; }
        public void setTps(double tps) { this.tps = tps; }

        public int getPlayerCount() { return playerCount; }
        public void setPlayerCount(int playerCount) { this.playerCount = playerCount; }

        public int getMaxPlayers() { return maxPlayers; }
        public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

        public boolean isOnline() { return online; }
        public void setOnline(boolean online) { this.online = online; }

        public long getLastHeartbeat() { return lastHeartbeat; }
        public void setLastHeartbeat(long lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    }
}
