package net.minedrop.api.packet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Signals that a player is being transferred between servers.
 * The target server uses this to pre-load player data before the player arrives.
 * Broadcast on channel: {@code mdn:core:bus}
 */
public final class PlayerSwitchServerPacket extends MDNPacket {

    private final UUID uuid;
    private final String target;
    private final boolean force;

    @JsonCreator
    public PlayerSwitchServerPacket(
            @JsonProperty("uuid") UUID uuid,
            @JsonProperty("target") String target,
            @JsonProperty("force") boolean force,
            @JsonProperty("senderId") UUID senderId) {
        super("PLAYER_SWITCH_SERVER", senderId);
        this.uuid = uuid;
        this.target = target;
        this.force = force;
    }

    public UUID getUuid() { return uuid; }
    public String getTarget() { return target; }
    public boolean isForce() { return force; }

    @Override
    public String toString() {
        return "PlayerSwitchServerPacket{uuid=" + uuid + ", target='" + target + "', force=" + force + "}";
    }
}
