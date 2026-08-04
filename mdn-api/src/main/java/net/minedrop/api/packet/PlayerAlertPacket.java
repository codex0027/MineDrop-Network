package net.minedrop.api.packet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Sends a notification popup or action bar message to a player.
 * Broadcast on channel: {@code mdn_alerts}
 */
public final class PlayerAlertPacket extends MDNPacket {

    public enum AlertType { ALERT, ALERT_ERROR }

    private final UUID uuid;
    private final String message;
    private final AlertType type;

    @JsonCreator
    public PlayerAlertPacket(
            @JsonProperty("uuid") UUID uuid,
            @JsonProperty("message") String message,
            @JsonProperty("type") AlertType type,
            @JsonProperty("senderId") UUID senderId) {
        super("PLAYER_ALERT", senderId);
        this.uuid = uuid;
        this.message = message;
        this.type = type;
    }

    public UUID getUuid() { return uuid; }
    public String getMessage() { return message; }
    public AlertType getType() { return type; }
}
