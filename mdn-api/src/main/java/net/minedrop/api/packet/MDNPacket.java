package net.minedrop.api.packet;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import net.minedrop.api.MDNAPI;

import java.util.UUID;

/**
 * Base class for all MineDrop network packets transmitted via Redis Pub/Sub.
 * <p>
 * Uses Jackson polymorphic deserialization — the {@code packetType} field
 * determines which subclass to instantiate when deserializing from JSON.
 * <p>
 * Subclasses define specific payload fields. All packets are serialized to JSON
 * and broadcast on the {@code mdn_packet_bus} Redis channel.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "packetType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AuthUpdatePacket.class, name = "AUTH_UPDATE"),
        @JsonSubTypes.Type(value = PlayerAlertPacket.class, name = "PLAYER_ALERT"),
        @JsonSubTypes.Type(value = EconomySyncPacket.class, name = "ECONOMY_SYNC"),
        @JsonSubTypes.Type(value = ModerationActionPacket.class, name = "MODERATION_ACTION"),
        @JsonSubTypes.Type(value = ClanSyncPacket.class, name = "CLAN_SYNC"),
        @JsonSubTypes.Type(value = ServerHeartbeatPacket.class, name = "SERVER_HEARTBEAT"),
        @JsonSubTypes.Type(value = PlayerSwitchServerPacket.class, name = "PLAYER_SWITCH_SERVER"),
        @JsonSubTypes.Type(value = InventoryLockPacket.class, name = "INVENTORY_LOCK"),
})
public abstract class MDNPacket {

    private final String packetType;
    private final UUID senderId;
    private final long timestamp;
    private String correlationId;

    protected MDNPacket(String packetType, UUID senderId) {
        this.packetType = packetType;
        this.senderId = senderId;
        this.timestamp = System.currentTimeMillis();
        this.correlationId = MDNAPI.isInitialized()
                ? MDNAPI.getInstance().getInstanceId() + "-" + timestamp
                : "uninit-" + timestamp;
    }

    public String getPacketType() { return packetType; }
    public UUID getSenderId() { return senderId; }
    public long getTimestamp() { return timestamp; }

    /**
     * Correlation ID for distributed tracing across servers.
     * Generated per-packet to track a request's journey.
     */
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    /**
     * Serializes this packet to a JSON string for Redis publishing.
     */
    public String serialize() {
        try {
            return MDNAPI.getInstance().getObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize packet: " + packetType, e);
        }
    }

    /**
     * Deserializes a JSON string back into the appropriate packet subclass.
     * Uses Jackson's polymorphic deserialization based on the {@code packetType} field.
     *
     * @param json the JSON string from Redis
     * @return the deserialized packet (correct subclass)
     */
    public static MDNPacket deserialize(String json) {
        try {
            return MDNAPI.getInstance().getObjectMapper().readValue(json, MDNPacket.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize packet", e);
        }
    }
}
