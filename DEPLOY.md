# 🚀 MineDrop Network — Pterodactyl Deployment Guide

> **Target**: 1 Velocity proxy + 2+ Paper game servers, all managed via Pterodactyl Panel  
> **Last Updated**: August 4, 2026 (evening — Redis + Bridge shadow JAR fixes)
> **Plugin Versions**: mdn-bridge 1.0.0, mdn-core 1.0.0
>
> **Two setup paths**:
> - **VPS-direct** (recommended): MySQL + Redis installed directly on VPS. Fastest.
> - **Pterodactyl eggs**: MySQL + Redis managed via Pterodactyl eggs. More control.

---

## 📋 Overview — What We're Building

```
┌──────────────────────────────────────────────────┐
│                  VPS / Dedicated                  │
│                                                   │
│  ┌──────────┐     ┌──────────┐     ┌──────────┐  │
│  │ Velocity │────▶│  Paper   │     │  Paper   │  │
│  │  Proxy   │     │  Lobby   │────▶│ Game Svr │  │
│  │ mdn-bridge│    │mdn-bridge│     │mdn-bridge│  │
│  │ mdn-core │     │ mdn-core │     │ mdn-core │  │
│  └────┬─────┘     └────┬─────┘     └────┬─────┘  │
│       │                │                │        │
│  ┌────┴────────────────┴────────────────┴────┐   │
│  │              Redis (Pub/Sub)              │   │
│  │         — cross-server messaging —        │   │
│  └───────────────────┬───────────────────────┘   │
│                      │                           │
│  ┌───────────────────┴───────────────────────┐   │
│  │              MySQL (MariaDB)              │   │
│  │      — player data, economy, clans —      │   │
│  └───────────────────────────────────────────┘   │
└──────────────────────────────────────────────────┘
```

---

## ⚡ QUICK START: VPS-Direct Setup (Recommended)

If MySQL and Redis are already installed on your VPS (not via Pterodactyl eggs),
skip Phase 1 entirely and use this:

### Prerequisites Check

```bash
mysql --version    # Should show MariaDB 10.x or MySQL 8.x
redis-cli PING     # Should respond PONG
ip addr show docker0 | grep "inet "   # Note the IP (usually 172.17.0.1)
```

### Database Setup (3 commands)

```bash
sudo mysql
```

```sql
CREATE DATABASE IF NOT EXISTS minedrop;
CREATE USER IF NOT EXISTS 'mdn_user'@'172.%' IDENTIFIED BY 'YourPassword123!';
GRANT ALL PRIVILEGES ON minedrop.* TO 'mdn_user'@'172.%';
FLUSH PRIVILEGES;
```

### Redis Setup (1 command)

```bash
sudo sed -i 's/^bind .*/bind 0.0.0.0/' /etc/redis/redis.conf && sudo systemctl restart redis-server
```

### Config IPs

Use `172.17.0.1` (your Docker bridge IP) for `database.host` and `redis.host` in `plugins/mdn-core/config.yml`.
Pterodactyl containers reach the host VPS through this IP.

Then skip to **Phase 2**.

---

## 🟢 PHASE 1: Pterodactyl MySQL & Redis Setup (Alternative)

### Step 1: Add the MySQL (MariaDB) Egg

1. Log into your Pterodactyl Panel as admin
2. Go to **Admin → Nests → Create New Nest**
   - Name: `Database Services`
3. Inside that nest, click **Import Egg**
4. Paste the official MariaDB egg JSON:
   - URL: `https://raw.githubusercontent.com/pelican-eggs/eggs/master/database/sql/mariadb/egg-mariadb.json`
   - Or search "Pterodactyl MariaDB egg" if the URL doesn't work
5. Go to **Servers → Create New Server**
   - **Name**: `MineDrop-MySQL`
   - **Owner**: Your user
   - **Nest**: `Database Services`
   - **Egg**: `MariaDB`
   - **Memory**: 512 MB (minimum — increase if you have many plugins)
   - **Disk**: 2 GB
   - **Ports**: Keep default (3306)
6. **Start the server** — first boot creates the database
7. Open the **Console** tab and note the auto-generated password
8. Open **File Manager** → edit `.my.cnf` if you want to change credentials

### Step 2: Add the Redis Egg

