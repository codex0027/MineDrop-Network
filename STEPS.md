# MineDrop Network — Step-by-Step Development Log

> **Purpose**: Every single change — no matter how small — is logged here chronologically.  
> **For**: New developers onboarding, debugging "why was this done this way", and auditing changes.  
> **Last Updated**: August 4, 2026

---

## Phase 0 — Project Analysis & Planning

### Step 0.1 — Read all design documents
- Read `plan/MineDrop/MINEDROP - A MINIGAMES SERVER...md` — Original game concept
- Read `plan/MineDrop/plugins-roadmap.md` — Consolidation plan (18→10 plugins)
- Read `plan/MineDrop/Plugin-making ranking.md` — Build priority & phases
- Read `plan/MineDrop/SAB_Plugin_Design_Review.md` — Detailed game mechanics
- Read `plan/MineDrop/Updated Core System - Velocity & Paper.md` — Original specs
- Read all `plan/MineDrop/plugins/00-10_MDN_*.md` — Individual plugin specs

### Step 0.2 — Architecture decisions made
- **Decision**: Gradle Kotlin DSL monorepo (not Maven, not separate repos)
- **Decision**: Java 21 target (not 25 — too bleeding edge for Paper ecosystem)
- **Decision**: Shadow/Shade for fat JARs (not shared lib install)
- **Decision**: Full structure skeletons for remaining 7 plugins (not minimal)
- **Decision**: Dual-platform plugins use single module with compileOnly for both APIs

---

## Phase 1 — Monorepo Foundation (Commit: `9522d09`)

### Step 1.1 — Root build system
- Created `settings.gradle.kts` — includes all 10 subprojects
- Created `gradle.properties` — version catalog (Paper 1.21.1, Velocity 3.3.0, Java 21)
- Created `build.gradle.kts` (root) — repos, group, version, common Java config
- Created `README.md` — onboarding guide with architecture diagram and build instructions
- Created `.gitignore` — excludes `.gradle/`, `build/`, IDE files, secrets

### Step 1.2 — MDN-API module (Shared Library)

**Files created**:
| # | File | Purpose |
|---|------|---------|
| 1 | `mdn-api/build.gradle.kts` | Dependencies: Paper API, Jackson, HikariCP, Jedis, SLF4J |
| 2 | `MDNAPI.java` | Singleton with initialize(), getInstance(), getObjectMapper(), getDataSource(), getJedisPool() |
| 3 | `packet/MDNPacket.java` | Base packet with @JsonTypeInfo, @JsonSubTypes, serialize(), deserialize() |
| 4 | `packet/AuthUpdatePacket.java` | 2FA completion signal: uuid + status |
| 5 | `packet/PlayerAlertPacket.java` | Notification: uuid + message + AlertType enum |
| 6 | `packet/EconomySyncPacket.java` | Balance update: uuid + newBalance |
| 7 | `packet/ModerationActionPacket.java` | Ban/mute/kick: target + ActionType enum + expiry |
| 8 | `packet/ClanSyncPacket.java` | Clan roster: clanId + ClanAction enum + player |
| 9 | `events/StatueStealEvent.java` | Bukkit event: thief + victimUuid + statueRarity + value |
| 10 | `security/SecurityUtil.java` | sha256Hex(), hmacSha256(), encryptAes(), decryptAes() |
| 11 | `database/DatabaseSchema.java` | 8 CREATE TABLE statements, table name constants |

### Step 1.3 — MDN-Bridge module (Security Foundation)

**Files created**:
| # | File | Purpose |
|---|------|---------|
| 12 | `mdn-bridge/build.gradle.kts` | Shadow plugin, dual API deps, processResources with expand |
| 13 | `bridge/BridgeManager.java` | Plugin registration, signature verification, handshake challenge |
| 14 | `api/BridgeSecurityProvider.java` | Public API interface |
| 15 | `paper/BridgePaperPlugin.java` | Paper entry: onLoad, onEnable, handshake with validation |
| 16 | `velocity/BridgeVelocityPlugin.java` | Velocity entry: ProxyInitializeEvent handler |
| 17 | `resources/plugin.yml` | Paper metadata template |
| 18 | `resources/velocity-plugin.json` | Velocity metadata template |
| 19 | `resources/config.yml` | Allowed hashes, secret key, timeout, failure action |

### Step 1.4 — MDN-Core module (Network Heartbeat)

