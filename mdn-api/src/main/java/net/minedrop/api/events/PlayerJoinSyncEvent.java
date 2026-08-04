package net.minedrop.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired when a player's data has been loaded on a new server after a transfer.
 * Plugins like MDN-Economy and MDN-Social listen for this to refresh caches.
 */
public class PlayerJoinSyncEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final UUID playerUuid;
    private final String playerName;
    private final String sourceServer;
    private final String targetServer;

    public PlayerJoinSyncEvent(UUID playerUuid, String playerName, String sourceServer, String targetServer) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.sourceServer = sourceServer;
        this.targetServer = targetServer;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public String getSourceServer() { return sourceServer; }
    public String getTargetServer() { return targetServer; }

    @Override public @NotNull HandlerList getHandlers() { return handlers; }
    @SuppressWarnings("unused")
    public static HandlerList getHandlerList() { return handlers; }
}
