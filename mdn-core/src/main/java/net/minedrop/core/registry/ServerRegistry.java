package net.minedrop.core.registry;

import net.minedrop.core.MDNCore;
import net.minedrop.core.redis.RedisManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks all registered game servers, their status, load, and capacity.
 * <p>
 * Servers heartbeat periodically via Redis. The registry maintains the list
 * and provides routing decisions based on server load and region.
 */
public final class ServerRegistry {

    private static final Logger log = LoggerFactory.getLogger(ServerRegistry.class);

    private final RedisManager redisManager;
    private final Map<String, ServerInfo> servers = new ConcurrentHashMap<>();

    public ServerRegistry(RedisManager redisManager) {
        this.redisManager = redisManager;
    }

    /**
     * Registers or updates a server in the registry.
     */
    public void registerServer(String serverName, ServerInfo info) {
        servers.put(serverName, info);
        log.debug("Server registered: {} (players: {}/{}, tps: {})",
                serverName, info.getPlayerCount(), info.getMaxPlayers(), info.getTps());
    }

    /**
     * Removes a server from the registry (on shutdown or timeout).
     */
    public void unregisterServer(String serverName) {
        servers.remove(serverName);
        log.info("Server removed from registry: {}", serverName);
    }

    /**
     * Returns the best available lobby server for a player.
     * Prioritises servers with lower player counts in the player's region.
     */
    public Optional<ServerInfo> findBestLobby(String region) {
        return servers.values().stream()
                .filter(s -> "lobby".equals(s.getServerGroup()))
                .filter(s -> s.isOnline() && s.getPlayerCount() < s.getMaxPlayers())
                .filter(s -> region == null || region.equalsIgnoreCase(s.getRegion()))
                .min(Comparator.comparingInt(ServerInfo::getPlayerCount));
    }

    /**
     * Returns the best public SAM game server.
     */
    public Optional<ServerInfo> findBestPublicGame() {
        return servers.values().stream()
                .filter(s -> "sam-public".equals(s.getServerGroup()))
                .filter(s -> s.isOnline() && s.getPlayerCount() < s.getMaxPlayers())
                .min(Comparator.comparingInt(ServerInfo::getPlayerCount));
    }

    /**
     * Finds a clan-specific private server by clan name.
     */
    public Optional<ServerInfo> findClanServer(String clanName) {
        return servers.values().stream()
                .filter(s -> ("sam-clan-" + clanName).equals(s.getServerGroup()))
                .filter(ServerInfo::isOnline)
                .findFirst();
    }

    public List<ServerInfo> getAllServers() {
        return List.copyOf(servers.values());
    }

    public int getOnlineServerCount() {
        return (int) servers.values().stream().filter(ServerInfo::isOnline).count();
    }

    /**
     * Represents a single registered game server.
     */
    public static class ServerInfo {
        private String name;
        private String serverGroup;  // lobby, sam-public, sam-clan-<name>
        private String region;       // EU, NA, ASIA
        private double tps;
        private int playerCount;
        private int maxPlayers;
        private boolean online;
        private long lastHeartbeat;

        public ServerInfo() {}

        public ServerInfo(String name, String serverGroup, String region,
                          int maxPlayers) {
            this.name = name;
            this.serverGroup = serverGroup;
            this.region = region;
            this.maxPlayers = maxPlayers;
            this.online = true;
            this.lastHeartbeat = System.currentTimeMillis();
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
