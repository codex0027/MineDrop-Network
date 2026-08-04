package net.minedrop.communication;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * MDN-Communication — Paper side.
 * <h3>TODO</h3>
 * <ol><li>Global chat sync via Redis</li><li>Private messages /msg</li>
 * <li>Staff chat /sc, Clan chat /cc</li><li>Anti-spam, caps filter, blocked words</li>
 * <li>Slowmode /chat slowmode</li></ol>
 * @see plan/MineDrop/plugins/05_MDN_Communication.md
 */
public class CommunicationPaperPlugin extends JavaPlugin {
    @Override public void onEnable() { getLogger().info("MDN-Communication (Paper) loaded — TODO"); }
    @Override public void onDisable() { getLogger().info("MDN-Communication (Paper) disabled."); }
}