1. Go to **Admin → Nests → Database Services** (or create a new nest)
2. Click **Import Egg**
3. Paste the official Redis egg JSON:
   - URL: `https://raw.githubusercontent.com/pelican-eggs/eggs/master/database/nosql/redis/egg-redis.json`
4. Go to **Servers → Create New Server**
   - **Name**: `MineDrop-Redis`
   - **Nest**: `Database Services`  
   - **Egg**: `Redis`
   - **Memory**: 256 MB
   - **Disk**: 1 GB
   - **Ports**: Keep default (6379)
5. **Start the server**

### Step 3: Create the MySQL Database

1. Open **MineDrop-MySQL → Console**
2. Type these commands:

```sql
CREATE DATABASE minedrop;
CREATE USER 'mdn_user'@'%' IDENTIFIED BY 'YourSecurePassword123!';
GRANT ALL PRIVILEGES ON minedrop.* TO 'mdn_user'@'%';
FLUSH PRIVILEGES;
```

3. Note the credentials:
   - Host: `MineDrop-MySQL` (Pterodactyl internal hostname) or the node IP
   - Port: `3306`
   - Database: `minedrop`
   - Username: `mdn_user`
   - Password: `YourSecurePassword123!`

### Step 4: Verify Connectivity

From your VPS terminal (SSH), test both:

```bash
# Test MySQL — should get a mysql> prompt
mysql -h <mysql-server-ip> -P 3306 -u mdn_user -p minedrop
# Type: SELECT 1; then exit

# Test Redis — should get PONG
redis-cli -h <redis-server-ip> -p 6379 PING
```

---

## 🟢 PHASE 2: Create the Minecraft Servers

### Step 5: Velocity Proxy Server

1. **Create New Server** in Pterodactyl:
   - **Name**: `MineDrop-Velocity`
   - **Nest**: Minecraft → **Egg**: `Velocity`
   - **Memory**: 1 GB
   - **Disk**: 2 GB
   - **Primary Allocation**: The port players connect to (e.g., 25565)
   - **Startup Command**: `java -Xms512M -Xmx1G -jar velocity.jar`
   - **Java Version**: 21 (or latest)

2. **Start once** to generate files, then **Stop**

3. In **File Manager**, edit `velocity.toml`:
```toml
bind = "0.0.0.0:25565"
online-mode = false
forwarding-secret-file = "forwarding.secret"

[servers]
lobby = "MineDrop-Lobby:25566"
game1 = "MineDrop-Game1:25567"

[forced-hosts]
"minedrop.net" = ["lobby"]
```

> ⚠️ **Important**: Replace `MineDrop-Lobby`, `MineDrop-Game1` with your actual Pterodactyl server hostnames. The port is the server's primary allocation port.

### Step 6: Paper Lobby Server

1. **Create New Server**:
   - **Name**: `MineDrop-Lobby`
   - **Egg**: `Paper`
   - **Memory**: 2 GB
   - **Disk**: 5 GB
   - **Primary Allocation**: Port 25566 (or any available)
   - **Java Version**: 21

2. **Start once, then Stop**

3. Edit `server.properties`:
```properties
online-mode=false
server-port=25566
spawn-protection=0
allow-flight=true
```

4. Edit `spigot.yml`:
```yaml
settings:
  bungeecord: true  # IMPORTANT — enables Velocity compatibility
```

### Step 7: Paper Game Server(s)

Repeat Step 6 for each game server:
- **Name**: `MineDrop-Game1`, `MineDrop-Game2`, etc.
- **Egg**: `Paper`
- **Memory**: 4 GB (game servers need more)
- **Primary Allocation**: Unique port (25567, 25568...)
- Same `spigot.yml` setting: `bungeecord: true`

---

## 🟢 PHASE 3: Deploy the Plugins

### Step 8: Build the JARs (on your dev machine)

```bash
cd MineDrop-Network
JAVA_HOME=/usr/lib/jvm/java-1.21.0-openjdk-amd64 ./gradlew :mdn-bridge:shadowJar :mdn-core:shadowJar
```

The JARs are at:
- `mdn-bridge/build/libs/mdn-bridge-1.0.0-SNAPSHOT.jar`
- `mdn-core/build/libs/mdn-core-1.0.0-SNAPSHOT.jar`

