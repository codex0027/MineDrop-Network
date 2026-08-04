package net.minedrop.social;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * MDN-Social — Friends, Clans, Teams.
 *
 * <h3>TODO — Implementation Checklist</h3>
 * <ol>
 *   <li>Friend system — add/remove, requests, online status, block list</li>
 *   <li>Clan creation — /team create, max 10 members, roles (Leader/Co-Leader/Officer/Member)</li>
 *   <li>Clan GUI — browse clans, join, member management</li>
 *   <li>Clan leveling — XP from captures/defenses, level-up rewards</li>
 *   <li>Shared vaults — Redis-locked to prevent dupes</li>
 *   <li>Idle leader demotion — auto-promote after 30 days offline</li>
 * </ol>
 *
 * @see plan/MineDrop/plugins/08_MDN_Social.md
 */
public class SocialPaperPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("MDN-Social loaded — awaiting implementation.");
        // TODO: Implement social features (see class Javadoc)
    }

    @Override
    public void onDisable() {
        getLogger().info("MDN-Social disabled.");
    }
}
