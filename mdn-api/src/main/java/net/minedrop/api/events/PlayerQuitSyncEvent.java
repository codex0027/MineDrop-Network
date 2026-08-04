package net.minedrop.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired when a player disconnects and their data is being flushed.
 * Plugins should release any resources tied to the player.
 */
public class PlayerQuitSyncEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final UUID playerUuid;
    private final String playerName;
    private final String lastServer;

    public PlayerQuitSyncEvent(UUID playerUuid, String playerName, String lastServer) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.lastServer = lastServer;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public String getLastServer() { return lastServer; }

    @Override public @NotNull HandlerList getHandlers() { return handlers; }
    @SuppressWarnings("unused")
    public static HandlerList getHandlerList() { return handlers; }
}
