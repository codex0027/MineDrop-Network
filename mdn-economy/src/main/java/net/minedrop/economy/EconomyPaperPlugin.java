package net.minedrop.economy;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * MDN-Economy — Transaction engine, auction house, NPC shop.
 *
 * <h3>TODO — Implementation Checklist</h3>
 * <ol>
 *   <li>Vault integration — register as economy provider</li>
 *   <li>Player wallets — MySQL-backed, Redis-cached balances</li>
 *   <li>Auction House GUI — list, browse, buy, cancel, expiry recovery</li>
 *   <li>NPC Shop — YAML-configurable categories, buy items with coins</li>
 *   <li>Commands: /money, /pay, /balancetop, /ah, /ah sell, /eco</li>
 *   <li>Anti-dupe: transaction locks, price ceilings per Mineling rarity</li>
 * </ol>
 *
 * @see plan/MineDrop/plugins/07_MDN_Economy.md
 */
public class EconomyPaperPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("MDN-Economy loaded — awaiting implementation.");
        // TODO: Implement economy features (see class Javadoc)
    }

    @Override
    public void onDisable() {
        getLogger().info("MDN-Economy disabled.");
    }
}
