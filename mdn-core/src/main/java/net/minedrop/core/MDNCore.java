package net.minedrop.core;

/**
 * Shared constants and references for MDN-Core.
 */
public final class MDNCore {

    private MDNCore() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    // ── Redis Channels ──
    public static final String REDIS_CHANNEL_CORE        = "mdn:core:bus";
    public static final String REDIS_CHANNEL_PACKET_BUS  = "mdn_packet_bus";
    public static final String REDIS_CHANNEL_AUTH        = "mdn_auth";
    public static final String REDIS_CHANNEL_ECONOMY     = "mdn_economy";
    public static final String REDIS_CHANNEL_MODERATION  = "mdn_moderation";
    public static final String REDIS_CHANNEL_CLANS       = "mdn_clans";
    public static final String REDIS_CHANNEL_ALERTS      = "mdn_alerts";
    public static final String REDIS_CHANNEL_BRIDGE      = "mdn:bridge:handshake";

    // ── Redis Key Prefixes ──
    public static final String KEY_PLAYER_CACHE    = "player:cache:";
    public static final String KEY_PLAYER_LOCK     = "player:lock:";
    public static final String KEY_SERVER_STATUS   = "server:status:";
    public static final String KEY_CLAN_VAULT_LOCK = "clan:vault:lock:";

    // ── Permissions ──
    public static final String PERM_ADMIN_RELOAD  = "mdn.admin.reload";
    public static final String PERM_ADMIN_SERVERS = "mdn.admin.servers";
    public static final String PERM_ADMIN_SYNC    = "mdn.admin.sync";
    public static final String PERM_PLAYER_HUB    = "mdn.player.hub";
    public static final String PERM_PLAYER_USE    = "mdn.player.use";
}
