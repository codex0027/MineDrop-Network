package net.minedrop.maintenance;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * MDN-Maintenance — Paper side.
 * <h3>TODO</h3>
 * <ol><li>Maintenance mode — /maintenance on/off</li><li>Scheduled restarts — /networkrestart</li>
 * <li>Game state freeze on restart — lock conveyor, pause destroyers</li>
 * <li>Safe player routing to hub before shutdown</li>
 * <li>Orphaned server detection — auto-lock if proxy heartbeat lost</li></ol>
 * @see plan/MineDrop/plugins/06_MDN_Maintenance.md
 */
public class MaintenancePaperPlugin extends JavaPlugin {
    @Override public void onEnable() { getLogger().info("MDN-Maintenance (Paper) loaded — TODO"); }
}
