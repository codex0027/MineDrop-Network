package net.minedrop.api.packet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Signals that a player completed 2FA or device verification.
 * Broadcast on channel: {@code mdn_auth}
 */
public final class AuthUpdatePacket extends MDNPacket {

    private final UUID uuid;
    private final boolean status;

    @JsonCreator
    public AuthUpdatePacket(
            @JsonProperty("uuid") UUID uuid,
            @JsonProperty("status") boolean status,
            @JsonProperty("senderId") UUID senderId) {
        super("AUTH_UPDATE", senderId);
        this.uuid = uuid;
        this.status = status;
    }

    public UUID getUuid() { return uuid; }
    public boolean isStatus() { return status; }
}
