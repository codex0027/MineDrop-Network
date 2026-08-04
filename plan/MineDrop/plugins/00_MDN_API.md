# MineDrop Network Specification — Shared API Library (MDN-API)

## 📋 1. Overview
* **Consolidated Name**: `MDN-API`
* **Target Environment**: Shared Library (Compiled as a standalone `.jar` shaded or added to the classpath of both Velocity and Paper plugins).
* **Target Audience**: Core Developers building MineDrop plugins.
* **Purpose**: Provides a unified, single source of truth for cross-plugin data models, database structures, Redis pub/sub packets, custom events, and developer hooks.

---

## 🛠️ 2. Architectural Dependencies
```
        [Velocity Plugins]             [Paper Plugins]
               │                              │
               └──────────────┬───────────────┘
                              ▼
                         [ MDN-API ]
                              │
               ┌──────────────┼──────────────┐
               ▼              ▼              ▼
           [ MySQL ]       [ Redis ]     [ Lombok/Slf4j ]
```

* **Core Libraries**:
  * Lombok (Data tags, builders)
  * Jackson / Gson (JSON serialization/deserialization)
  * HikariCP (High-performance SQL database pooling)
  * Jedis / Lettuce (Redis connectivity)

---

## 💾 3. Database Schema Blueprint
Every plugin uses this shared API library to initialize its SQL tables. The schema must be database-independent (standard SQL).

```sql
-- 1. Core Player Session Table
CREATE TABLE IF NOT EXISTS mdn_player_profiles (
    uuid VARCHAR(36) PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    ip_address VARCHAR(45) NOT NULL,
    device_fingerprint VARCHAR(64) DEFAULT NULL,
    is_staff BOOLEAN DEFAULT FALSE
);

-- 2. Economy Tables
CREATE TABLE IF NOT EXISTS mdn_economy (
    uuid VARCHAR(36) PRIMARY KEY,
    coins DOUBLE PRECISION DEFAULT 1000.0,
    prestige_points INT DEFAULT 0,
    FOREIGN KEY (uuid) REFERENCES mdn_player_profiles(uuid) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS mdn_auction_house (
    id VARCHAR(36) PRIMARY KEY,
    seller_uuid VARCHAR(36) NOT NULL,
    item_serialized TEXT NOT NULL, -- NBT base64 or JSON structure
    price DOUBLE PRECISION NOT NULL,
    list_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expiry_time TIMESTAMP NOT NULL,
    is_claimed BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (seller_uuid) REFERENCES mdn_player_profiles(uuid) ON DELETE CASCADE
);

-- 3. Social & Clan Tables
CREATE TABLE IF NOT EXISTS mdn_clans (
    clan_id VARCHAR(36) PRIMARY KEY,
    clan_name VARCHAR(24) UNIQUE NOT NULL,
    leader_uuid VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    level INT DEFAULT 1,
    experience INT DEFAULT 0,
    vault_serialized TEXT DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS mdn_clan_members (
    uuid VARCHAR(36) PRIMARY KEY,
    clan_id VARCHAR(36),
    clan_role VARCHAR(12) DEFAULT 'MEMBER', -- LEADER, CO_LEADER, OFFICER, MEMBER
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (clan_id) REFERENCES mdn_clans(clan_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS mdn_friendships (
    player_one VARCHAR(36) NOT NULL,
    player_two VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_one, player_two)
);

-- 4. Game Storage (SAM)
CREATE TABLE IF NOT EXISTS mdn_sam_player_data (
    uuid VARCHAR(36) PRIMARY KEY,
    inventory_serialized TEXT NOT NULL, -- Base64 serialized custom Mineling inventory
    unslotted_statues TEXT NOT NULL,     -- JSON array of statues stored but not placed
    total_stolen INT DEFAULT 0,
    total_collected INT DEFAULT 0,
    FOREIGN KEY (uuid) REFERENCES mdn_player_profiles(uuid) ON DELETE CASCADE
);
```

---

## 📡 4. Redis Communication & Packet Bus Spec
The library defines a custom packet wrapper interface, `MDNPacket`, which is serialized to JSON and broadcast via Redis Pub/Sub channel `"mdn_packet_bus"`.

### Base Packet Class (Java)
```java
package net.minedrop.api.packet;

import java.util.UUID;

public abstract class MDNPacket {
    private final String packetType;
    private final UUID senderId;
    private final long timestamp;

    public MDNPacket(String packetType, UUID senderId) {
        this.packetType = packetType;
        this.senderId = senderId;
        this.timestamp = System.currentTimeMillis();
    }

    public String getPacketType() { return packetType; }
    public UUID getSenderId() { return senderId; }
    public long getTimestamp() { return timestamp; }
    
    public abstract String serialize();
}
```

### Packet Registries (Standard Types)

| Packet Name | Description | Channel | Payload Fields |
| :--- | :--- | :--- | :--- |
| `AuthUpdatePacket` | Signals a player completed 2FA or device check. | `mdn_auth` | `uuid` (UUID), `status` (Boolean) |
| `PlayerAlertPacket` | Sends notification popup / action bar to a user. | `mdn_alerts` | `uuid` (UUID), `message` (String), `type` (ALERT/ALERT_ERROR) |
| `EconomySyncPacket` | Informs other servers to update a player's cached balance. | `mdn_economy` | `uuid` (UUID), `new_balance` (Double) |
| `ModerationActionPacket` | Executes mute/ban kicks across the network instantly. | `mdn_moderation` | `target` (UUID), `type` (BAN/MUTE/KICK), `expiry` (Long) |
| `ClanSyncPacket` | Updates active rosters or clan stats. | `mdn_clans` | `clan_id` (UUID), `action` (DISBAND/JOIN/LEAVE), `player` (UUID) |

---

## 📅 5. Custom Event Hooks (Paper API)
Custom spigot events are registered here so any game sub-module can listen to them.

### `StatueStealEvent`
Triggered when a player successfully sneaks a statue from an enemy base and places it into their own.
```java
package net.minedrop.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.entity.Player;
import java.util.UUID;

public class StatueStealEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player thief;
    private final UUID victimUuid;
    private final String statueRarity;
    private final double value;

    public StatueStealEvent(Player thief, UUID victimUuid, String statueRarity, double value) {
        this.thief = thief;
        this.victimUuid = victimUuid;
        this.statueRarity = statueRarity;
        this.value = value;
    }

    public Player getThief() { return thief; }
    public UUID getVictimUuid() { return victimUuid; }
    public String getStatueRarity() { return statueRarity; }
    public double getValue() { return value; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
```

---

## 🛡️ 6. Common Encryption & Verification Utility
Used by `MDN-Bridge` to validate signatures and build integrity.

```java
package net.minedrop.api.security;

import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class SecurityUtil {
    public static String getSHA256Hash(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String decrypt(String encryptedText, String secretKey) throws Exception {
        byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(secretKey.getBytes("UTF-8"));
        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
        return new String(decryptedBytes, "UTF-8");
    }
}
```
