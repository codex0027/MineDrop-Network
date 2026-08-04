package net.minedrop.sam;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * MDN-SAM — "Steal a Mineling" — The core gameplay plugin.
 *
 * <h3>TODO — Build Order (from plan/Plugin-making ranking.md)</h3>
 * <ol>
 *   <li><b>Core Match Engine</b> — Arena Manager, Match Manager, Teams, Spawn System</li>
 *   <li><b>Base System</b> — Player plots, protection, regions, building</li>
 *   <li><b>Conveyor System</b> — Belt physics, animation, spawn logic</li>
 *   <li><b>Minelings</b> — AI, capture, storage, placement</li>
 *   <li><b>Passive Income</b> — Timers, coin generation, upgrades</li>
 *   <li><b>Stealing</b> — Enter enemy base, carry statue, escape, anti-abuse</li>
 *   <li><b>Destroyers</b> — AI, attacks, boss bar, events</li>
 *   <li><b>Merchant NPCs</b> — Selling, buying, upgrades</li>
 *   <li><b>Cases</b> — Loot tables, animations, cosmetics</li>
 *   <li><b>Events</b> — Lucky conveyor, double coins, Brainrot event</li>
 *   <li><b>Leaderboards</b> — Global, clan, friends</li>
 *   <li><b>Polish</b> — Sounds, effects, PlaceholderAPI, GUI, animations</li>
 * </ol>
 *
 * <h3>Key admin commands</h3>
 * <ul>
 *   <li>/setbaseregion, /setbase, /setlockplate, /setstatue</li>
 *   <li>/publiclobby, /privatelobby, /createlobby</li>
 * </ul>
 *
 * @see plan/MineDrop/plugins/10_MDN_SAM.md
 * @see plan/MineDrop/SAB_Plugin_Design_Review.md
 */
public class SAMPaperPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("MDN-SAM loaded — awaiting implementation.");
        getLogger().info("Build stages remaining: 12 (see class Javadoc)");
        // TODO: Implement gameplay features (see class Javadoc)
    }

    @Override
    public void onDisable() {
        getLogger().info("MDN-SAM disabled. All game states saved.");
    }
}
