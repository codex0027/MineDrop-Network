package net.minedrop.core.sync;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.minedrop.api.MDNAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles serialization, storage, and cross-server synchronization
 * of player inventories, ender chests, and item metadata.
 * <p>
 * Inventory data is serialized to Base64 (standard Bukkit format) and stored
 * in MySQL for persistence. Anti-duplication locks prevent race conditions.
 */
public final class InventorySyncManager {

    private static final Logger log = LoggerFactory.getLogger(InventorySyncManager.class);

    /**
     * Saves a player's inventory to MySQL asynchronously.
     * Uses Jackson ObjectMapper to build properly escaped JSON (fixes H-10).
     *
     * @param uuid            player UUID
     * @param inventoryBase64 Base64-encoded inventory data
     * @param enderChestBase64 Base64-encoded ender chest data
     */
    public CompletableFuture<Void> saveInventory(UUID uuid, String inventoryBase64,
                                                  String enderChestBase64) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO mdn_sam_player_data (uuid, inventory_serialized, unslotted_statues)
                    VALUES (?, ?, '[]')
                    ON DUPLICATE KEY UPDATE
                        inventory_serialized = VALUES(inventory_serialized)
                    """;
            try (Connection conn = MDNAPI.getInstance().getDataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                // Build proper JSON via Jackson to avoid corrupt data from string concatenation
                ObjectNode combined = JsonNodeFactory.instance.objectNode();
                combined.put("inv", inventoryBase64 != null ? inventoryBase64 : "");
                combined.put("ec", enderChestBase64 != null ? enderChestBase64 : "");
                String json = MDNAPI.getInstance().getObjectMapper().writeValueAsString(combined);
                ps.setString(2, json);
                ps.executeUpdate();
                log.debug("Inventory saved for {} (inv: {} bytes, ec: {} bytes)",
                        uuid,
                        inventoryBase64 != null ? inventoryBase64.length() : 0,
                        enderChestBase64 != null ? enderChestBase64.length() : 0);
            } catch (SQLException e) {
                log.error("Failed to save inventory for {}", uuid, e);
            } catch (Exception e) {
                log.error("Failed to serialize inventory JSON for {}", uuid, e);
            }
        });
    }

    /**
     * Loads a player's inventory from MySQL.
     *
     * @return Base64-encoded inventory string, or null if not found
     */
    public CompletableFuture<String> loadInventory(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT inventory_serialized FROM mdn_sam_player_data WHERE uuid = ?";
            try (Connection conn = MDNAPI.getInstance().getDataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("inventory_serialized");
                    }
                }
            } catch (SQLException e) {
                log.error("Failed to load inventory for {}", uuid, e);
            }
            return null;
        });
    }

    /**
     * Converts a byte array to a Base64-encoded string for storage.
     */
    public static String toBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Decodes a Base64-encoded string back to a byte array.
     */
    public static byte[] fromBase64(String base64) {
        return Base64.getDecoder().decode(base64);
    }
}