**Files created**:
| # | File | Purpose |
|---|------|---------|
| 20 | `mdn-core/build.gradle.kts` | Shadow plugin, depends on mdn-api + mdn-bridge |
| 21 | `MDNCore.java` | Redis channels, key prefixes, permission constants |
| 22 | `database/DatabaseManager.java` | HikariCP pool, schema init, isConnected() |
| 23 | `redis/RedisManager.java` | Jedis pool, publish, subscribe, get/set/delete |
| 24 | `cache/PlayerCache.java` | Redis-backed cache, CachedPlayer class, computeIfAbsent |
| 25 | `registry/ServerRegistry.java` | ServerInfo class, register/unregister, findBest methods |
| 26 | `session/SessionManager.java` | PlayerSession class, create/transfer/remove session |
| 27 | `sync/DataSyncEngine.java` | lockPlayer/unlockPlayer, savePlayerProfile, saveAll |
| 28 | `sync/InventorySyncManager.java` | saveInventory, loadInventory, base64 helpers |
| 29 | `velocity/CoreVelocityPlugin.java` | Proxy init, login/disconnect events, /hub, /lobby, /mdn commands |
| 30 | `paper/CorePaperPlugin.java` | onEnable sequence, join/quit events, /mdn command |
| 31 | `resources/plugin.yml` | Paper metadata |
| 32 | `resources/velocity-plugin.json` | Velocity metadata |
| 33 | `resources/config.yml` | Database, Redis, network, routing settings |

### Step 1.5 — Skeleton plugins (7 modules)

**Files created per skeleton** (mdn-auth through mdn-sam):
- `build.gradle.kts` — plugin + shadow + platform deps
- `plugin.yml` — metadata
- Main class — numbered TODO checklist in Javadoc
- Some also got `velocity-plugin.json` (communication, maintenance)

### Step 1.6 — Build errors encountered & fixed

| # | Error | Root Cause | Fix |
|---|-------|-----------|-----|
| 1 | `FAILURE: 25.0.3` | Kotlin in Gradle 8.10 doesn't support Java 25 class files | Set `JAVA_HOME=/usr/lib/jvm/java-1.21.0-openjdk-amd64` |
| 2 | `Unresolved reference: compileOnly` | `apply(plugin = "java-library")` in root subprojects block didn't take effect for dependency blocks | Removed dependencies from root subprojects block; moved to individual subproject build.gradle.kts |
| 3 | `Unresolved reference: java {}` | `java {}` block used in root subprojects before plugin applied | Removed java block from root; added to each subproject's build.gradle.kts |
| 4 | `Entry velocity-plugin.json is a duplicate` | Dual-platform plugin has both plugin.yml and velocity-plugin.json; processResources expand creates duplicates | Added `duplicatesStrategy = DuplicatesStrategy.EXCLUDE` to jar+processResources tasks |
| 5 | `Unsupported class file major version 65` | Shadow 8.1.1 uses ASM 9.2 which doesn't support Java 21 bytecode | Switched to `com.gradleup.shadow` version 8.3.5 |
| 6 | `Plugin com.github.johnrengelman.shadow:8.3.6 not found` | Tried upgrading to 8.3.6 but that version doesn't exist | Used `com.gradleup.shadow:8.3.5` (community fork) |
| 7 | `Task ':mdn-core:shadowJar' uses output of ':mdn-bridge:shadowJar' without dependency` | mdn-core depends on mdn-bridge; shadowJar picks up bridge's fat jar | Added `dependsOn(":mdn-bridge:shadowJar")` to mdn-core's shadowJar task |

### Step 1.7 — Build successful
- `mdn-api:build` ✅ — 2 actionable tasks
- `mdn-bridge:build` ✅ — shadowJar produced
- `mdn-core:build` ✅ — shadowJar produced

### Step 1.8 — Code review & fixes applied
- **AES mode**: Changed from ECB (insecure) → GCM with random 12-byte IV prepended to ciphertext
- **MDNPacket dispatching**: Added `@JsonTypeInfo` + `@JsonSubTypes` for Jackson polymorphic deserialization
- **Skeleton builds**: Added `java {}` block with toolchain to all 7 skeletons so new devs can compile immediately

### Step 1.9 — First push to GitHub
- Commit: `9522d09` — 70 files, 3,412 insertions
- Pushed to `codex0027/MineDrop-Network` (main)

---

## Phase 2 — Production Hardening (Commit: part of `9522d09`)

### Step 2.1 — MDN-API fixes

