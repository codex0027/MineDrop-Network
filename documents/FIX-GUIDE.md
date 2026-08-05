# 🔧 Deployment Fix Guide — 3 Issues Found in Logs

## What Went Wrong

| # | Error in Log | What It Means | Status |
|---|-------------|---------------|--------|
| 1 | `No implementation for Logger (net.minedrop.libs.slf4j.Logger) was bound` | SLF4J was relocated, Velocity couldn't inject it | ✅ **Fixed in code** — rebuild JARs |
| 2 | `Plugin 'MDN-Bridge' is missing signature.json` | Paper requires signed plugins | 🔧 **1 setting to change** |
| 3 | `Communications link failure` (MySQL timeout) | Container can't reach MySQL | 🔧 **1 IP to fix** |

---

## Fix #1: Re-upload the JARs (SLF4J — Fixed in Code)

The code fix removes the SLF4J relocation that broke Velocity's Guice injection.
**Just rebuild + re-upload the JARs.** They're already built at:

```
mdn-bridge/build/libs/mdn-bridge-1.0.0-SNAPSHOT.jar   (18K)
mdn-core/build/libs/mdn-core-1.0.0-SNAPSHOT.jar       (~4M)
```

Upload both to **both** servers (Velocity + Lobby) via Pterodactyl File Manager → `plugins/`.

---

## Fix #2: Disable Plugin Signature Verification (Paper)

Paper 1.21+ checks for plugin signatures. Our JARs aren't signed → Paper disables them.

### On your Pterodactyl Lobby server:

1. Go to **Startup** tab
2. Find **"Additional Java Arguments"** or **"Server JAR Flags"**
3. Add this flag:
   ```
   -Dpaper.disable-plugin-signature-verification=true
   ```
4. Save

It should look like:
```
java -Xmx2G -Dpaper.disable-plugin-signature-verification=true -jar server.jar nogui
```

> ⚠️ This is safe for development. In production, you'd sign the JARs properly (separate guide).

---

## Fix #3: Fix MySQL Host IP (Connectivity)

The log shows `Connect timed out` — the plugin config has the wrong host IP.
Pterodactyl containers use Docker — they CANNOT reach `127.0.0.1` on the host.

### Option A: Use Docker Bridge IP (best)

SSH into your VPS and run:
```bash
ip addr show docker0 | grep "inet "
```

If it shows `172.17.0.1`, use that as your MySQL/Redis host.

### Option B: Use Pterodactyl's Database Host Feature

1. **Pterodactyl Admin** → **Database Hosts** → Add:
   - Name: `VPS MySQL`
   - Host: `172.17.0.1` (or the Docker bridge IP)
   - Port: `3306`
   - Username: `root` (or your MySQL user)

2. Go to **Lobby Server** → **Database** tab → **New Database**
3. Note the connection details Pterodactyl gives you.

### Option C: Allow External MySQL Connections

SSH into VPS:
```bash
# Edit MySQL config to listen on all interfaces
sudo nano /etc/mysql/mariadb.conf.d/50-server.cnf
# Change: bind-address = 0.0.0.0

# Create a user that can connect from Docker
sudo mysql -u root -p
```

```sql
CREATE USER 'mdn_user'@'172.%' IDENTIFIED BY 'YourPassword123!';
GRANT ALL PRIVILEGES ON minedrop.* TO 'mdn_user'@'172.%';
FLUSH PRIVILEGES;
```

Then use `172.17.0.1` as the host in `config.yml`.

---

## Final Config Files (Updated)

### Velocity Proxy: `plugins/mdn-bridge/config.yml`

```yaml
bridge:
  server-identity: "velocity-proxy-01"
  secret-api-key: "my-shared-secret-key-2024"
  handshake-timeout-seconds: 10
  handshake-retries: 3
```

### Paper Lobby: `plugins/mdn-bridge/config.yml`

```yaml
bridge:
  server-identity: "paper-lobby-01"
  secret-api-key: "my-shared-secret-key-2024"
  handshake-timeout-seconds: 10
  handshake-retries: 3
  allowed-build-hashes: []
  debug-mode: false

verification-failure:
  action: "SHUTDOWN"
  alert-webhook: ""
```

### Paper Lobby: `plugins/mdn-core/config.yml`

```yaml
database:
  host: "172.17.0.1"          # ← CHANGE THIS to your Docker bridge IP
  port: 3306
  database: "minedrop"
  username: "mdn_user"
  password: "YourPassword123!"
  pool-settings:
    maximum-pool-size: 10
    minimum-idle: 2
    connection-timeout-ms: 30000
    idle-timeout-ms: 600000

redis:
  host: "172.17.0.1"          # ← CHANGE THIS to your Docker bridge IP
  port: 6379
  password: ""
  connection-timeout-ms: 2000
  pubsub-channel: "mdn_packet_bus"

network:
  server-group: "lobby"
  default-region: "EU"
  save-interval-seconds: 300

commands:
  hub: true
  lobby: true
  spawn: true
  website: true
  store: true
  vote: true
  discord: true
  help: true
  rules: true
```

---

## Startup Order (Every Time)

```
1. Start MySQL + Redis (should already be running on VPS)
2. Start Paper Lobby  →  wait for console: "Done!"
3. Start Velocity     →  wait for console: "Listening on"
4. Test: /mdn health on lobby console
```

---

## Quick Test Commands

```bash
# On VPS — test Docker can reach MySQL
docker exec <lobby-container> mysql -h 172.17.0.1 -u mdn_user -p -e "SELECT 1"

# On VPS — test Docker can reach Redis
docker exec <lobby-container> redis-cli -h 172.17.0.1 PING

# On lobby console in Pterodactyl
/mdn health
```

---

## If It Still Fails

Upload your new logs to the repo as `Lobby.log` and `Proxy.log` and ping me.
I'll read them and fix whatever's left.
