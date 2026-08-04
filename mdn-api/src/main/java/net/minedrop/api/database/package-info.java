/**
 * Central database schema definitions for the MineDrop Network.
 * <p>
 * Every plugin references these SQL table names and DDL constants
 * to ensure consistent table structures. MDN-Core executes the DDL
 * statements on startup via {@link net.minedrop.api.database.DatabaseSchema#getCreateTableStatements()}.
 * <p>
 * <b>Tables:</b> mdn_schema_migrations, mdn_player_profiles, mdn_economy,
 * mdn_auction_house, mdn_clans, mdn_clan_members, mdn_friendships,
 * mdn_sam_player_data, mdn_auth_totp
 *
 * @since 1.0.0
 */
package net.minedrop.api.database;