| # | Change | File | What was wrong | What we did |
|---|--------|------|---------------|-------------|
| 8 | Added `shutdown()` | `MDNAPI.java` | No way to cleanly close DB/Redis pools | Added close logic for HikariDataSource + JedisPool, with try/catch per resource |
| 9 | Added `isInitialized()` | `MDNAPI.java` | Plugins couldn't safely check if API was ready | Added AtomicBoolean flag, checked in getInstance() |
| 10 | Added `createStandalone()` | `MDNAPI.java` | No way to test without live DB | Creates instance with null pools for unit testing |
| 11 | ObjectMapper configuration | `MDNAPI.java` | No JavaTimeModule, unknown props would crash | Added JavaTimeModule, FAIL_ON_UNKNOWN_PROPERTIES=false, NON_NULL serialization |
| 12 | Added `ServerHeartbeatPacket` | New file | Missing from design doc | Server TPS/player count heartbeat packet |
| 13 | Added `PlayerSwitchServerPacket` | New file | Missing from design doc | Player transfer signal with force flag |
| 14 | Added `InventoryLockPacket` | New file | Missing from design doc | Inventory lock during server transfers |
| 15 | Added `PlayerJoinSyncEvent` | New file | Missing sync events | Fired when player data loaded on new server |
| 16 | Added `PlayerQuitSyncEvent` | New file | Missing sync events | Fired when player disconnects |
| 17 | Added `InventorySyncEvent` | New file | Missing sync events | Fired when inventory sync completes |

**Build errors during Phase 2**:
| # | Error | Fix |
|---|-------|-----|
| 8 | `package com.fasterxml.jackson.datatype.jsr310 does not exist` | Added `jackson-datatype-jsr310:2.17.2` to mdn-api dependencies |
| 9 | `class PlayerJoinSyncEvent is public, should be declared in PlayerJoinSyncEvent.java` | Renamed `SyncEvent.java` → `PlayerJoinSyncEvent.java`, split other events into own files |

### Step 2.2 — MDN-Bridge fixes

| # | Change | File | What was wrong | What we did |
|---|--------|------|---------------|-------------|
| 18 | Real JAR hashing | `BridgeManager.java` | `computeJarHash()` hashed the file PATH string, not the bytes | Changed to `Files.readAllBytes(jarPath)` + actual SHA-256 |
| 19 | Parse signature.json | `BridgeManager.java` | File was read but content ignored | Parse with Jackson, validate internal `build_hash` field |
| 20 | Thread-safe singleton | `BridgeManager.java` | `getInstance()` had race condition | Double-checked locking with `volatile` |
| 21 | Retry buffer | `BridgePaperPlugin.java` | Handshake tried once and gave up | 3 retries × 3-second spacing per design doc |
| 22 | Plugin disabling | `BridgeManager.java` + `BridgePaperPlugin.java` | Verification failure only logged | Calls `PluginManager.disablePlugin()` via callback |
| 23 | Debug mode restriction | `BridgePaperPlugin.java` | Debug bypass worked anywhere | Checks `Bukkit.getIp()` — only allows on 127.0.0.1/0.0.0.0/localhost |
| 24 | Discord webhook | `BridgeManager.java` | No alert on security failure | `sendDiscordAlert()` method with fire-and-forget virtual thread |
| 25 | Velocity config | `BridgeVelocityPlugin.java` + `config-velocity.yml` | Secrets hardcoded | Reads from `plugins/mdn-bridge/config.yml` |
| 26 | BridgeSecurityProvider | `BridgeManager.java` | Interface existed, never registered | `setPluginDisabler()` callback, `isPluginSecure()` check |

### Step 2.3 — MDN-Core fixes

