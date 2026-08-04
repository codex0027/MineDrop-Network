package net.minedrop.core.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.minedrop.api.MDNAPI;
import net.minedrop.core.MDNCore;
import net.minedrop.core.redis.RedisManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Handles asynchronous cross-server data synchronization.
 * <p>
 * Manages player profile saves, state locking to prevent race conditions,
 * and crash recovery buffer files for emergency data preservation.
 */
public final class DataSyncEngine {

    private static final Logger log = LoggerFactory.getLogger(DataSyncEngine.class);

    /** Directory for emergency crash recovery dumps. */
    private static final Path CRASH_BUFFER_DIR = Path.of("plugins", "MDN-Core", "emergencies");

    private final RedisManager redisManager;
    private final InventorySyncManager inventorySyncManager;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ObjectMapper objectMapper;

    /** Registry of active session data for bulk saves. */
    private final Map<UUID, PendingSave> pendingSaves = new ConcurrentHashMap<>();

    public DataSyncEngine(RedisManager redisManager, InventorySyncManager inventorySyncManager) {
        this.redisManager = redisManager;
        this.inventorySyncManager = inventorySyncManager;
        this.objectMapper = MDNAPI.getInstance().getObjectMapper();

        // Ensure crash buffer directory exists
        try {
            Files.createDirectories(CRASH_BUFFER_DIR);
        } catch (IOException e) {
            log.error("Failed to create crash buffer directory", e);
        }
    }

    /**
     * Locks a player's data for a server transfer to prevent duplication.
     */
    public void lockPlayer(UUID uuid) {
        redisManager.setWithExpiry(MDNCore.KEY_PLAYER_LOCK + uuid, "locked", 30);
        log.debug("Player data locked: {}", uuid);
    }

    /**
     * Releases the player lock after the transfer is complete.
     */
    public void unlockPlayer(UUID uuid) {
        redisManager.delete(MDNCore.KEY_PLAYER_LOCK + uuid);
        log.debug("Player data unlocked: {}", uuid);
    }

    /**
     * Checks if a player's data is currently locked by another server.
     */
    public boolean isPlayerLocked(UUID uuid) {
        return redisManager.get(MDNCore.KEY_PLAYER_LOCK + uuid) != null;
    }

    /**
     * Saves player profile data to MySQL asynchronously.
     */
    public CompletableFuture<Boolean> savePlayerProfile(UUID uuid, String username, String ipAddress) {
        pendingSaves.put(uuid, new PendingSave(uuid, username, ipAddress));
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                    INSERT INTO mdn_player_profiles (uuid, username, ip_address, last_join)
                    VALUES (?, ?, ?, NOW())
                    ON DUPLICATE KEY UPDATE
                        username = VALUES(username),
                        ip_address = VALUES(ip_address),
                        last_join = NOW()
                    """;
            try (Connection conn = MDNAPI.getInstance().getDataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, username);
                ps.setString(3, ipAddress);
                ps.executeUpdate();
                pendingSaves.remove(uuid);
                return true;
            } catch (SQLException e) {
                log.error("Failed to save player profile for {}", uuid, e);
                dumpToCrashBuffer(uuid, username, ipAddress);
                return false;
            }
        });
    }

    /**
     * Performs a full save of all online player data.
     * Called periodically and before restarts.
     */
    public void saveAll() {
        if (pendingSaves.isEmpty()) {
            log.debug("saveAll: No pending saves to flush.");
            return;
        }

        log.info("Flushing {} pending player saves...", pendingSaves.size());
        var snapshot = Map.copyOf(pendingSaves);

        for (var entry : snapshot.entrySet()) {
            UUID uuid = entry.getKey();
            PendingSave save = entry.getValue();
            try {
                savePlayerProfile(uuid, save.username(), save.ipAddress()).get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failed to flush save for {} during saveAll", uuid, e);
                dumpToCrashBuffer(uuid, save.username(), save.ipAddress());
            }
        }

        log.info("saveAll complete. {} profiles flushed.", snapshot.size());
    }

    /**
     * Emergency dump: writes player data to a local JSON file when MySQL is unavailable.
     * These files are loaded back on the player's next login.
     */
    private void dumpToCrashBuffer(UUID uuid, String username, String ipAddress) {
        try {
            Path crashFile = CRASH_BUFFER_DIR.resolve("profile_" + uuid + ".json");
            String json = objectMapper.writeValueAsString(Map.of(
                    "uuid", uuid.toString(),
                    "username", username,
                    "ip_address", ipAddress,
                    "dumped_at", System.currentTimeMillis()
            ));
            Files.writeString(crashFile, json);
            log.warn("Emergency dump written: {}", crashFile);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to write crash buffer for {}!", uuid, e);
        }
    }

    public InventorySyncManager getInventorySyncManager() {
        return inventorySyncManager;
    }

    public void shutdown() {
        // Final flush before shutdown
        saveAll();
        scheduler.shutdown();
        log.info("DataSyncEngine shut down.");
    }

    /** Lightweight record for pending saves. */
    private record PendingSave(UUID uuid, String username, String ipAddress) {}
}
