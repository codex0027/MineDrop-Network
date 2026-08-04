package net.minedrop.api.packet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Informs servers to update a player's cached coin balance.
 * Broadcast on channel: {@code mdn_economy}
 */
public final class EconomySyncPacket extends MDNPacket {

    private final UUID uuid;
    private final double newBalance;

    @JsonCreator
    public EconomySyncPacket(
            @JsonProperty("uuid") UUID uuid,
            @JsonProperty("newBalance") double newBalance,
            @JsonProperty("senderId") UUID senderId) {
        super("ECONOMY_SYNC", senderId);
        this.uuid = uuid;
        this.newBalance = newBalance;
    }

    public UUID getUuid() { return uuid; }
    public double getNewBalance() { return newBalance; }

    @Override
    public String toString() {
        return "EconomySyncPacket{uuid=" + uuid + ", newBalance=" + newBalance + "}";
    }
}