| # | Change | File | What was wrong | What we did |
|---|--------|------|---------------|-------------|
| 27 | Heartbeat timeout | `ServerRegistry.java` | Dead servers stayed forever | 45s timeout, ScheduledExecutorService evicts every 15s |
| 28 | Health scoring | `ServerRegistry.ServerInfo` | Routing ignored server health | `getHealthScore()` considers TPS + load + staleness |
| 29 | Cache eviction | `PlayerCache.java` | Memory leak — never cleaned | CacheEntry with lastAccess, 10-min TTL, 5-min cleanup |
| 30 | Cancellable Redis subs | `RedisManager.java` | Thread leak on disable | Track subscriptions in Set, unsubscribe all on shutdown |
| 31 | saveAll() real implementation | `DataSyncEngine.java` | Was a no-op (only logged) | Iterates pendingSaves, flushes all with 30s timeout |
| 32 | EnderChest save | `InventorySyncManager.java` | Param received but ignored | Now stored as `{"inv":"...","ec":"..."}` JSON |
| 33 | Crash recovery buffer | `DataSyncEngine.java` | Save failures = data loss | Dumps JSON to `plugins/MDN-Core/emergencies/profile_<uuid>.json` |
| 34 | PacketDispatcher | New file + `CorePaperPlugin.java` | Incoming Redis messages only logged | Routes deserialized packets to registered handlers |
| 35 | Velocity config | `CoreVelocityPlugin.java` + `config-velocity.yml` | All settings hardcoded | Reads host, port, password, region from YAML |
| 36 | Standard commands | `CoreVelocityPlugin.java` + `CorePaperPlugin.java` | Missing /website /store /vote /discord /help /rules /spawn | All added with config toggle |
| 37 | /mdn health command | Both plugins | Only basic status | Full report: DB, Redis, TPS, players, memory, circuit breakers |
| 38 | Server heartbeat | `CorePaperPlugin.java` | Not sent | Publishes ServerHeartbeatPacket every 5 seconds via Redis |

### Step 2.4 — Build errors during Phase 2 fixes
| # | Error | Fix |
|---|-------|-----|
| 10 | `State has private access in CircuitBreaker` in test | Changed `private enum State` → `public enum State` |
| 11 | `Task ':mdn-core:test' uses output of ':mdn-bridge:shadowJar' without dependency` | Added `dependsOn(":mdn-bridge:shadowJar")` to mdn-core test task |

---

## Phase 3 — Resilience & Observability (Commit: `1687d56`)

### Step 3.1 — Unit tests

| # | Change | File | Tests |
|---|--------|------|-------|
| 39 | Created SecurityUtilTest | `mdn-api/src/test/.../SecurityUtilTest.java` | 8 tests: SHA-256 consistency, different inputs, HMAC consistency, different secrets, AES roundtrip, wrong password, tampered ciphertext, empty string |
| 40 | Created ApiVersionTest | `mdn-api/src/test/.../ApiVersionTest.java` | 9 tests: parse full, parse no-patch, compatible higher minor, same minor, incompatible major, incompatible lower minor, compareTo, invalid format, CURRENT exists |
| 41 | Created CircuitBreakerTest | `mdn-core/src/test/.../CircuitBreakerTest.java` | 7 tests: success keeps closed, failures open circuit, open rejects, manual reset, success resets counter, executeVoid open, executeVoid success |

### Step 3.2 — Circuit Breaker

| # | Change | File | Details |
|---|--------|------|---------|
| 42 | Created CircuitBreaker | `CircuitBreaker.java` | 5-failure threshold, 30s cooldown, CLOSED→OPEN→HALF_OPEN states |
| 43 | Wired into CorePaperPlugin | `CorePaperPlugin.java` | `dbCircuitBreaker` + `redisCircuitBreaker` fields, initialized in onEnable |
| 44 | Heartbeat uses circuit | `CorePaperPlugin.java` | `redisCircuitBreaker.executeVoid(() -> redisManager.publishPacket(...))` |
| 45 | Health command shows circuits | `CorePaperPlugin.java` | `/mdn health` displays circuit breaker states |

### Step 3.3 — API Versioning

| # | Change | File | Details |
|---|--------|------|---------|
| 46 | Created ApiVersion | `ApiVersion.java` | MAJOR.MINOR.PATCH, parse(), isCompatibleWith(), compareTo() |
| 47 | Added to MDNAPI | `MDNAPI.java` | `getApiVersion()` returns `ApiVersion.CURRENT` |
| 48 | Bridge checks compatibility | `BridgeManager.java` | `register()` with `requiredApiVersion` parameter, compares against CURRENT |
| 49 | Logged on startup | `CorePaperPlugin.java` | Logs "MDN-API version: 1.0.0" on enable |

### Step 3.4 — Config Validation

| # | Change | File | Details |
|---|--------|------|---------|
| 50 | validateConfiguration() | `CorePaperPlugin.java` | Checks database.host, database.database, redis.host are set |
| 51 | performHealthChecks() | `CorePaperPlugin.java` | Runs SELECT 1 + PING on startup, logs warnings but allows degraded mode |
| 52 | Fail-fast | `CorePaperPlugin.java` | If critical config missing, calls `disablePlugin(this)` |

### Step 3.5 — Correlation IDs

