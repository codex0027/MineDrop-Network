# 🔧 Deployment Fix Guide — Updated August 6, 2026

## What Went Wrong (Now All Fixed)

| # | Error in Log | What It Means | Status |
|---|-------------|---------------|--------|
| 1 | `No implementation for Logger` | SLF4J was relocated, Velocity couldn't inject it | ✅ Fixed — rebuild JARs |
| 2 | `Plugin 'MDN-Bridge' is missing signature.json` | signature.json not generated at build time | ✅ Fixed — auto-generated via finalizedBy |
| 3 | `Communications link failure` (MySQL timeout) | Container can't reach MySQL | 🔧 Set correct host IP |
| 4 | `Server EVICTED: lobby (no heartbeat for 45s)` | discoverServers pre-registered servers | ✅ Fixed — servers self-register via heartbeat |
| 5 | `Plugin 'MDN-Core' is missing signature.json` | mdn-core had no signature.json | ✅ Fixed — both plugins now auto-generate |

---

## Fix #1: Rebuild the JARs (signature.json now auto-generated)

```bash
cd MineDrop-Network
JAVA_HOME=/usr/lib/jvm/java-1.21.0-openjdk-amd64 ./gradlew :mdn-bridge:shadowJar :mdn-core:shadowJar
```

The build log prints the hashes:
```
  [signature] mdn-bridge: d78a76a066371c549912c21be1c18ee16d2d63eb7404c45d5d83e47c2048bc2b
  [signature] mdn-core: 9bfac3f66f53394533cbb4094179e1aa5ec2b34af0a54934ae54b5dccb01f8df
```

Copy these hashes into the config files below. Upload JARs to both servers via Pterodactyl File Manager → `plugins/`.

---

## Fix #2: Update Config Files with Build Hashes

### Velocity Proxy: `plugins/mdn-bridge/config.yml`

```yaml
bridge:
  server-identity: "velocity-proxy-01"
  secret-api-key: "your-shared-secret-key-change-me"
  handshake-timeout-seconds: 10
  handshake-retries: 3
  debug-mode: false
  allowed-build-hashes:
    - "d78a76a066371c549912c21be1c18ee16d2d63eb7404c45d5d83e47c2048bc2b"
```

### Paper Lobby: `plugins/mdn-bridge/config.yml`

```yaml
bridge:
  server-identity: "paper-lobby-01"
  secret-api-key: "your-shared-secret-key-change-me"
  handshake-timeout-seconds: 10
  handshake-retries: 3
  allowed-build-hashes:
    - "d78a76a066371c549912c21be1c18ee16d2d63eb7404c45d5d83e47c2048bc2b"
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

## Fix #3: Fix MySQL Host IP (Connectivity)

The log shows `Connect timed out` — the plugin config has the wrong host IP.
Pterodactyl containers use Docker — they CANNOT reach `127.0.0.1` on the host.

### Option A: Use Docker Bridge IP (best)

SSH into your VPS and run:
```bash
ip addr show docker0 | grep "inet "
```

If it shows `172.17.0.1`, use that as your MySQL/Redis host.

---

## Startup Order (Every Time)

```
1. Start MySQL + Redis (should already be running on VPS)
2. Start Paper Lobby  →  wait for console: "Done!"
3. Start Velocity     →  wait for console: "Listening on"
4. Test: /mdn health on lobby console
```

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

*Last updated: August 6, 2026 — signature.json auto-gen, Velocity allowed-hashes, server eviction fix*
