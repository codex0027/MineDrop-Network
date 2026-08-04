# MineDrop Network — Development Diary

> **Last Updated**: August 4, 2026 (night)  
> **Build Status**: ✅ All 3 plugins compiling, 24/24 tests passing  
> **Branch**: `main` | **Commit**: `ae71f4f`

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
8. [File Map — Every File Explained](#file-map--every-file-explained)
9. [How to Build](#how-to-build)
10. [How to Add a New Plugin](#how-to-add-a-new-plugin)
11. [How to Add a New Feature](#how-to-add-a-new-feature)
12. [Test Coverage](#test-coverage)
13. [Known Gaps & Future Work](#known-gaps--future-work)
14. [Conventions & Style Guide](#conventions--style-guide)

---

## What We're Building

**MineDrop Network** is a Minecraft minigames network running on **Velocity** (proxy) and **Paper** (game servers). The flagship game is **"Steal a Mineling" (SAM)** — a conveyor-belt base-defence PvPvE game never seen before in Minecraft.

The codebase is a **Gradle monorepo** containing 10 plugins. Three are fully implemented with production-grade code, and seven are skeletons waiting for new developers.

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
├── mdn-auth/         ◻ Skeleton — Authentication & 2FA (Velocity)
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
| `resources/config.yml` | 30 | Allowed hashes, secret key, timeout, failure action, webhook |

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
| `resources/config.yml` | 40 | Database, Redis, network, routing settings |
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
- `BridgePaperPlugin.performHandshake()` — Currently validates HMAC locally. In production, it should publish to Redis and await Velocity's response asynchronously.
- `DataSyncEngine.saveAll()` — Flushes pending saves but doesn't iterate all active server sessions. Full implementation requires tracking all online player UUIDs.
- `BridgeVelocityPlugin` — Doesn't subscribe to `mdn:bridge:handshake` Redis channel yet. The handshake is self-validated on the Paper side.
- `PacketDispatcher` — Only dispatches to registered handlers. No built-in handlers registered yet (that's for each plugin to do).
- `InventorySyncManager` — Stores combined inv+ec as JSON. A proper implementation would store them as separate columns or use Bukkit's native serialization.

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

---

*Built with ❤️ by the MineDrop Network Team*  
*"Almost perfect — because nothing is perfect."*