### Step 9: Upload to Velocity

Via Pterodactyl **File Manager**, upload to `plugins/`:

```
MineDrop-Velocity/plugins/
├── mdn-bridge-1.0.0-SNAPSHOT.jar
├── mdn-core-1.0.0-SNAPSHOT.jar
└── mdn-bridge/
    └── config.yml          ← create this (see below)
```

Then create `plugins/mdn-bridge/config.yml`:
```yaml
bridge:
  server-identity: "velocity-proxy-01"
  secret-api-key: "your-shared-secret-key-change-me"
  handshake-timeout-seconds: 10
  handshake-retries: 3
```

### Step 10: Upload to Each Paper Server

For **every** Paper server (Lobby + Game servers):

Upload to `plugins/`:
```
MineDrop-Lobby/plugins/
├── mdn-bridge-1.0.0-SNAPSHOT.jar
├── mdn-core-1.0.0-SNAPSHOT.jar
├── mdn-bridge/
│   └── config.yml
└── mdn-core/
    └── config.yml
```

**`plugins/mdn-bridge/config.yml`** (Paper servers):
```yaml
bridge:
  server-identity: "paper-lobby-01"    # CHANGE per server!
  secret-api-key: "your-shared-secret-key-change-me"
  handshake-timeout-seconds: 10
  allowed-build-hashes: []             # Leave empty for now
  debug-mode: false                    # NEVER enable on public IP!

verification-failure:
  action: "SHUTDOWN"
  alert-webhook: ""
```

> ⚠️ **CRITICAL**: Each Paper server MUST have a unique `server-identity`:
> - `paper-lobby-01`
> - `paper-game1-01`
> - `paper-game2-01`
> - etc.

**`plugins/mdn-core/config.yml`** (Paper servers):
```yaml
database:
  host: "<mysql-pterodactyl-host>"
  port: 3306
  database: "minedrop"
  username: "mdn_user"
  password: "YourSecurePassword123!"
  pool-settings:
    maximum-pool-size: 10
    minimum-idle: 2
    connection-timeout-ms: 30000
    idle-timeout-ms: 600000

redis:
  host: "<redis-pterodactyl-host>"
  port: 6379
  password: ""
  connection-timeout-ms: 2000
  pubsub-channel: "mdn_packet_bus"

network:
  server-group: "lobby"       # "lobby" for lobby, "sam-public" or custom for games
  save-interval-seconds: 300

commands:
  website: true
  store: true
  vote: true
  discord: true
  help: true
  rules: true
```

> 💡 **Finding Pterodactyl hostnames**: In Pterodactyl, each server gets an internal hostname like `a1b2c3d4`. You can use this, or use the node's IP. The container-to-container networking in Pterodactyl usually works via `servername.containername`.

### Step 11: How Pterodactyl Networking Works

Pterodactyl containers communicate via Docker networking. To find the exact connection details:

1. Go to **MineDrop-MySQL → Startup** — note the allocation IP:Port
2. Go to **MineDrop-Redis → Startup** — note the allocation IP:Port
3. These IPs are usually the **node's IP** (e.g., `172.18.0.5`)
4. In your plugin configs, use these IPs for `database.host` and `redis.host`

Alternatively, if all servers are on the same node, you can use the container name:
- MySQL: `MineDrop-MySQL` (the Pterodactyl short UUID)
- Redis: `MineDrop-Redis`

---

## 🟢 PHASE 4: Startup & Verification

### Step 12: Startup Order (THIS MATTERS)

Start servers in this EXACT order:

1. ⬆️ **MineDrop-MySQL** — wait for "ready for connections"
2. ⬆️ **MineDrop-Redis** — wait for "Ready to accept connections"
3. ⬆️ **MineDrop-Lobby** (Paper) — watch console for:
   ```
   [MDN-Bridge] MDN-Bridge loaded. Server identity: paper-lobby-01
   [MDN-Bridge] Velocity handshake SUCCESS on attempt 1
   [MDN-Core] MySQL health check: PASSED
   [MDN-Core] Redis health check: PASSED
   [MDN-Core] Database schema initialized successfully (9 tables).
   [MDN-Core] MDN-Core Paper enabled. All systems ready.
   ```
