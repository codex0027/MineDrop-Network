package net.minedrop.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired when a player's inventory has been synced across servers.
 */
public class InventorySyncEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final UUID playerUuid;
    private final String playerName;
    private final boolean success;

    public InventorySyncEvent(UUID playerUuid, String playerName, boolean success) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.success = success;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public boolean isSuccess() { return success; }

    @Override public @NotNull HandlerList getHandlers() { return handlers; }
    @SuppressWarnings("unused")
    public static HandlerList getHandlerList() { return handlers; }
}
