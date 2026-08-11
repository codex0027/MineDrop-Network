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
    public static final String TABLE_ACCOUNTS         = "mdn_accounts";
    public static final String TABLE_BACKUP_CODES     = "mdn_backup_codes";
    public static final String TABLE_PASSWORD_RESETS  = "mdn_password_resets";
    public static final String TABLE_AUTH_AUDIT       = "mdn_auth_audit";

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
                """,

                // 9. Auth Accounts (password-based authentication)
                """
                CREATE TABLE IF NOT EXISTS mdn_accounts (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    uuid CHAR(36) NOT NULL,
                    username VARCHAR(16) NOT NULL,
                    status ENUM('PENDING', 'ACTIVE', 'SUSPENDED')
                        NOT NULL DEFAULT 'PENDING',

                    password_hash VARCHAR(255) NOT NULL,
                    password_version INT NOT NULL DEFAULT 1,
                    password_changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    last_login_at TIMESTAMP NULL,
                    last_ip VARCHAR(64) NULL,

                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP,

                    PRIMARY KEY (id),
                    UNIQUE KEY uq_mdn_accounts_uuid (uuid),
                    INDEX idx_mdn_accounts_username (username),
                    INDEX idx_mdn_accounts_status (status)
                )
                """,

                // 10. Auth Backup Codes (hashed, single-use)
                """
                CREATE TABLE IF NOT EXISTS mdn_backup_codes (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    account_id BIGINT UNSIGNED NOT NULL,

                    code_hash VARCHAR(255) NOT NULL,
                    used_at TIMESTAMP NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                    PRIMARY KEY (id),

                    CONSTRAINT fk_mdn_backup_account
                        FOREIGN KEY (account_id)
                        REFERENCES mdn_accounts(id)
                        ON DELETE CASCADE,

                    INDEX idx_mdn_backup_account (account_id)
                )
                """,

                // 11. Auth Password Resets (token-based recovery)
                """
                CREATE TABLE IF NOT EXISTS mdn_password_resets (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    account_id BIGINT UNSIGNED NOT NULL,

                    token_hash VARCHAR(255) NOT NULL,
                    expires_at TIMESTAMP NOT NULL,
                    used_at TIMESTAMP NULL,

                    requested_ip VARCHAR(64) NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                    PRIMARY KEY (id),

                    CONSTRAINT fk_mdn_reset_account
                        FOREIGN KEY (account_id)
                        REFERENCES mdn_accounts(id)
                        ON DELETE CASCADE,

                    UNIQUE KEY uq_mdn_reset_token (token_hash),
                    INDEX idx_mdn_reset_account (account_id),
                    INDEX idx_mdn_reset_expiry (expires_at)
                )
                """,

                // 12. Auth Audit Log (spec §80-81)
                """
                CREATE TABLE IF NOT EXISTS mdn_auth_audit (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

                    account_id BIGINT UNSIGNED NULL,
                    uuid CHAR(36) NULL,

                    event_type VARCHAR(64) NOT NULL,
                    source_ip VARCHAR(64) NULL,
                    metadata_json JSON NULL,

                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                    PRIMARY KEY (id),

                    INDEX idx_auth_audit_account (account_id),
                    INDEX idx_auth_audit_uuid (uuid),
                    INDEX idx_auth_audit_event (event_type),
                    INDEX idx_auth_audit_created (created_at)
                )
                """
        );
    }
}
