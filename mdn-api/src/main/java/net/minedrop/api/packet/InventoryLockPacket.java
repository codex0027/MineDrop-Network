package net.minedrop.api.packet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Acquires or releases the inventory lock for a player during server transfers.
 * Prevents inventory duplication across server boundaries.
 * Broadcast on channel: {@code mdn:core:bus}
 */
public final class InventoryLockPacket extends MDNPacket {

    private final UUID uuid;
    private final boolean locked;
    private final long timestamp;

    @JsonCreator
    public InventoryLockPacket(
            @JsonProperty("uuid") UUID uuid,
            @JsonProperty("locked") boolean locked,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("senderId") UUID senderId) {
        super("INVENTORY_LOCK", senderId);
        this.uuid = uuid;
        this.locked = locked;
        this.timestamp = timestamp;
    }

    public UUID getUuid() { return uuid; }
    public boolean isLocked() { return locked; }
    public long getTimestamp() { return timestamp; }
}
