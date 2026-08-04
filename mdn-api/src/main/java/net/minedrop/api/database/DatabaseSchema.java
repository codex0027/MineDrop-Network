package net.minedrop.api.database;

import java.util.List;

/**
 * Central database schema definitions for the MineDrop Network.
 * <p>
 * Every plugin references these SQL constants to ensure consistent table
 * structures. MDN-Core is responsible for executing the DDL statements on startup.
 */
public final class DatabaseSchema {

    private DatabaseSchema() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    // ── Table Names ──

    public static final String TABLE_SCHEMA_MIGRATIONS = "mdn_schema_migrations";
    public static final String TABLE_PLAYER_PROFILES  = "mdn_player_profiles";
    public static final String TABLE_ECONOMY          = "mdn_economy";
    public static final String TABLE_AUCTION_HOUSE    = "mdn_auction_house";
    public static final String TABLE_CLANS            = "mdn_clans";
    public static final String TABLE_CLAN_MEMBERS     = "mdn_clan_members";
    public static final String TABLE_FRIENDSHIPS      = "mdn_friendships";
    public static final String TABLE_SAM_PLAYER_DATA  = "mdn_sam_player_data";
    public static final String TABLE_AUTH_TOTP        = "mdn_auth_totp";

    // ── DDL Statements ──

    /** Returns the ordered list of CREATE TABLE statements for database initialization. */
    public static List<String> getCreateTableStatements() {
        return List.of(
                // 1. Core Player Profile
                """
                CREATE TABLE IF NOT EXISTS mdn_player_profiles (
                    uuid VARCHAR(36) PRIMARY KEY,
                    username VARCHAR(16) NOT NULL,
                    first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    ip_address VARCHAR(45) NOT NULL,
                    device_fingerprint VARCHAR(64) DEFAULT NULL,
                    is_staff BOOLEAN DEFAULT FALSE
                )
                """,

                // 2. Economy
                """
                CREATE TABLE IF NOT EXISTS mdn_economy (
                    uuid VARCHAR(36) PRIMARY KEY,
                    coins DOUBLE PRECISION DEFAULT 1000.0,
                    prestige_points INT DEFAULT 0,
                    FOREIGN KEY (uuid) REFERENCES mdn_player_profiles(uuid) ON DELETE CASCADE
                )
                """,

                // 3. Auction House
                """
                CREATE TABLE IF NOT EXISTS mdn_auction_house (
                    id VARCHAR(36) PRIMARY KEY,
                    seller_uuid VARCHAR(36) NOT NULL,
                    item_serialized TEXT NOT NULL,
                    price DOUBLE PRECISION NOT NULL,
                    list_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    expiry_time TIMESTAMP NOT NULL,
                    is_claimed BOOLEAN DEFAULT FALSE,
                    FOREIGN KEY (seller_uuid) REFERENCES mdn_player_profiles(uuid) ON DELETE CASCADE
                )
                """,

                // 4. Clans
                """
                CREATE TABLE IF NOT EXISTS mdn_clans (
                    clan_id VARCHAR(36) PRIMARY KEY,
                    clan_name VARCHAR(24) UNIQUE NOT NULL,
                    leader_uuid VARCHAR(36) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    level INT DEFAULT 1,
                    experience INT DEFAULT 0,
                    vault_serialized TEXT DEFAULT NULL
                )
                """,

                // 5. Clan Members
                """
                CREATE TABLE IF NOT EXISTS mdn_clan_members (
                    uuid VARCHAR(36) PRIMARY KEY,
                    clan_id VARCHAR(36),
                    clan_role VARCHAR(12) DEFAULT 'MEMBER',
                    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (clan_id) REFERENCES mdn_clans(clan_id) ON DELETE SET NULL
                )
                """,

                // 6. Friendships
                """
                CREATE TABLE IF NOT EXISTS mdn_friendships (
                    player_one VARCHAR(36) NOT NULL,
                    player_two VARCHAR(36) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (player_one, player_two)
                )
                """,

                // 7. SAM Player Data
                """
                CREATE TABLE IF NOT EXISTS mdn_sam_player_data (
                    uuid VARCHAR(36) PRIMARY KEY,
                    inventory_serialized TEXT NOT NULL,
                    unslotted_statues TEXT NOT NULL,
                    total_stolen INT DEFAULT 0,
                    total_collected INT DEFAULT 0,
                    FOREIGN KEY (uuid) REFERENCES mdn_player_profiles(uuid) ON DELETE CASCADE
                )
                """,

                // 0. Schema Migrations (must be created first — fixes M-3)
                """
                CREATE TABLE IF NOT EXISTS mdn_schema_migrations (
                    version VARCHAR(32) PRIMARY KEY,
                    description VARCHAR(255) NOT NULL,
                    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """,

                // 8. Auth TOTP
                """
                CREATE TABLE IF NOT EXISTS mdn_auth_totp (
                    uuid VARCHAR(36) PRIMARY KEY,
                    totp_secret VARCHAR(32) NOT NULL,
                    backup_codes TEXT NOT NULL,
                    ip_lock VARCHAR(45) DEFAULT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """
        );
    }
}