4. ⬆️ **MineDrop-Game1**, **MineDrop-Game2** — same checks
5. ⬆️ **MineDrop-Velocity** — watch console for:
   ```
   [MDN-Bridge] MDN-Bridge Velocity initialized.
   [MDN-Core] Redis: connected
   [MDN-Core] MDN-Core Velocity initialized. 2 servers discovered.
   ```

### Step 13: Verify Everything Works

On any Paper server console, run:
```
/mdn health
```

Expected output:
```
Health Report
  API: v1.0.0 | Instance: <uuid>
  DB: true | Circuit: CLOSED
  Redis: true | Circuit: CLOSED
  DLQ: 0 pending / 0 permanent
  TPS: 20.0
  Players: 0/100
  Memory: 128 MB / 2048 MB
```

On Velocity console, run:
```
/mdn servers
```

Expected output:
```
Registered Servers (2):
 ✓ paper-lobby-01 0/100 TPS:20.0 lobby
 ✓ paper-game1-01 0/100 TPS:20.0 sam-public
```

---

## 🔴 Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `CONFIG ERROR: database.host is not set!` | Missing or malformed config.yml | Check `plugins/mdn-core/config.yml` exists and has proper YAML syntax |
| `MySQL health check: FAILED` | MySQL unreachable or wrong credentials | Verify host/port/username/password. Test with `mysql` CLI from VPS |
| `Redis health check: FAILED` | Redis unreachable | Verify host/port. Test with `redis-cli PING` from VPS |
| `Handshake FAILED after 3 attempts` | Wrong `secret-api-key` or Velocity not running | Make sure same key in ALL config.yml files. Start Velocity last |
| `No handler registered for packet type` | Plugin load order wrong | mdn-bridge must load before mdn-core. Check `plugins/` directory listing |
| `Circuit OPEN` | DB/Redis had 5 consecutive failures | Service is recovering — circuit auto-closes after 30s. Check service logs |
| Server shows as offline | `bungeecord: true` not set | Edit `spigot.yml` on every Paper server |

---

## 🔧 Build Notes (August 4, 2026 — Evening Build)

### Critical fixes in this build:

1. **Redis "Connection reset"** — JedisPoolConfig now has `testOnCreate`, `testOnBorrow`,
   and `testWhileIdle` validation. Prevents dead connections in Docker/Pterodactyl.

2. **Bridge shadow JAR** — Fixed `jar` task overwriting `shadowJar` on clean builds.
   Both JARs now properly bundle all dependencies (4.0 MB each).

3. **Startup ordering** — PacketDispatcher created before DeadLetterQueue (no more NPE).
   MDNAPI initialized on Velocity. BridgeManager decoupled from MDNAPI.

### JAR locations after build:
```
mdn-bridge/build/libs/mdn-bridge-1.0.0-SNAPSHOT.jar  (4.0 MB — includes Jackson)
mdn-core/build/libs/mdn-core-1.0.0-SNAPSHOT.jar      (4.0 MB — includes all deps)
```

---

## 📊 Quick Reference Card

```
┌──────────────────────┬──────────────────────┬──────────────────────┐
│     VELOCITY         │     PAPER LOBBY      │     PAPER GAMES      │
├──────────────────────┼──────────────────────┼──────────────────────┤
│ mdn-bridge.jar       │ mdn-bridge.jar       │ mdn-bridge.jar       │
│ mdn-core.jar         │ mdn-core.jar         │ mdn-core.jar         │
│                      │                      │                      │
│ configs:             │ configs:             │ configs:             │
│  mdn-bridge/config   │  mdn-bridge/config   │  mdn-bridge/config   │
│                      │  mdn-core/config     │  mdn-core/config     │
├──────────────────────┼──────────────────────┼──────────────────────┤
│ identity:            │ identity:            │ identity:            │
│ velocity-proxy-01    │ paper-lobby-01       │ paper-game1-01       │
│                      │ server-group: lobby  │ server-group: sam    │
├──────────────────────┼──────────────────────┼──────────────────────┤
│ Secret key: SAME ACROSS ALL SERVERS!                              │
└──────────────────────┴──────────────────────┴──────────────────────┘
```

---

*Deploy guide created August 4, 2026. Built for Pterodactyl Panel + MineDrop Network v1.0.0.*
