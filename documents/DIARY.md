# MineDrop Network — Development Diary

> **Last Updated**: August 11, 2026 — all 7 MDN-Auth gaps fixed + production hardened  
> **Build Status**: ✅ All 4 plugins building + signature-verified — mdn-auth production-ready
> **Branch**: `main` | **Commit**: `afda633` (gap fixes)

> **📚 Companion Docs**: [STEPS.md](STEPS.md) (step-by-step log) · [SUGGEST.md](SUGGEST.md) (suggestions catalog) · [TIMELINE.md](TIMELINE.md) (roadmap)

---

## 📖 Table of Contents

1. [What We're Building](#what-were-building)
2. [Project Structure](#project-structure)
3. [Architecture Decisions](#architecture-decisions)
4. [Round 1 — Monorepo Foundation](#round-1--monorepo-foundation)
5. [Round 2 — Production Hardening](#round-2--production-hardening)
6. [Round 3 — Resilience & Observability](#round-3--resilience--observability)
7. [Round 4 — Dead Letter Queue & Operation Timeouts](#round-4--dead-letter-queue--operation-timeouts)
8. [Round 5 — Velocity Config Bootstrap Fix](#round-5--velocity-config-bootstrap-fix)
9. [Round 6 — Cross-Server Handshake & Signature Verification](#round-6--cross-server-handshake--signature-verification)
10. [Round 7 — MDN-Auth Plugin #4 Implementation](#round-7--mdn-auth-plugin-4-implementation)
11. [Round 8 — MDN-Auth Gap Fixes & Production Hardening](#round-8--mdn-auth-gap-fixes--production-hardening)
12. [File Map — Every File Explained](#file-map--every-file-explained)
9. [How to Build](#how-to-build)
10. [How to Add a New Plugin](#how-to-add-a-new-plugin)
11. [How to Add a New Feature](#how-to-add-a-new-feature)
14. [Test Coverage](#test-coverage)
15. [Known Gaps & Future Work](#known-gaps--future-work)
16. [Conventions & Style Guide](#conventions--style-guide)

---

## What We're Building

**MineDrop Network** is a Minecraft minigames network running on **Velocity** (proxy) and **Paper** (game servers). The flagship game is **"Steal a Mineling" (SAM)** — a conveyor-belt base-defence PvPvE game never seen before in Minecraft.

The codebase is a **Gradle monorepo** containing 10 plugins. Four are fully implemented with production-grade code, and six are skeletons waiting for new developers.

---

## Project Structure

```
MineDrop-Network/
│
├── build.gradle.kts              # Root build — repos, group, version
├── settings.gradle.kts           # Includes all 10 subprojects
├── gradle.properties             # Version catalog
├── DIARY.md                      # ← You are here
├── README.md                     # Quick-start guide
├── .gitignore
│
├── gradlew / gradlew.bat         # Gradle wrapper (Java 21 required)
├── gradle/wrapper/
│
├── plan/                         # Original design documents (reference only)
│   └── MineDrop/
│       ├── MINEDROP - A MINIGAMES SERVER...md
│       ├── plugins-roadmap.md
│       ├── Plugin-making ranking.md
│       ├── SAB_Plugin_Design_Review.md
│       └── plugins/              # 11 design specs (00-10_MDN_*.md)
│
│   ★ = Fully Implemented    ◻ = Skeleton
│
├── mdn-api/          ★ Shared library — packets, DB schema, security, events, versioning
├── mdn-bridge/       ★ Security foundation — signature verification, handshake, plugin validation
├── mdn-core/         ★ Network heartbeat — sessions, routing, cache, sync, circuit breakers
│
├── mdn-auth/         ★ Authentication — TOTP 2FA, alt detection, device fingerprinting (Velocity)
├── mdn-security/     ◻ Skeleton — Anti-cheat & exploit prevention (Paper)
├── mdn-economy/      ◻ Skeleton — Coins, auction house, NPC shop (Paper)
├── mdn-social/       ◻ Skeleton — Friends & clans (Paper)
├── mdn-communication/◻ Skeleton — Chat & Discord bridge (Dual)
├── mdn-maintenance/  ◻ Skeleton — Whitelist, restarts, lockdown (Dual)
├── mdn-moderation/   ◻ Skeleton — Staff tools (Paper)
└── mdn-sam/          ◻ Skeleton — "Steal a Mineling" gameplay (Paper)
```

---

## Architecture Decisions

### Why a monorepo?
- All 10 plugins share the same versioning, dependencies, and build tooling
- New developers clone one repo and can build everything immediately
- Cross-plugin refactors are atomic (one commit changes all affected plugins)

### Why Gradle Kotlin DSL?
- Type-safe build scripts — IDE autocompletion catches errors before they compile
- Better multi-project support than Groovy DSL
- Modern ecosystem integration (Shadow plugin, toolchains)

### Why `api()` vs `implementation()` in MDN-API?
- MDN-API exposes Jackson, HikariCP, Jedis, and SLF4J to consumer plugins via `api()`
- This means plugins don't need to declare these dependencies — they come transitively
- Result: less boilerplate in every plugin's build.gradle.kts

### Why Shadow/Shade instead of a shared lib?
- Each plugin JAR is **self-contained** — no classpath ordering issues
- No risk of plugin A loading a different Jackson version than plugin B
- Dependencies are relocated (`net.minedrop.libs.*`) to avoid conflicts

### Why Circuit Breakers?
- Redis or MySQL WILL go down in production. The question is whether the server stays up.
- Without circuit breakers: every failed call creates a new connection attempt, piling up threads
- With circuit breakers: after 5 failures, the circuit OPENS and calls are rejected instantly
- After 30 seconds, it goes HALF_OPEN (allows one probe). If it succeeds, back to CLOSED.

### Why API Versioning?
- MDN-SAM v2.0 might require a new API method added in MDN-API v1.5
- On startup, MDN-Bridge checks `requiredApiVersion` from plugin.yml
- If the currently loaded API is too old, the plugin is disabled with a clear error
- Prevents "mystery crashes" from version mismatches

### Why Correlation IDs?
- In a multi-server network, a player's journey crosses 3+ servers
- Without correlation IDs, you can't trace: "Why did this economy transaction fail?"
- Every packet gets a `correlationId` that flows through Redis, logs, and error messages
- Combined with `instanceId`, you know exactly which server touched the data

---

## Round 1 — Monorepo Foundation

**Commit**: `9522d09`  
**Date**: August 4, 2026  
**What was built**: The entire project skeleton from scratch

### MDN-API (Shared Library)
**19 source files + 3 test files**

| Package | Files | Purpose |
|---------|-------|---------|
| `net.minedrop.api` | `MDNAPI.java`, `ApiVersion.java` | Central singleton, version management |
| `net.minedrop.api.packet` | 8 files | Redis Pub/Sub packet types with Jackson polymorphic deserialization |
| `net.minedrop.api.events` | 4 files | Custom Bukkit events (StatueSteal, PlayerJoinSync, PlayerQuitSync, InventorySync) |
| `net.minedrop.api.security` | `SecurityUtil.java` | SHA-256, HMAC-SHA256, AES-256-GCM encryption |
| `net.minedrop.api.database` | `DatabaseSchema.java` | 8 CREATE TABLE statements, table name constants |

**Packet Types**:
- `AuthUpdatePacket` — 2FA completion signal
- `PlayerAlertPacket` — Notification/error popup
- `EconomySyncPacket` — Balance update broadcast
- `ModerationActionPacket` — Ban/mute/kick across network
- `ClanSyncPacket` — Clan roster/stats update
- `ServerHeartbeatPacket` — Server TPS/player metrics
- `PlayerSwitchServerPacket` — Player transfer signal
- `InventoryLockPacket` — Inventory lock during transfers

### MDN-Bridge (Security Foundation)
**6 source files + 2 configs**

| File | Purpose |
|------|---------|
| `BridgeManager.java` | Core: plugin registration, signature verification, handshake |
| `BridgeSecurityProvider.java` | API interface for other plugins |
| `BridgePaperPlugin.java` | Paper entry: config reading, retry buffer, localhost-only debug mode |
| `BridgeVelocityPlugin.java` | Velocity entry: config reading, handshake listener |

**Security Flow**:
1. Plugin calls `BridgeManager.register("MDN-Core", CorePaperPlugin.class)`
2. Bridge reads `signature.json` from the plugin JAR
3. Validates the internal `build_hash` matches actual JAR bytes (SHA-256)
4. Checks the hash against the allowed list in config.yml
5. If invalid → calls `PluginManager.disablePlugin()` + sends Discord webhook alert

**Handshake Flow**:
1. Paper server generates HMAC challenge via `SecurityUtil`
2. Sends challenge to Velocity via Redis channel `mdn:bridge:handshake`
3. Velocity computes HMAC response using shared secret key
4. Paper validates response — if invalid after 3 retries (3s spacing) → server shuts down

### MDN-Core (Network Heartbeat)
**13 source files + 1 test file + 2 configs**

| File | Purpose |
|------|---------|
| `MDNCore.java` | Constants: Redis channels, key prefixes, permissions |
| `DatabaseManager.java` | HikariCP pool, schema initialization, health checks |
| `RedisManager.java` | Jedis pool, Pub/Sub with cancellable subscriptions, CRUD |
| `PlayerCache.java` | Redis-backed cache with TTL eviction (10 min) |
| `ServerRegistry.java` | Server discovery, heartbeat timeout eviction (45s), health scoring |
| `SessionManager.java` | Player session tracking, transfers, reconnect handling |
| `DataSyncEngine.java` | Async profile saves, state locking, crash recovery buffer |
| `InventorySyncManager.java` | Inventory/enderchest serialization and cross-server sync |
| `PacketDispatcher.java` | Routes incoming Redis packets to registered handlers |
| `CircuitBreaker.java` | Resilience pattern: 5-failure threshold, 30s cooldown |
| `CorePaperPlugin.java` | Paper entry: startup sequence, commands, heartbeat, health checks |
| `CoreVelocityPlugin.java` | Velocity entry: routing, commands, server discovery, heartbeat listener |

**Startup Sequence (Paper)**:
1. `onLoad()` — Plugin loads
2. `onEnable()`:
   - Log API version
   - Validate config (required fields present)
   - Connect MySQL → create tables
   - Connect Redis
   - Run health checks (SELECT 1, PING)
   - Initialize circuit breakers
   - Initialize MDN-API (makes DB/Redis available to all plugins)
   - Initialize PlayerCache, DataSyncEngine, PacketDispatcher
   - Start periodic save task (every 5 min)
   - Start heartbeat task (every 5 seconds)
   - Register Bukkit events
   - Subscribe to Redis packet bus
3. `onDisable()`:
   - Cancel heartbeat task
   - Flush all pending saves (saveAll)
   - Shut down PlayerCache (clear + evict)
   - MDNAPI.shutdown() (close DB + Redis pools)
   - DatabaseManager.shutdown()

---

## Round 2 — Production Hardening

**Commit**: Part of `9522d09`  
**What was fixed**: 24 critical bugs and missing features

### MDN-API Fixes
- ✅ Added `shutdown()` + `isInitialized()` — clean lifecycle management
- ✅ Added `createStandalone()` — dev/testing mode without live DB/Redis
- ✅ ObjectMapper configured with `JavaTimeModule`, `FAIL_ON_UNKNOWN_PROPERTIES=false`
- ✅ 3 new packet types (ServerHeartbeat, PlayerSwitchServer, InventoryLock)
- ✅ 3 new events (PlayerJoinSync, PlayerQuitSync, InventorySync)
- ✅ All 8 packet types registered in `@JsonSubTypes` for auto-dispatch

### MDN-Bridge Fixes
- ✅ `computeJarHash()` now reads actual JAR file bytes (was hashing the file path!)
- ✅ `signature.json` now parsed — validates internal `build_hash` field
- ✅ `getInstance()` — double-checked locking for thread safety
- ✅ Handshake retry buffer — 3 retries × 3-second spacing
- ✅ Verification failure now calls `PluginManager.disablePlugin()`
- ✅ Debug mode restricted to localhost (`127.0.0.1` or `0.0.0.0`)
- ✅ Discord webhook alert on security failures
- ✅ Velocity config reading via `plugins/mdn-bridge/config.yml`

### MDN-Core Fixes
- ✅ ServerRegistry: 45-second heartbeat timeout, dead servers evicted every 15s
- ✅ Health scoring: TPS + player load + staleness for routing decisions
- ✅ PlayerCache: 10-minute TTL, scheduled eviction every 5 minutes
- ✅ RedisManager: cancellable subscriptions with `unsubscribe()` on shutdown
- ✅ `saveAll()`: now iterates all pending saves and flushes to MySQL
- ✅ EnderChest data now saved (was silently ignored)
- ✅ Crash recovery buffer: JSON dump to `plugins/MDN-Core/emergencies/` on save failure
- ✅ PacketDispatcher: routes incoming packets to registered handlers (was just logged)
- ✅ Velocity config: reads `plugins/mdn-core/config.yml` (was all hardcoded)
- ✅ Standard commands: `/website`, `/store`, `/vote`, `/discord`, `/help`, `/rules`, `/spawn`
- ✅ `/mdn health` command: full report (DB, Redis, TPS, players, memory, circuits)

---

## Round 3 — Resilience & Observability

**Commit**: `1687d56`  
**What was added**: 5 major enhancements, 24 unit tests

### 1. Unit Tests (24/24 passing)
| Test Class | Tests | What It Covers |
|------------|-------|---------------|
| `SecurityUtilTest` | 8 | SHA-256, HMAC-SHA256, AES/GCM encrypt-decrypt roundtrip, tampering detection |
| `ApiVersionTest` | 9 | Parsing, format validation, compatibility checks, comparison, CURRENT |
| `CircuitBreakerTest` | 7 | Open/close transitions, manual reset, counter reset, reject on open |

### 2. Circuit Breaker (`CircuitBreaker.java`)
- **Settings**: 5 consecutive failures → OPEN, 30-second cooldown → HALF_OPEN
- **States**: CLOSED (normal) → OPEN (reject all) → HALF_OPEN (allow probe)
- **Wired into**: `CorePaperPlugin` — `dbCircuitBreaker` and `redisCircuitBreaker`
- **Heartbeat uses it**: `redisCircuitBreaker.executeVoid(() -> redisManager.publishPacket(...))`
- **Health command shows it**: `/mdn health` displays circuit breaker states

### 3. API Versioning (`ApiVersion.java`)
- **Format**: `MAJOR.MINOR.PATCH` (semantic versioning)
- **Current**: `1.0.0`
- **Compatibility**: Same MAJOR + ≥ MINOR = compatible
- **Bridge check**: On `register()`, verifies `requiredApiVersion` against `ApiVersion.CURRENT`
- **Log**: Every startup logs `MDN-API version: 1.0.0`

### 4. Config Validation
- **Startup check**: `database.host` and `database.database` must be set
- **Health probes**: `SELECT 1` for MySQL, `PING` for Redis
- **Fail-fast**: Clear error messages in console, plugin disabled if config is invalid
- **Degraded mode allowed**: If health probes fail, server still starts (warns, doesn't die)

### 5. Correlation IDs
- Every `MDNPacket` gets a `correlationId` = `instanceId-timestamp`
- `MDNAPI.getInstance().getInstanceId()` returns 8-char unique server identifier
- Logged on player join: `[corr=a1b2c3d4] Player joined: Steve`
- Flows through all Redis packets for cross-server tracing

---

## Round 5 — Velocity Config Bootstrap Fix

**Commit**: `81da386`  
**Date**: August 4, 2026 (night)  
**What was fixed**: Velocity plugins never created data directories or copied default configs

### The Problem
Velocity doesn't have Paper's `saveDefaultConfig()` API. Both `BridgeVelocityPlugin` and
`CoreVelocityPlugin` were reading config from `plugins/mdn-*/config.yml` but never creating
the directories or copying defaults. Result: `"No config.yml found — using defaults."` on
every startup, no config folder under the proxy plugins directory.

### The Fix
Added `saveDefaultConfig()` methods to both Velocity plugins:
- **CoreVelocityPlugin**: Creates `plugins/mdn-core/`, copies `config-velocity.yml` from JAR → `config.yml`
- **BridgeVelocityPlugin**: Creates `plugins/mdn-bridge/`, copies `config-velocity.yml` from JAR → `config.yml`
- **Loader updated**: Now reads both `network.default-region` (Paper layout) and `routing.default-region` (Velocity layout)
- **Unused imports**: Cleaned `PluginManager` (Bridge) and `CommandMeta` (Core)

### Config Source Matrix
| Plugin | JAR Resource | Copies To | Content |
|--------|-------------|-----------|---------|
| mdn-bridge | `config-velocity.yml` | `plugins/mdn-bridge/config.yml` | Bridge settings (no DB) |
| mdn-core | `config-velocity.yml` | `plugins/mdn-core/config.yml` | Redis + routing (no DB) |

---

## Round 8 — MDN-Auth Gap Fixes & Production Hardening

**Commit**: `afda633`  
**Date**: August 11, 2026  
**What was fixed**: All 7 spec comparison gaps (A-1 through A-7) + production enhancements

### Gap Fix Summary

| Gap | Fix | Key Methods |
|-----|-----|-------------|
| A-1 MySQL persistence | Dual-write to `mdn_auth_totp` table + Redis cache | `generateSecret()`, `getRecord()`, `saveRecord()` |
| A-2 IP lock enforcement | IP prefix comparison with rate limiting | `verifyCodeWithIpLock()`, `IpVerifyResult` enum |
| A-3 Full /2fa reset | ProxyServer API + Redis username→UUID mapping | `resolveUsername()`, `recordUsernameMapping()` |
| A-4 SHADOW_BAN | KICK→SHADOW_BAN conversion, Redis set tracking | `shadowBan()`, `isShadowBanned()` |
| A-5 Backup codes | `/2fa verify-backup <code>` command | `verifyBackupCode()`, `handleVerifyBackup()` |
| A-6 Alt list TTL | 24h expire on IP+FP keys, scheduled cleanup | `expire()`, `startCleanupTask()` |
| A-7 PreLoginEvent | Real UUID via Redis username lookup | Uses `event.getUsername()` + `resolveUsername()` |

### Production Enhancements (Beyond Spec)
- **Rate limiting**: 5 failed 2FA attempts → 15-minute lockout per UUID
- **Username→UUID mapping**: Redis `mdn:auth:username:<name>` with 30-day TTL
- **4 new Redis operations**: `expire`, `sadd`, `sismember`, `scard` in RedisManager
- **Scheduled cleanup**: Daemon thread every 6h, proper shutdown in onProxyShutdown
- **Graceful degradation**: TotpManager works with or without MySQL (null-safe dataSource)

### Live Verification (Velocity 4.1.0)
```
Plugin 'MDN-Auth' fully verified — signature valid, hash matches.
MDN-Auth enabled.
  Alt limits: 3 per IP, 2 per fingerprint (action: KICK)
  Staff 2FA: enabled (2 permission groups, ip-lock: on)
  Commands: /2fa (setup|verify|verify-backup|reset), /auth (unblock)
  Alt list cleanup task scheduled (every 6h)
```

### Files Changed (7 files, +638/-94)
- `TotpManager.java`: +256 lines (MySQL, IP lock, backup codes, rate limit)
- `AltDetector.java`: +44 lines (SHADOW_BAN, TTL cleanup)
- `AuthManager.java`: +108 lines (constructor, passthrough methods)
- `AuthVelocityPlugin.java`: +101 lines (SHADOW_BAN, cleanup task, PreLogin)
- `TwoFactorCommand.java`: +158 lines (reset, backup verify, IP lock UX)
- `RedisManager.java`: +48 lines (expire, sadd, sismember, scard)
- `ISSUES.md`: updated (A-1 to A-7 all fixed)

---

## Round 7 — MDN-Auth Plugin #4 Implementation

**Commit**: `2a4a469`  
**Date**: August 6, 2026  
**What was built**: Complete Velocity authentication plugin — plugin #4 of 10

### Architecture
```
AuthVelocityPlugin (main entry point)
├── AuthManager (coordinator)
│   ├── TotpManager — RFC 6238 TOTP, QR URLs, backup codes, ±1 step drift
│   ├── DeviceFingerprinter — SHA-256 from client metadata (no UUID in hash)
│   └── AltDetector — Redis IP/fingerprint tracking + whitelist
├── TwoFactorCommand — /2fa setup|verify|reset
└── AuthCommand — /auth unblock <ip>
```

### Features Implemented
| Feature | Implementation |
|---------|---------------|
| **Staff 2FA** | TOTP with QR codes, 8 backup codes, ±30s drift buffer |
| **Alt detection** | IP + fingerprint tracking, max-accounts limits, whitelist |
| **Device fingerprint** | SHA-256: client brand + protocol + IP prefix (no UUID) |
| **Pre-auth lockdown** | Title overlay, persistent action bar, limbo state on proxy |
| **Config** | SnakeYAML, saveDefaultConfig, matches design spec exactly |
| **Bridge registration** | Self-registers for signature verification |
| **Signature** | auto-generated via sorted-entry hash + Python ZIP_STORED injection |

### Spec Comparison Results
Compared implementation against `plan/MineDrop/plugins/03_MDN_Auth.md`:
- ✅ Velocity-only plugin
- ✅ Device fingerprinting (SHA-256)
- ✅ Staff 2FA (TOTP with QR codes, backup codes)
- ✅ Alt detection (IP + fingerprint, ALLOW/KICK/ALERT)
- ✅ Config matches spec exactly (all fields identical)
- ✅ 4 commands with correct permissions
- ✅ Pre-auth lockdown (title overlay, action bar)
- ✅ TOTP time drift buffer (±1 step)
- ❌ Database schema — Redis-only, no MySQL `mdn_auth_totp` table
- ⚠️ IP lock enforcement — parsed but never checked
- ⚠️ `/2fa reset` — stub (needs DB-backed UUID lookup)
- ⚠️ SHADOW_BAN — enum exists, never used
- ❌ Backup code verification — no `/2fa verify-backup` command

**Gaps documented**: ISSUES.md A-1 through A-7 — planned for next session

### Build Quality
- JAR: 4.0 MB, 2,400+ entries, shadow-relocated dependencies
- velocity-plugin.json: 1 entry (annotation-generated, no duplicate)
- signature.json: auto-generated with sorted-entry hash
- No bridge/core class leaks
- All 6 skeleton plugin build fixes applied (version in expand, deleted duplicate velocity-plugin.json)

---

## Round 6 — Cross-Server Handshake & Signature Verification

**Commit**: `ed69f5d`  
**Date**: August 5, 2026  
**What was built**: End-to-end Redis-based handshake + build-time signature generation

### Cross-Server Handshake
- **Flow**: Paper publishes challenge → Redis `mdn:bridge:handshake` → Velocity computes HMAC → publishes response to `mdn:bridge:handshake:response` → Paper validates
- **HandshakeTransport interface**: Avoids circular dependency (mdn-bridge can't import mdn-core's RedisManager)
- **Timing solved**: Bridge defers handshake until Core injects transport, then triggers via `triggerHandshake()` / `triggerHandshakeListener()`
- **ClassLoader fix**: mdn-core shadowJar excludes `net/minedrop/bridge/**` — prevents `ClassCastException: BridgePaperPlugin cannot be cast to BridgePaperPlugin`
- **Tested**: Paper 26.2 + Velocity 4.1.0 — handshake VERIFIED end-to-end

### Build-Time signature.json
- **Gradle task**: `generateSignature` runs after `jar`, computes SHA-256 of ZIP entries (skipping signature.json), writes JSON
- **Runtime mirror**: `BridgeManager.computeJarHash()` iterates ZIP entries the same way — hashes match exactly
- **Output**: `{"plugin_id":"mdn-bridge","version":"1.0.0-SNAPSHOT","build_hash":"f2b389f1..."}`

---

## Round 9 — Signature Auto-Gen + Eviction Fix (August 6, 2026)

**What was changed**:

### signature.json auto-generation for both plugins
- **mdn-bridge** and **mdn-core**: Added `tasks.shadowJar { finalizedBy(generateSignature) }`
  — running `shadowJar` always injects `signature.json`. No need to run `build` separately.
- **mdn-core generateSignature**: Already had the Gradle task; just needed the `finalizedBy` link.
- **Build output**: `[signature] mdn-bridge: <hash>`, `[signature] mdn-core: <hash>`

### Velocity allowed-build-hashes support
- **BridgeVelocityPlugin**: Now reads `allowed-build-hashes` from config (previously only BridgePaperPlugin did).
  Both sides validate signature hashes. Added `List` import.
- **config-velocity.yml**: Added `allowed-build-hashes` field to default template.
- **Duplicate register removed**: BridgeVelocityPlugin accidentally called `register()` twice — fixed.

### Server eviction fix
- **CoreVelocityPlugin.discoverServers()**: No longer pre-registers servers via `serverRegistry.registerServer()`.
  Instead logs `"N configured server(s) found — awaiting heartbeats"`.
  Servers self-register when their first heartbeat arrives via Redis.
  This eliminates the `"Server EVICTED: lobby (no heartbeat for 45s)"` warning before
  the Paper server finishes booting.

### Startup script rewrite
- **server/startup.sh**: Rewritten with `setsid` + Java 25 absolute path (server JARs require Java 25),
  Java 21 for Gradle (Gradle 8.10 doesn't support Java 25 as runtime),
  wait-for-boot loops with timeout warnings, log capture to `/tmp/`, signal trap for cleanup.

### Verified
- Both servers running: Velocity PID 22961, Lobby PID 23026
- Signature verified on both sides: `"Plugin 'MDN-Bridge' fully verified — signature valid, hash matches."`
- mdn-core also now has signature.json: `"Plugin 'MDN-Core' fully verified"`
- Handshake: SUCCESS on attempt 1
- No eviction warnings

---

## Round 4 — Dead Letter Queue & Operation Timeouts

**Commit**: *(current working state)*

### Dead Letter Queue (`DeadLetterQueue.java`)
- **Retry strategy**: 5 retries with exponential backoff (1s → 2s → 4s → 8s → 16s)
- **Permanent DLQ**: After 5 failures, packet moves to `mdn:dead_letter:permanent` for staff inspection
- **Wired into PacketDispatcher**: If a handler throws, the raw JSON is auto-enqueued
- **Visible in /mdn health**: Shows pending + permanent DLQ counts

### Operation Timeouts
- `DatabaseManager.executeWithTimeout(seconds, operation)` — wraps any SQL operation with a hard timeout
- `RedisManager.executeWithTimeout(seconds, operation)` — same for Redis
- `isConnected()` methods now use `CompletableFuture.get(10/5, SECONDS)` to prevent hanging
- If a timeout fires, the operation returns null and logs an error — no thread leaks

---

## File Map — Every File Explained

### `mdn-api/` — 22 files

| File | Lines | Purpose |
|------|-------|---------|
| `build.gradle.kts` | 30 | Dependencies: Paper API, Jackson, HikariCP, Jedis, SLF4J |
| `MDNAPI.java` | 170 | Singleton, lifecycle, ObjectMapper config, instance ID |
| `ApiVersion.java` | 110 | Semantic version for plugin compatibility checks |
| `packet/MDNPacket.java` | 75 | Base packet with Jackson `@JsonTypeInfo` + `@JsonSubTypes` |
| `packet/AuthUpdatePacket.java` | 35 | 2FA completion signal |
| `packet/PlayerAlertPacket.java` | 40 | Notification/error popup |
| `packet/EconomySyncPacket.java` | 35 | Balance update broadcast |
| `packet/ModerationActionPacket.java` | 45 | Ban/mute/kick across network |
| `packet/ClanSyncPacket.java` | 45 | Clan roster/stats update |
| `packet/ServerHeartbeatPacket.java` | 40 | Server TPS/player metrics |
| `packet/PlayerSwitchServerPacket.java` | 35 | Player transfer signal |
| `packet/InventoryLockPacket.java` | 40 | Inventory lock during transfers |
| `events/StatueStealEvent.java` | 40 | Fired on successful statue theft |
| `events/PlayerJoinSyncEvent.java` | 35 | Player data loaded on new server |
| `events/PlayerQuitSyncEvent.java` | 35 | Player disconnecting, data flushing |
| `events/InventorySyncEvent.java` | 30 | Inventory synced across servers |
| `security/SecurityUtil.java` | 110 | SHA-256, HMAC-SHA256, AES-256-GCM |
| `database/DatabaseSchema.java` | 130 | 8 CREATE TABLE DDL statements |
| `test/ApiVersionTest.java` | 85 | 9 tests |
| `test/security/SecurityUtilTest.java` | 85 | 8 tests |

### `mdn-bridge/` — 8 files

| File | Lines | Purpose |
|------|-------|---------|
| `build.gradle.kts` | 45 | Shadow plugin, dual API dependencies |
| `BridgeManager.java` | 270 | Plugin registration, signature verification, handshake, API version check |
| `api/BridgeSecurityProvider.java` | 20 | Public API interface |
| `paper/BridgePaperPlugin.java` | 150 | Paper entry: config, retry buffer, plugin disabling, localhost-only debug |
| `velocity/BridgeVelocityPlugin.java` | 95 | Velocity entry: config, handshake listener |
| `resources/plugin.yml` | 8 | Paper plugin metadata |
| `resources/velocity-plugin.json` | *REMOVED* | Now auto-generated by Velocity @Plugin annotation processor |
| `resources/config.yml` | 30 | Bridge Paper config — allowed hashes, secret key, timeout |
| `resources/config-velocity.yml` | 15 | Bridge Velocity config — no DB, Velocity-specific defaults |

### `mdn-core/` — 16 files

| File | Lines | Purpose |
|------|-------|---------|
| `build.gradle.kts` | 60 | Shadow plugin, dual API deps, dependsOn mdn-bridge |
| `MDNCore.java` | 40 | Redis channels, key prefixes, permissions |
| `database/DatabaseManager.java` | 100 | HikariCP pool, schema init, connectivity check |
| `redis/RedisManager.java` | 140 | Jedis pool, Pub/Sub, cancellable subscriptions |
| `cache/PlayerCache.java` | 180 | Redis-backed cache, TTL eviction |
| `registry/ServerRegistry.java` | 180 | Server tracking, heartbeat timeout, health scoring |
| `session/SessionManager.java` | 110 | Player sessions, transfers |
| `sync/DataSyncEngine.java` | 140 | Async saves, state locking, crash buffer |
| `sync/InventorySyncManager.java` | 95 | Inventory base64 serialization, MySQL CRUD |
| `packet/PacketDispatcher.java` | 90 | Routes incoming Redis JSON to handlers |
| `util/CircuitBreaker.java` | 130 | OPEN/CLOSED/HALF_OPEN resilience pattern |
| `paper/CorePaperPlugin.java` | 330 | Paper entry: init sequence, commands, heartbeat, health |
| `velocity/CoreVelocityPlugin.java` | 260 | Velocity entry: routing, commands, server discovery |
| `resources/plugin.yml` | 8 | Paper plugin metadata |
| `resources/config.yml` | 40 | Paper config — database, Redis, network, routing settings |
| `resources/config-velocity.yml` | 35 | Velocity config — Redis + routing only, no database |
| `test/CircuitBreakerTest.java` | 90 | 7 tests |

---

## How to Build

### Prerequisites
- **Java 21** (JDK)
- The Gradle wrapper is included — no Gradle install needed

### Quick Build
```bash
# Build all 3 main plugins
JAVA_HOME=/usr/lib/jvm/java-1.21.0-openjdk-amd64 ./gradlew :mdn-api:build :mdn-bridge:build :mdn-core:build

# Run all tests
JAVA_HOME=/usr/lib/jvm/java-1.21.0-openjdk-amd64 ./gradlew test

# Build a specific plugin
JAVA_HOME=/usr/lib/jvm/java-1.21.0-openjdk-amd64 ./gradlew :mdn-sam:build
```

### Output
Plugin JARs are in each module's `build/libs/`:
- `mdn-api/build/libs/mdn-api-1.0.0-SNAPSHOT.jar` — Library (not a plugin, shaded into others)
- `mdn-bridge/build/libs/mdn-bridge-1.0.0-SNAPSHOT.jar` — Security plugin (fat JAR)
- `mdn-core/build/libs/mdn-core-1.0.0-SNAPSHOT.jar` — Core plugin (fat JAR)

### IDE Setup
1. Clone the repo
2. Open in IntelliJ IDEA — it detects the Gradle project automatically
3. Set Project SDK to Java 21
4. Run `./gradlew build` from the terminal or use the Gradle tool window

---

## How to Add a New Plugin

1. **Create the directory**: `mkdir mdn-myplugin`
2. **Create these files**:
   - `build.gradle.kts` — copy from `mdn-auth/build.gradle.kts`, adjust dependencies
   - `src/main/resources/plugin.yml` — Paper metadata
   - `src/main/java/net/minedrop/myplugin/MyPlugin.java` — Main class
3. **Register in settings.gradle.kts**: Add `include("mdn-myplugin")`
4. **Build**: `./gradlew :mdn-myplugin:build`

The main class should follow this pattern:
```java
public class MyPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        // Access shared services via MDNAPI
        MDNAPI api = MDNAPI.getInstance();
        HikariDataSource ds = api.getDataSource();
        JedisPool redis = api.getJedisPool();
        // Your logic here
    }
}
```

---

## How to Add a New Feature

### Adding to an existing plugin
1. Read the TODO checklist in the main class Javadoc
2. Read the design spec in `plan/MineDrop/plugins/0X_MDN_*.md`
3. Write your code — follow the same package structure
4. Write tests in `src/test/java/`
5. Run `./gradlew :mdn-xxx:build` to verify

### Adding a new packet type
1. Create `MyNewPacket.java` in `mdn-api/src/main/java/net/minedrop/api/packet/`
2. Extend `MDNPacket`, add `@JsonCreator` constructor with `@JsonProperty` fields
3. Add `@JsonSubTypes.Type` entry in `MDNPacket.java`
4. Build: `./gradlew :mdn-api:build`

### Adding a new event
1. Create the event class in `mdn-api/src/main/java/net/minedrop/api/events/`
2. Extend `org.bukkit.event.Event`, implement `HandlerList` pattern
3. Fire it from your plugin code
4. Listen for it in other plugins

### Registering a packet handler
```java
// In your plugin's onEnable():
CorePaperPlugin core = (CorePaperPlugin) getServer().getPluginManager().getPlugin("MDN-Core");
core.getPacketDispatcher().registerHandler("ECONOMY_SYNC", packet -> {
    EconomySyncPacket econ = (EconomySyncPacket) packet;
    // Update local balance cache
});
```

---

## Test Coverage

```
Tests: 24 total, 0 failures

SecurityUtilTest (mdn-api) — 8 tests:
  ✓ sha256HexConsistency
  ✓ sha256HexDifferentInputs
  ✓ hmacSha256Consistency
  ✓ hmacSha256DifferentSecrets
  ✓ aesRoundtrip
  ✓ aesWrongPassword
  ✓ aesTamperedCiphertext
  ✓ aesEmptyString

ApiVersionTest (mdn-api) — 9 tests:
  ✓ parseFull (1.2.3)
  ✓ parseNoPatch (1.0)
  ✓ compatibleHigherMinor
  ✓ compatibleSameMinor
  ✓ incompatibleDifferentMajor
  ✓ incompatibleLowerMinor
  ✓ compareTo ordering
  ✓ invalidFormat throws
  ✓ currentVersionExists

CircuitBreakerTest (mdn-core) — 7 tests:
  ✓ successKeepsClosed
  ✓ failuresOpenCircuit
  ✓ openCircuitRejects
  ✓ manualReset
  ✓ successResetsCounter
  ✓ executeVoidOpen
  ✓ executeVoidSuccess
```

---

## Known Gaps & Future Work

### Things explicitly left as stubs (by design)
- `BridgePaperPlugin.performHandshake()` — **DONE (Round 6)**: Now publishes challenge to Redis, subscribes to response channel, validates Velocity HMAC. Tested end-to-end on live servers.
- `DataSyncEngine.saveAll()` — Flushes pending saves but doesn't iterate all active server sessions. Full implementation requires tracking all online player UUIDs.
- `BridgeVelocityPlugin` — **DONE (Round 6)**: Now subscribes to `mdn:bridge:handshake`, computes HMAC, publishes response. Triggered after transport injected.
- `PacketDispatcher` — Only dispatches to registered handlers. No built-in handlers registered yet (that's for each plugin to do).
- `InventorySyncManager` — Stores combined inv+ec as JSON. A proper implementation would store them as separate columns or use Bukkit's native serialization.

### MDN-Auth gaps (found in spec comparison — Round 7)
- `AuthManager.validateServiceSecret()` — stub, documented TODO for private lobby system (MDN-SAM)
- `TotpManager` — Stores records in Redis only (no MySQL `mdn_auth_totp` table per design spec)
- `AltDetector` — IP/fingerprint lists grow indefinitely (no TTL cleanup)
- `TwoFactorCommand.handleReset()` — stub (cannot resolve username→UUID without database)
- IP lock (`enforce-ip-lock`) — parsed from config but never checked on 2FA verify
- SHADOW_BAN action — enum value exists but never used in any code path
- No `/2fa verify-backup <code>` command — backup codes generated but unusable

### Future enhancements (not yet implemented)
- Rate limiter per IP/player on packet publishing
- Database migration framework (auto-run schema changes)
- Prometheus/Grafana metrics export
- Developer debug kit (`/mdn debug packets`, etc.)

---

## Conventions & Style Guide

### Package naming
- All plugins: `net.minedrop.<plugin>`
- API: `net.minedrop.api`
- Bridge: `net.minedrop.bridge`
- Core: `net.minedrop.core`

### File naming
- Main plugin class: `<Name>PaperPlugin.java` or `<Name>VelocityPlugin.java`
- Managers/services end in `Manager` or `Engine`: `DatabaseManager`, `DataSyncEngine`
- Utilities end in `Util`: `SecurityUtil`

### Dependency injection
- No DI framework (Guice is used by Velocity internally, but we don't use it in our code)
- Services are accessed through `MDNAPI.getInstance()` or the main plugin class getters
- This is intentionally simple — new developers don't need to learn DI

### Async operations
- ALL database operations are async via `CompletableFuture.runAsync()`
- ALL Redis Pub/Sub runs on separate threads via `subscriberThreads`
- NEVER block the main server thread with I/O

### Error handling
- Circuit breakers wrap all external service calls
- Failed DB saves go to crash buffer JSON files
- Failed Redis operations are logged and the circuit breaker tracks them
- Never throw from an event handler — catch and log

### Logging
- Use SLF4J (`LoggerFactory.getLogger()`)
- Include correlation IDs: `[corr=abc123] Message`
- Log at appropriate levels: `info` for lifecycle, `warn` for recoverable issues, `error` for failures

---

## Changelog

| Date | Commit | What |
|------|--------|------|
| 2026-08-04 | `9522d09` | Monorepo foundation — MDN-API, MDN-Bridge, MDN-Core fully implemented + 7 skeletons |
| 2026-08-04 | `1687d56` | Production hardening — 24 bug fixes, circuit breakers, API versioning, correlation IDs, config validation, 24 unit tests |
| 2026-08-04 | `61c063a` | Dead Letter Queue + Operation Timeouts + DIARY.md, STEPS.md, SUGGEST.md, TIMELINE.md |
| 2026-08-04 | `847745f` | Startup lifecycle fixes — PacketDispatcher NPE, MDNAPI init, Bridge Jackson decoupling, Redis executor, onDisable shutdown |
| 2026-08-04 | `dddaa76` | Redis connection reset fix (JedisPoolConfig validation) + Bridge shadow JAR build fix (jar classifier + explicit Jackson deps) |
| 2026-08-04 | `ae71f4f` | Duplicate velocity-plugin.json deduplication — removed manual templates, annotation processor now sole source of truth |
| 2026-08-04 | `81da386` | Velocity config bootstrap — saveDefaultConfig() for both plugins, config-velocity.yml now copies to disk, routing.default-region path support |
| 2026-08-05 | `ed69f5d` | Cross-server handshake via Redis Pub/Sub + build-time signature.json generation + ClassLoader conflict fix |
| 2026-08-05 | `de86137` | Handshake race fix (2s delay → SUCCESS on attempt 1) + signature hash fix (sorted entries + Python injection) + MDN-Core self-registration |
| 2026-08-06 | `2a4a469` | MDN-Auth plugin #4 — TOTP 2FA, alt detection, device fingerprinting, pre-auth lockdown |
| 2026-08-06 | *(pending)* | Signature auto-gen finalizedBy link for both bridge + core, Velocity allowed-build-hashes support, server eviction fix |
| 2026-08-11 | `afda633` | MDN-Auth gap fixes — all 7 gaps (A-1 to A-7) + rate limiting + username→UUID mapping + backup codes + scheduled cleanup |

---

*Built with ❤️ by the MineDrop Network Team*  
*"Almost perfect — because nothing is perfect."*
