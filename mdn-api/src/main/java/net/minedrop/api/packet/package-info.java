/**
 * Network packet definitions for MineDrop's Redis Pub/Sub bus.
 * <p>
 * All packets extend {@link net.minedrop.api.packet.MDNPacket} and use
 * Jackson polymorphic deserialization based on the {@code packetType} field.
 * <p>
 * <b>Available packet types:</b>
 * <ul>
 *   <li>AUTH_UPDATE — 2FA/device verification completed</li>
 *   <li>PLAYER_ALERT — Notification popup to a player</li>
 *   <li>ECONOMY_SYNC — Update cached coin balance</li>
 *   <li>MODERATION_ACTION — Mute/ban/kick across the network</li>
 *   <li>CLAN_SYNC — Clan roster or stat updates</li>
 *   <li>SERVER_HEARTBEAT — Server health metrics</li>
 *   <li>PLAYER_SWITCH_SERVER — Player transfer signal</li>
 *   <li>INVENTORY_LOCK — Inventory duplication prevention</li>
 * </ul>
 *
 * @since 1.0.0
 */
package net.minedrop.api.packet;