| # | Change | File | Details |
|---|--------|------|---------|
| 53 | correlationId field | `MDNPacket.java` | Auto-generated: `instanceId-timestamp`, settable for propagation |
| 54 | instanceId | `MDNAPI.java` | 8-char UUID substring, unique per server instance |
| 55 | Correlation logging | `CorePaperPlugin.java` | Logs `[corr=<instanceId>] Player joined: <name>` on join |
| 56 | Logged in status | `CorePaperPlugin.java` | `/mdn status` shows "Instance: a1b2c3d4" |

---

## Phase 4 — Dead Letter Queue & Operation Timeouts (Commit: `61c063a`)

### Step 4.1 — Dead Letter Queue

| # | Change | File | Details |
|---|--------|------|---------|
| 57 | Created DeadLetterQueue | `DeadLetterQueue.java` | 5 retries, exponential backoff (1s→2s→4s→8s→16s), permanent DLQ list |
| 58 | Added lpush/rpop/llen | `RedisManager.java` | Redis list operations needed by DLQ |
| 59 | Wired DLQ into PacketDispatcher | `PacketDispatcher.java` | Handler exceptions → `deadLetterQueue.enqueue(rawJson, error)` |
| 60 | Wired DLQ into CorePaperPlugin | `CorePaperPlugin.java` | Initialized in onEnable, shut down in onDisable, shown in /mdn health |
| 61 | setDeadLetterQueue() | `PacketDispatcher.java` | Setter for DLQ reference (circular dependency avoidance) |

### Step 4.2 — Operation Timeouts

| # | Change | File | Details |
|---|--------|------|---------|
| 62 | executeWithTimeout() | `DatabaseManager.java` | Wraps any SQL op in CompletableFuture.get(seconds, SECONDS), null on timeout |
| 63 | executeWithTimeout() | `RedisManager.java` | Same pattern for Redis operations |
| 64 | Timeout-protected isConnected() | `DatabaseManager.java` | Changed from direct conn check to CompletableFuture with 10s timeout |
| 65 | Timeout-protected isConnected() | `RedisManager.java` | Changed from direct ping to CompletableFuture with 5s timeout |
| 66 | DBOperation interface | `DatabaseManager.java` | @FunctionalInterface for SQL operations |
| 67 | RedisOperation interface | `RedisManager.java` | @FunctionalInterface for Redis operations |

### Step 4.3 — Build errors during Phase 4

| # | Error | Fix |
|---|-------|-----|
| 12 | `local variables referenced from a lambda expression must be final or effectively final` | `entry` in while loop reassigned; added `final String finalEntry = entry;` to capture it |

### Step 4.4 — Documentation

| # | Change | File | Details |
|---|--------|------|---------|
| 68 | Created DIARY.md | Root | 500+ line dev journal: architecture, file map, conventions, changelog |
| 69 | Created STEPS.md | Root | This file — every change logged |
| 70 | Created SUGGEST.md | Root | All suggestions organized by status |
| 71 | Created TIMELINE.md | Root | Past/present/future roadmap |
| 72 | Updated DIARY.md | `DIARY.md` | Added Phase 4 section, updated changelog |

---

## Summary Statistics

### By Phase
| Phase | Commits | Files Created | Files Modified | Build Errors Fixed |
|-------|---------|---------------|----------------|-------------------|
| Phase 0 (Analysis) | 0 | 0 | 0 | 0 |
| Phase 1 (Foundation) | 1 | 70 | 1 | 7 |
| Phase 2 (Hardening) | 0* | 11 | 14 | 4 |
| Phase 3 (Resilience) | 1 | 7 | 6 | 0 |
| Phase 4 (DLQ + Docs) | 1 | 5 | 4 | 1 |
| **Total** | **3** | **93** | **25** | **12** |

*Phase 2 was bundled with Phase 1 commit

### By Plugin
| Plugin | Source Files | Test Files | Config Files | Total |
|--------|-------------|------------|-------------|-------|
| mdn-api | 15 | 2 | 1 | 18 |
| mdn-bridge | 5 | 0 | 2 | 7 |
| mdn-core | 15 | 1 | 2 | 18 |
| 7 skeletons | 14 | 0 | 0 | 14 |
| Root docs | DIARY, STEPS, SUGGEST, TIMELINE, README | — | — | 5 |
| **Total** | **49** | **3** | **5** | **62** |

---

*This file is updated every time any change is made. No change is too small to log.*
