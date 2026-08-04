# MineDrop Network Specification — MDN-Core

## 📋 1. Overview
* **Consolidated Name**: `MDN-Core`
* **Target Environment**: Velocity & Paper
* **Purpose**: The foundational network core. It manages proxy routing, server state tracking, central Redis connection pools, HikariCP database connections, unified player cache profiles, and state synchronization (like cross-server inventories and active sessions).

---

## 🛠️ 2. Architectural Design & Modules
MDN-Core runs on both layers to coordinate cross-network operations:

```
[Velocity Proxy] ──── Redis Pub/Sub (Packet Bus) ──── [Paper Game Servers]
       │                                                      │
       ├─► (Session Cache)                                    ├─► (Inventory Cache)
       ▼                                                      ▼
[MySQL DB: Profiles]                                    [MySQL DB: Player Inventories]
```

### Modules
1. **Server Registry (Velocity)**: Automatically registers game servers dynamically on startup via a heartbeat packet.
2. **Session Manager (Velocity)**: Keeps track of online players, routing them to the appropriate lobby or plot server.
3. **Player Cache (Shared)**: Caches general player data in Redis for rapid read operations (username, UUID, IP, current server, active game state).
4. **Data Sync Engine (Paper)**: Handles automatic, async saving and loading of player inventories, ender chests, and status levels when switching servers.

---

## 📡 3. Communication Channels & Redis Packets
* **Redis Channel**: `mdn:core:bus`
* **Packet Schemes**:
  * `ServerHeartbeatPacket`: `{ "server": "lobby-01", "tps": 19.95, "players": 12, "max_players": 100 }`
  * `PlayerSwitchServerPacket`: `{ "uuid": "uuid-str", "target": "sam-plot-03", "force": true }`
  * `InventoryLockPacket`: `{ "uuid": "uuid-str", "locked": true, "timestamp": 1234567890 }`

---

## ⚙️ 4. Configuration Template
### `config.yml`
```yaml
# MDN-Core Base Configuration
database:
  host: "127.0.0.1"
  port: 3306
  database: "minedrop"
  username: "mdn_user"
  password: "secure_password"
  pool-settings:
    maximum-pool-size: 10
    minimum-idle: 2
    connection-timeout-ms: 30000
    idle-timeout-ms: 600000

redis:
  host: "127.0.0.1"
  port: 6379
  password: ""
  connection-timeout-ms: 2000

network:
  server-group: "lobby"
  save-interval-seconds: 300 # Periodical backup save
  session-expiry-seconds: 1200
```

---

## 🎮 5. Commands & Permissions
### Commands (Velocity & Paper)

| Command | Action | Permission | Target |
| :--- | :--- | :--- | :--- |
| `/mdn reload` | Reloads core config files. | `mdn.admin.reload` | Velocity & Paper |
| `/mdn server list`| Lists all dynamically registered servers. | `mdn.admin.servers` | Velocity |
| `/mdn sync <player>`| Manually pushes player inventory to DB. | `mdn.admin.sync` | Paper |
| `/hub` | Redirects player to the lobby. | `mdn.player.hub` | Velocity |

### Permission Nodes
* `mdn.player.use`: Essential login/interaction rights. (Default: true)
* `mdn.admin.core`: Full access to core control structures. (Default: operator)

---

## 🛡️ 6. Edge Cases & Solutions
* **Inventory Duplication (State Race Conditions)**:
  * *Issue*: A player logs out of Server A and logs into Server B quickly before Server A finished uploading their inventory to MySQL, resulting in rollback or duplication.
  * *Solution*: Implement a "State Locking" mechanism using Redis. When a player leaves Server A, a lock key (`player:lock:<uuid>`) is set in Redis. Server B will inspect this lock on player login. If the lock is present, Server B delays player loading (showing a "Loading your profile..." screen) until Server A finishes saving and releases the lock.
* **Database Outages**:
  * *Issue*: If MySQL goes down, all write operations fail, resulting in data loss on logout.
  * *Solution*: Keep a local JSON buffer folder on Paper (`plugins/MDN-Core/saves/`) to write failed profile saves. When connection is restored, the buffer is processed sequentially before allowing new logins.
