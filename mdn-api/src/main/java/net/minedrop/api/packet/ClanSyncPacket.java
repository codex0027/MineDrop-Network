package net.minedrop.api.packet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Updates active clan rosters or stats.
 * Broadcast on channel: {@code mdn_clans}
 */
public final class ClanSyncPacket extends MDNPacket {

    public enum ClanAction { DISBAND, JOIN, LEAVE, LEVEL_UP }

    private final UUID clanId;
    private final ClanAction action;
    private final UUID player;

    @JsonCreator
    public ClanSyncPacket(
            @JsonProperty("clanId") UUID clanId,
            @JsonProperty("action") ClanAction action,
            @JsonProperty("player") UUID player,
            @JsonProperty("senderId") UUID senderId) {
        super("CLAN_SYNC", senderId);
        this.clanId = clanId;
        this.action = action;
        this.player = player;
    }

    public UUID getClanId() { return clanId; }
    public ClanAction getAction() { return action; }
    public UUID getPlayer() { return player; }
}
