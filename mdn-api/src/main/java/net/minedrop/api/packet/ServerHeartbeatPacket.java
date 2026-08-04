package net.minedrop.api.packet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Sent periodically by every Paper server to report health metrics.
 * Broadcast on channel: {@code mdn:core:bus}
 */
public final class ServerHeartbeatPacket extends MDNPacket {

    private final String server;
    private final double tps;
    private final int players;
    private final int maxPlayers;

    @JsonCreator
    public ServerHeartbeatPacket(
            @JsonProperty("server") String server,
            @JsonProperty("tps") double tps,
            @JsonProperty("players") int players,
            @JsonProperty("maxPlayers") int maxPlayers,
            @JsonProperty("senderId") UUID senderId) {
        super("SERVER_HEARTBEAT", senderId);
        this.server = server;
        this.tps = tps;
        this.players = players;
        this.maxPlayers = maxPlayers;
    }

    public String getServer() { return server; }
    public double getTps() { return tps; }
    public int getPlayers() { return players; }
    public int getMaxPlayers() { return maxPlayers; }

    @Override
    public String toString() {
        return "ServerHeartbeatPacket{server='" + server + "', tps=" + tps + ", players=" + players + "/" + maxPlayers + "}";
    }
}
