package net.minedrop.moderation;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * MDN-Moderation — Central staff tool plugin.
 * <h3>TODO</h3>
 * <ol><li>Punishments — /ban, /mute, /kick, /warn, /unban, /unmute</li>
 * <li>Vanish — /vanish with TAB integration, no pressure plate triggers</li>
 * <li>Staff mode — /staffmode, fly, inventory inspector</li>
 * <li>Freeze — /freeze, auto-ban on disconnect during freeze</li>
 * <li>Screenshare — /ss, teleport to ss_world, checklist</li>
 * <li>Reports system — GUI-based, status tracking</li>
 * <li>Notes & history — /note, /history</li>
 * <li>Clan integration — /clan freeze, /clan chat slowmode</li></ol>
 * @see plan/MineDrop/plugins/09_MDN_Moderation.md
 */
public class ModerationPaperPlugin extends JavaPlugin {
    @Override public void onEnable() { getLogger().info("MDN-Moderation loaded — TODO"); }
    @Override public void onDisable() { getLogger().info("MDN-Moderation disabled."); }
}
