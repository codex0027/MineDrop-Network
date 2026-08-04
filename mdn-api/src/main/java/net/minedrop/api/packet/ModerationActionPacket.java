package net.minedrop.api.packet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Executes mute/ban/kick actions across the network instantly.
 * Broadcast on channel: {@code mdn_moderation}
 */
public final class ModerationActionPacket extends MDNPacket {

    public enum ActionType { BAN, MUTE, KICK }

    private final UUID target;
    private final ActionType type;
    private final long expiry; // epoch millis, -1 = permanent

    @JsonCreator
    public ModerationActionPacket(
            @JsonProperty("target") UUID target,
            @JsonProperty("type") ActionType type,
            @JsonProperty("expiry") long expiry,
            @JsonProperty("senderId") UUID senderId) {
        super("MODERATION_ACTION", senderId);
        this.target = target;
        this.type = type;
        this.expiry = expiry;
    }

    public UUID getTarget() { return target; }
    public ActionType getType() { return type; }
    public long getExpiry() { return expiry; }
}
