package net.minedrop.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired when a player successfully steals a statue from an enemy base
 * and places it into their own base slot.
 */
public class StatueStealEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final Player thief;
    private final UUID victimUuid;
    private final String statueRarity;
    private final double value;

    public StatueStealEvent(Player thief, UUID victimUuid, String statueRarity, double value) {
        this.thief = thief;
        this.victimUuid = victimUuid;
        this.statueRarity = statueRarity;
        this.value = value;
    }

    public Player getThief() { return thief; }
    public UUID getVictimUuid() { return victimUuid; }
    public String getStatueRarity() { return statueRarity; }
    public double getValue() { return value; }

    @Override
    public @NotNull HandlerList getHandlers() { return handlers; }

    @SuppressWarnings("unused")
    public static HandlerList getHandlerList() { return handlers; }
}
