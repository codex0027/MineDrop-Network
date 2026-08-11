# MineDrop Network — Step-by-Step Development Log

> **Purpose**: Every single change — no matter how small — is logged here chronologically.  
> **For**: New developers onboarding, debugging "why was this done this way", and auditing changes.  
> **Last Updated**: August 11, 2026 — Password auth system + /auth clear + COMMANDS.md

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

## Phase 5 — Startup Lifecycle Fixes (Commit: `847745f`)

### Step 5.1 — PacketDispatcher NullPointerException (Issue #1)

**Root cause**: In `CorePaperPlugin.onEnable()`, Step 7 created `DeadLetterQueue` with a lambda
that captured `packetDispatcher::dispatch`, but `packetDispatcher` wasn't assigned until Step 8.
The `this.packetDispatcher` field was null when the lambda was constructed.

| # | Change | File | Details |
|---|--------|------|---------|
| 73 | Reordered Steps 7-8 | `CorePaperPlugin.java` | `packetDispatcher = new PacketDispatcher()` now runs BEFORE `deadLetterQueue = new DeadLetterQueue(...)`. The DLQ's retry lambda now captures an already-assigned `packetDispatcher`. |

### Step 5.2 — CoreVelocityPlugin MDNAPI Not Initialized (Issue #3a)

**Root cause**: `CoreVelocityPlugin.onProxyInitialize()` created `PlayerCache` which calls
`MDNAPI.getInstance().getObjectMapper()` in its constructor. But `MDNAPI.initialize()` was NEVER called
on Velocity — it was only called in `CorePaperPlugin`. This caused an NPE on Velocity startup.

| # | Change | File | Details |
|---|--------|------|---------|
| 74 | Added MDNAPI init | `CoreVelocityPlugin.java` | Added `MDNAPI.initialize(null, redisManager.getJedisPool())` before `PlayerCache` creation. Velocity runs in Redis-only mode (no MySQL). |

### Step 5.3 — BridgeManager Jackson Dependency Decoupling (Issue #3b)

**Root cause**: `BridgeManager.readAndParseSignature()` called `MDNAPI.getInstance().getObjectMapper()`
to parse `signature.json`. But `BridgeManager.register()` runs during `onLoad()`, which executes BEFORE
`CorePaperPlugin.onEnable()` where `MDNAPI.initialize()` is called. This created a latent crash if any
plugin had a `signature.json` file.

| # | Change | File | Details |
|---|--------|------|---------|
| 75 | Standalone ObjectMapper | `BridgeManager.java` | BridgeManager now has its own static `OBJECT_MAPPER` field configured identically to MDNAPI's mapper (JavaTimeModule, FAIL_ON_UNKNOWN_PROPERTIES=false, NON_NULL). Zero MDNAPI dependency during onLoad(). |
| 76 | Removed unused import | `BridgePaperPlugin.java` | Removed `import net.minedrop.api.MDNAPI` and `import net.minedrop.api.security.SecurityUtil` — no longer needed. |

### Step 5.4 — Redis Health Check False-Negatives (Issue #2)

**Root cause**: `RedisManager.isConnected()` used `CompletableFuture.supplyAsync()` which defaults
to `ForkJoinPool.commonPool()`. Bukkit also uses this pool for async tasks. Under load, the pool
could starve, causing the 5-second health check timeout to fire before `PING` was ever attempted.

| # | Change | File | Details |
|---|--------|------|---------|
| 77 | Dedicated health executor | `RedisManager.java` | Added `healthCheckExecutor` (single-thread daemon executor). `isConnected()` and `executeWithTimeout()` now use this pool instead of ForkJoinPool.commonPool(). Shut down with awaitTermination in `shutdown()`. |

### Step 5.5 — onDisable() Missing redisManager.shutdown()

**Root cause**: `CorePaperPlugin.onDisable()` called `MDNAPI.shutdown()` which closes the JedisPool,
but never called `redisManager.shutdown()` which stops subscriber threads. Active Redis subscriptions
were never unsubscribed, causing thread leaks on reload.

| # | Change | File | Details |
|---|--------|------|---------|
| 78 | Added redisManager.shutdown() | `CorePaperPlugin.java` | Added `redisManager.shutdown()` between `playerCache.shutdown()` and `MDNAPI.shutdown()` in onDisable(). Subscriber threads are now properly stopped and JedisPool is only closed by MDNAPI after all subscribers are unsubscribed. |

### Step 5.6 — Documentation & Deployment

| # | Change | File | Details |
|---|--------|------|---------|
| 79 | Created DEPLOY.md | `DEPLOY.md` | Complete 4-phase Pterodactyl deployment guide: MySQL/Redis setup, Minecraft server creation, plugin upload + config generation, startup order + verification. Includes troubleshooting table and quick reference card. |
| 80 | Updated config files | `config.yml` × 4 | Added Pterodactyl-oriented comments and CHANGE-ME markers for required fields (server-identity, secret-api-key, database host). |

---

## Phase 6 — Redis Connection Reset & Bridge Shadow JAR Build Fix (Commit: `dddaa76`)

### Step 6.1 — Redis "SocketException: Connection reset" on Publish/Subscribe (Issue #1)

**Root cause**: `JedisPoolConfig` had ZERO connection validation settings. In Docker/Pterodactyl
environments, Redis closes idle TCP connections (network timeouts, Redis `timeout` config).
The pool handed out dead connections without checking. First operation on a dead connection
failed with `SocketException: Connection reset`. Health checks also failed because `isConnected()`
borrowed a dead connection that couldn't PING.

**Why this wasn't caught earlier**: Local development (localhost Redis) doesn't drop idle connections.
It only manifests in containerized deployments where Docker's network layer or Redis server config
closes idle TCP sockets.

| # | Change | File | Details |
|---|--------|------|---------|
| 81 | Connection validation | `RedisManager.java` | Added to JedisPoolConfig: `testOnCreate(true)` — validates first connection at pool init; `testOnBorrow(true)` — PING-validates every borrowed connection before use; `testWhileIdle(true)` — periodically evicts dead idle connections; `minEvictableIdleDuration(1min)` + `timeBetweenEvictionRuns(30s)` — eviction timing. |

### Step 6.2 — MDN-Bridge NoClassDefFoundError: com.fasterxml.jackson.databind.Module (Issue #2)

**Root cause (TWO layers)**:

**Layer 1 — Build system bug**: Both `jar` and `shadowJar` tasks in `build.gradle.kts` output to
the SAME filename (`mdn-bridge-1.0.0-SNAPSHOT.jar`) because both use `archiveClassifier.set("")`.
On clean builds, Gradle runs `jar` AFTER `shadowJar`, causing the plain 19KB JAR (only Bridge classes,
zero dependencies) to silently overwrite the 4.0MB shadow JAR (with Jackson, HikariCP, Jedis bundled).

The overwritten JAR contains no Jackson classes → `NoClassDefFoundError` on first class load.

**Layer 2 — Missing explicit dependency**: Even though Jackson was transitively available via
`implementation(project(":mdn-api"))`, relying solely on transitive resolution is fragile.
Making it explicit guarantees inclusion regardless of Gradle resolution quirks.

| # | Change | File | Details |
|---|--------|------|---------|
| 82 | jar classifier | `mdn-bridge/build.gradle.kts` | Added `archiveClassifier.set("original")` to `tasks.jar`. Plain jar now outputs as `mdn-bridge-1.0.0-SNAPSHOT-original.jar`, never conflicts with shadow JAR. |
| 83 | jar classifier | `mdn-core/build.gradle.kts` | Same fix — `archiveClassifier.set("original")` on jar task. Prevents the same overwrite bug on clean builds. |
| 84 | Explicit Jackson deps | `mdn-bridge/build.gradle.kts` | Added `implementation` for jackson-databind, jackson-annotations, jackson-datatype-jsr310 (2.17.2). Guarantees Jackson is bundled in shadow JAR. |

### Step 6.3 — Verification

| Check | Result |
|-------|--------|
| mdn-bridge shadow JAR size | 4.0 MB (up from 19 KB — Jackson now included) |
| mdn-bridge total entries | 2,418 (was 16 — all deps now bundled) |
| Jackson classes in bridge JAR | 1,212 relocated to `net/minedrop/libs/jackson/` ✅ |
| Module.class location | `net/minedrop/libs/jackson/jackson/databind/Module.class` ✅ |
| mdn-core shadow JAR size | 4.0 MB, 2,453 entries ✅ |
| All 3 plugins build + tests | PASS ✅ |

---

## Phase 7 — Duplicate velocity-plugin.json Fix (Commit: `ae71f4f`)

### Step 7.1 — Shadow JAR Contains Two Copies of velocity-plugin.json

**Root cause**: Both the Velocity `@Plugin` annotation processor AND a manual template
in `src/main/resources/velocity-plugin.json` were producing the same file. The annotation
processor auto-generates `velocity-plugin.json` from `@Plugin(id, name, version, authors, dependencies)`
into `build/classes/java/main/`. The `processResources` task expanded the manual template
into `build/resources/main/`. The shadow JAR picked up **both** copies.

**Symptoms**:
- `jar tf ... | grep velocity-plugin` returned 2 entries
- `unzip -p` printed two concatenated JSON documents (174 bytes + 274 bytes)
- Could cause Velocity to read the wrong (unexpanded) copy

**Why this is a bug**: The `@Plugin` annotation is the canonical source of truth for
Velocity metadata. A manual template duplicates this responsibility and creates
race-condition-like ambiguity about which copy Velocity actually reads at runtime.

| # | Change | File | Details |
|---|--------|------|---------|
| 85 | Deleted manual template | `mdn-bridge/src/main/resources/velocity-plugin.json` | The `@Plugin` annotation on `BridgeVelocityPlugin` already has all metadata (id=mdn-bridge, name=MDN-Bridge, version, authors). The annotation processor generates `velocity-plugin.json` from this — no manual template needed. |
| 86 | Deleted manual template | `mdn-core/src/main/resources/velocity-plugin.json` | The `@Plugin` annotation on `CoreVelocityPlugin` has all metadata including `@Dependency(id = "mdn-bridge")`. Auto-generated copy correctly includes `"dependencies":[{"id":"mdn-bridge","optional":false}]`. |
| 87 | Removed expand blocks | `mdn-bridge/build.gradle.kts` + `mdn-core/build.gradle.kts` | Replaced `filesMatching("velocity-plugin.json") { expand(...) }` blocks with comments explaining the annotation processor handles it. The `expand()` calls had no effect after the template files were deleted. |

### Step 7.2 — Verification

| Check | Before | After |
|-------|--------|-------|
| Bridge JAR velocity-plugin entries | 2 (174B + 274B) | **1** ✅ |
| Core JAR velocity-plugin entries | 2 (duplicate) | **1** ✅ |
| Bridge content | Two concatenated JSON docs | Single valid JSON: `{"id":"mdn-bridge",...}` ✅ |
| Core content | Two concatenated JSON docs | Single valid JSON with dependencies `[{"id":"mdn-bridge"}]` ✅ |
| Bridge dependencies | Missing from annotation copy | Included: `"dependencies":[]` ✅ |
| Core dependencies | Correctly reflects `@Dependency(id="mdn-bridge")` | Unchanged — was already correct ✅ |
| All 3 plugins build + tests | PASS | PASS ✅ |

### Step 7.3 — Files Changed

| File | Change |
|------|--------|
| `mdn-bridge/src/main/resources/velocity-plugin.json` | **Deleted** (8 lines) |
| `mdn-core/src/main/resources/velocity-plugin.json` | **Deleted** (9 lines) |
| `mdn-bridge/build.gradle.kts` | Removed `filesMatching("velocity-plugin.json")` expand block, added comment (−10/+2 lines) |
| `mdn-core/build.gradle.kts` | Removed `filesMatching("velocity-plugin.json")` expand block, added comment (−10/+2 lines) |

---

## Phase 8 — Velocity Config Bootstrap Fix (Commit: `81da386`)

### Step 8.1 — "No config.yml found — using defaults" on Velocity

**Root cause**: Velocity doesn't have Paper's `saveDefaultConfig()` API. Both
`BridgeVelocityPlugin` and `CoreVelocityPlugin` were reading `plugins/mdn-*/config.yml`
from disk but **never created the data directory or copied default config from the JAR**.
The file never existed on first startup → always fell through to hardcoded defaults.

**Symptoms on real server**:
- Logs: `"No config.yml found — using defaults."` every startup
- No `plugins/mdn-bridge/` or `plugins/mdn-core/` folder created under proxy plugins directory
- Redis host, port, password, and region always hardcoded defaults — couldn't customize

**Additional bug found**: CoreVelocityPlugin read `network.default-region` from config
but its Velocity-specific `config-velocity.yml` uses `routing.default-region`. The
region was never loaded from the Velocity config file.

| # | Change | File | Details |
|---|--------|------|---------|
| 88 | Added `saveDefaultConfig()` | `CoreVelocityPlugin.java` | Creates `plugins/mdn-core/`, copies `config-velocity.yml` from JAR → `config.yml`. Loader now reads both `network.default-region` (Paper layout) and `routing.default-region` (Velocity layout). Cleaned unused `CommandMeta` import. |
| 89 | Added `saveDefaultConfig()` | `BridgeVelocityPlugin.java` | Creates `plugins/mdn-bridge/`, copies `config-velocity.yml` from JAR → `config.yml`. Cleaned unused `PluginManager` import. |
| 90 | Verified config resources | Both shadow JARs | Bridge: `config-velocity.yml` + `config.yml` both bundled ✅. Core: `config-velocity.yml` + `config.yml` both bundled ✅. Each plugin uses the Velocity-specific config as the default source. |
| 91 | Config path compatibility | `CoreVelocityPlugin.java` | Added `routing.default-region` parsing alongside `network.default-region`. The Velocity config uses `routing` layout (no database section); Paper config uses `network` layout. Both paths work. |

### Step 8.2 — Config Source Matrix

| Plugin | JAR Resource | Copies To | What's in it |
|--------|-------------|-----------|-------------|
| mdn-bridge | `config-velocity.yml` | `plugins/mdn-bridge/config.yml` | Bridge settings: server-identity, secret-api-key, handshake timeout (no DB) |
| mdn-core | `config-velocity.yml` | `plugins/mdn-core/config.yml` | Redis + routing + command toggles (no MySQL section — Velocity is Redis-only) |

### Step 8.3 — Startup Flow (Now Correct)

```
Velocity onProxyInitialize()
  ├─ saveDefaultConfig()  → creates plugins/mdn-*/ + copies config from JAR ✅
  ├─ loadConfiguration()  → reads from disk ✅ (file now exists!)
  └─ subsystems init      → uses actual config values ✅
```

### Step 8.4 — Files Changed

| File | Change |
|------|--------|
| `CoreVelocityPlugin.java` | Added `saveDefaultConfig()` method (+32 lines), updated loader for both config layouts, removed unused `CommandMeta` import (−1) |
| `BridgeVelocityPlugin.java` | Added `saveDefaultConfig()` method (+32 lines), removed unused `PluginManager` import (−1) |

---

## Phase 9 — Cross-Server Handshake & Signature Verification (Commit: `ed69f5d`)

### Step 9.1 — Cross-Server Handshake via Redis Pub/Sub

**Root cause**: The handshake was designed but never implemented end-to-end.
BridgePaperPlugin's `performCrossServerHandshake()` created a CompletableFuture but
nobody completed it — the challenge was generated but never published to Redis,
and Velocity never subscribed to respond. The handshake always timed out.

**Design**: Challenge-response via Redis Pub/Sub channels:
- Paper publishes challenge JSON to `mdn:bridge:handshake`
- Velocity subscribes, computes HMAC-SHA256 with secret-api-key, publishes response to `mdn:bridge:handshake:response`
- Paper validates HMAC — if correct, session token is established

**Timing problem solved**: BridgePaperPlugin.onEnable() runs BEFORE CorePaperPlugin.onEnable(),
so Redis isn't ready when Bridge first attempts the handshake. Fixed by:
- Bridge defers handshake until Core injects the transport
- CorePaperPlugin triggers `bridgePlugin.triggerHandshake()` after setting HandshakeTransport
- CoreVelocityPlugin triggers `BridgeVelocityPlugin.triggerHandshakeListener()` similarly

| # | Change | File | Details |
|---|--------|------|---------|
| 92 | HandshakeTransport interface | `BridgeManager.java` | Avoids circular dependency: mdn-bridge can't import mdn-core's RedisManager. Transport has publish/subscribe/isConnected methods. Core injects implementation. |
| 93 | Real cross-server handshake | `BridgePaperPlugin.java` | Publishes challenge JSON to Redis, subscribes to response channel, waits for Velocity's HMAC response, validates. Triggered by Core after transport ready. |
| 94 | Handshake responder | `BridgeVelocityPlugin.java` | Subscribes to `mdn:bridge:handshake`, parses challenge, computes HMAC, publishes response to `mdn:bridge:handshake:response`. Static `triggerHandshakeListener()` called by Core. |
| 95 | Transport injection (Paper) | `CorePaperPlugin.java` | Creates HandshakeTransport wrapping RedisManager, calls `bridgeManager.setHandshakeTransport()`, then triggers `bridgePlugin.triggerHandshake()`. |
| 96 | Transport injection (Velocity) | `CoreVelocityPlugin.java` | Same pattern — injects transport then calls `BridgeVelocityPlugin.triggerHandshakeListener()`. |

### Step 9.2 — Build-Time signature.json Generation

**Root cause**: `BridgeManager.verifyPluginSignature()` looks for `signature.json` in the JAR,
but nothing generated it. In debug mode this is bypassed, but production needs real signatures.

The chicken-and-egg problem: you can't hash the JAR and embed that hash in the JAR
because embedding changes the bytes and invalidates the hash.

**Solution**:
- Gradle `generateSignature` task runs after `jar`, computes SHA-256 of JAR contents
  by iterating ZIP entries while **skipping signature.json**
- Writes `signature.json` to `build/generated/signature/`
- `shadowJar` picks it up via `from()`
- `BridgeManager.computeJarHash()` mirrors the same algorithm at runtime:
  iterates ZIP entries, skips signature.json, hashes the rest
- Both produce identical hashes → verification succeeds

| # | Change | File | Details |
|---|--------|------|---------|
| 97 | generateSignature task | `mdn-bridge/build.gradle.kts` + `mdn-core/build.gradle.kts` | Custom Gradle task: reads JAR via ZipInputStream, hashes entries in order (skipping signature.json), writes JSON with plugin_id, version, build_hash, timestamp. |
| 98 | Fixed computeJarHash() | `BridgeManager.java` | Changed from `Files.readAllBytes(jarPath)` to ZIP entry iteration, skipping signature.json — matches build-time hash exactly. |

### Step 9.3 — ClassLoader Conflict Fix

**Root cause**: Both mdn-bridge.jar and mdn-core.jar bundled `net/minedrop/bridge/**` classes.
Paper creates separate ClassLoaders per JAR → `BridgePaperPlugin.class` loaded twice →
`ClassCastException: BridgePaperPlugin cannot be cast to BridgePaperPlugin`.

| # | Change | File | Details |
|---|--------|------|---------|
| 99 | Exclude bridge from core | `mdn-core/build.gradle.kts` | Added `exclude("net/minedrop/bridge/**")` to shadowJar. mdn-core uses BridgeManager from mdn-bridge's JAR at runtime via plugin dependency. |

### Step 9.4 — Handshake End-to-End Verification

Tested on live servers (Paper 26.2 + Velocity 4.1.0):

```
Lobby (Paper):
  [10:48:45] Handshake VERIFIED — session established with Velocity
  [10:48:45] Velocity handshake SUCCESS on attempt 3

Proxy (Velocity):
  [10:48:45] Received handshake challenge from paper-lobby-01: 26accd25...
  [10:48:45] Handshake response published for server: paper-lobby-01
```

Known race: Lobby starts before Proxy → first 2 attempts lost (Proxy not subscribed yet).
Attempt 3 succeeds once Proxy is up. Production fix: start Proxy before Paper servers.

---

## Phase 10 — Handshake Race Fix & Signature Hash Fix (Commit: `de86137`)

### Step 10.1 — Handshake Race: 2-Second Initial Delay

**Root cause**: Paper started before Velocity, so Paper's first handshake challenge
was published before Velocity subscribed to `mdn:bridge:handshake`. The first 2 of 3
retries were wasted waiting for a subscription that hadn't started yet.

| # | Change | File | Details |
|---|--------|------|---------|
| 100 | Added 2s initial delay | `BridgePaperPlugin.java` | `triggerHandshake()` now schedules first attempt with 40-tick (2s) delay via `runTaskLaterAsynchronously`. This gives Velocity time to subscribe to the handshake channel before Paper publishes. |

### Step 10.2 — Signature Hash: Alphabetical Entry Sorting

**Root cause**: `computeJarHash()` iterated ZIP entries in their on-disk order.
`jar uf` (used to inject signature.json) rewrites the ZIP central directory,
changing entry order. The hash computed at build time (before injection) didn't
match the hash computed at runtime (after injection).

| # | Change | File | Details |
|---|--------|------|---------|
| 101 | Sorted entry hashing | `BridgeManager.java` | `computeJarHash()` now collects all entries, sorts alphabetically by name, then hashes. Order-invariant — matches any ZIP tool. |
| 102 | Sorted entry hashing (Gradle) | `mdn-bridge/build.gradle.kts` + `mdn-core/build.gradle.kts` | `computeJarHash()` in both Gradle scripts uses same sort-then-hash algorithm. |
| 103 | Python signature injection | `mdn-bridge/build.gradle.kts` + `mdn-core/build.gradle.kts` | Replaced `jar uf` (rewrites JAR) with `python3 -c zipfile.ZipFile(ZIP_STORED)` (preserves entries). |

### Step 10.3 — MDN-Core Self-Registration

**Root cause**: MDN-Core never called `BridgeManager.register()` — only MDN-Bridge
self-registered. MDN-Core's signature was never verified because no registration happened.

| # | Change | File | Details |
|---|--------|------|---------|
| 104 | Added BridgeManager.register() | `CorePaperPlugin.java` | `onLoad()` now calls `BridgeManager.getInstance().register("MDN-Core", this.getClass())`. MDN-Core's signature.json is now verified on startup. |

### Step 10.4 — End-to-End Verification

Both plugins verified with `debug-mode: false` + real `allowed-build-hashes`:

```
[MDN-Bridge] fully verified — signature valid, hash matches.
[MDN-Bridge] passed signature verification.
[MDN-Core] fully verified — signature valid, hash matches.
[MDN-Core] passed signature verification.
[MDN-Bridge] Velocity handshake SUCCESS on attempt 1
```

---

## Phase 11 — MDN-Auth Plugin #4 Implementation (Commit: `2a4a469`)

### Step 11.1 — Build Configuration

Matched the established monorepo conventions from mdn-bridge/mdn-core:
- Shadow JAR with `com.gradleup.shadow`, Java 21 toolchain
- Relocates Jackson (`net.minedrop.libs.jackson`), HikariCP, Jedis
- Excludes `net/minedrop/bridge/**` + `net/minedrop/core/**` (loaded from own JARs)
- `archiveClassifier.set("original")` on jar task (prevents shadow overwrite bug)
- `generateSignature` task with sorted-entry hashing + Python ZIP_STORED injection
- `duplicatesStrategy = DuplicatesStrategy.EXCLUDE` on jar + processResources

| # | Change | File | Details |
|---|--------|------|---------|
| 105 | Created build.gradle.kts | `mdn-auth/build.gradle.kts` | Full build config: shadow plugin, deps (Velocity API, Jackson, Jedis, HikariCP), SLF4J compileOnly (not bundled — Velocity provides it), signature generation |

### Step 11.2 — Core Auth Classes (7 files)

| # | Change | File | Details |
|---|--------|------|---------|
| 106 | AuthManager | `AuthManager.java` | Central coordinator: owns TotpManager, DeviceFingerprinter, AltDetector. Player locking/unlocking (pre-auth state). BridgeManager self-registration for signature verification. Facade for the Velocity plugin. |
| 107 | TotpManager | `TotpManager.java` | RFC 6238 TOTP: SecureRandom 20-byte secrets, Base32 encoding, HmacSHA1, 6-digit codes, 30s period, ±1 step drift buffer. 8 backup codes generated. Redis storage: `mdn:auth:totp:<uuid>` → JSON. otpauth:// URL generation for QR codes. |
| 108 | DeviceFingerprinter | `DeviceFingerprinter.java` | SHA-256 composite hash: client brand + protocol version + IP prefix (first 3 octets). Player UUID NOT included in hash (fingerprints must match across accounts for alt detection). IPv6 returns full address (no dot-separated octets). |
| 109 | AltDetector | `AltDetector.java` | Redis-backed IP/fingerprint → UUID tracking. Key schema: `mdn:auth:alt:ip:<ip>`, `mdn:auth:alt:fp:<hash>`, `mdn:auth:unblocked:<ip>`. ALLOW/KICK/ALERT/SHADOW_BAN actions. IP whitelist via `/auth unblock`. |
| 110 | TwoFactorCommand | `TwoFactorCommand.java` | `/2fa setup` — generates QR link with clickable URL; `/2fa verify <code>` — parses int, verifies TOTP, calls removeLockdown callback on success; `/2fa reset <player>` — stub (needs DB-backed UUID lookup). Permission: verify has no perm requirement (usable while locked). |
| 111 | AuthCommand | `AuthCommand.java` | `/auth unblock <ip>` — IPv4 regex validation, calls AltDetector.unblockIp(). Requires `mdn.auth.admin.unblock` permission. |

### Step 11.3 — Velocity Plugin Main Class

| # | Change | File | Details |
|---|--------|------|---------|
| 112 | AuthVelocityPlugin | `AuthVelocityPlugin.java` | Full Velocity lifecycle: `@Plugin` with dependencies on mdn-bridge + mdn-core. 6-step `onProxyInitialize()`: saveDefaultConfig → loadConfiguration → initRedis → AuthManager.init → registerCommands → log status. Event handlers: PreLoginEvent (early IP alt check), LoginEvent (fingerprint + full alt check + staff 2FA check), DisconnectEvent (cleanup locked players). Pre-auth lockdown: title overlay + persistent action bar + limbo state. removeLockdown callback clears title + routes to lobby. `saveDefaultConfig()` copies config.yml from JAR on first startup. Config parser reads all sections (redis, alt-detection, staff-2fa, private-lobbies). |

### Step 11.4 — Config & Resources

| # | Change | File | Details |
|---|--------|------|---------|
| 113 | Default config | `config.yml` | Matches design spec exactly: redis.host/port/password, auth.alt-detection (max-accounts-per-ip: 3, max-accounts-per-fingerprint: 2, action: KICK), auth.staff-2fa (enabled, enforce-ip-lock, totp-issuer, force-for-permissions), auth.private-lobbies (token-lifetime-seconds: 60, secret-hashing-algorithm: SHA-256) |
| 114 | Removed manual velocity-plugin.json | Deleted file | Annotation processor auto-generates from `@Plugin` — prevents duplicate resource bug (Phase 7 pattern) |

### Step 11.5 — Build Fixes (Skeleton Plugins)

While building mdn-auth, discovered pre-existing issues in all 6 skeleton plugins:

| # | Change | Files | Details |
|---|--------|-------|---------|
| 115 | Missing `version` in expand() | 6 skeleton `build.gradle.kts` | `filesMatching("plugin.yml") { expand("mainClass" to ..., "version" to project.version) }` — `plugin.yml` templates contained `${version}` placeholder but expand() didn't pass version |
| 116 | Duplicate velocity-plugin.json | `mdn-communication`, `mdn-maintenance` | Deleted manual templates — `@Plugin` annotation processor auto-generates (Phase 7 pattern) |
| 117 | Removed stale expand blocks | `mdn-communication/build.gradle.kts`, `mdn-maintenance/build.gradle.kts` | Removed `filesMatching("velocity-plugin.json")` expand blocks after template deletion |

### Step 11.6 — Code Review Fixes

Issues found by code-reviewer-deepseek and fixed before commit:

| # | Change | File | Details |
|---|--------|------|---------|
| 118 | Wired removeLockdown callback | `TwoFactorCommand.java`, `AuthVelocityPlugin.java` | TwoFactorCommand now accepts `Consumer<Player>` — on successful 2FA verify, calls `onVerified.accept(player)` which triggers `removeLockdown()` in AuthVelocityPlugin (clears title, routes to lobby). Previously the player would pass 2FA but remain stuck in limbo. |
| 119 | Removed UUID from fingerprint | `DeviceFingerprinter.java` | `playerUuid` was included in the composite hash, making fingerprints unique per player — defeating alt detection (same device, different account = different fingerprint). Now only uses client brand + protocol version + IP prefix. |
| 120 | Removed dead lock code | `AltDetector.java` | Old `:write_lock` suffix pattern removed — was never functional. |

### Step 11.7 — Build Verification

```
mdn-auth:compileJava ✅
mdn-auth:shadowJar ✅ (4.0 MB, 2,400+ entries)
mdn-auth signature.json ✅ (auto-generated with sorted-entry hash)
velocity-plugin.json ✅ (1 entry — annotation-generated)
config.yml in JAR ✅
No bridge/core class leaks ✅
All 4 plugins build ✅ (mdn-api, mdn-bridge, mdn-core, mdn-auth)
```

### Step 11.8 — Spec Comparison Audit

Compared implementation against `plan/MineDrop/plugins/03_MDN_Auth.md`:

| Requirement | Status |
|-------------|--------|
| Velocity-only plugin | ✅ |
| Device fingerprinting | ✅ SHA-256: brand + protocol + IP prefix |
| Staff 2FA (TOTP) | ✅ RFC 6238, ±1 drift, QR URL, backup codes |
| Alt detection (IP + FP) | ✅ Redis lists, whitelist, ALLOW/KICK/ALERT |
| Config matches spec | ✅ All fields identical |
| 4 commands + permissions | ✅ /2fa setup|verify|reset, /auth unblock |
| Pre-auth lockdown | ✅ Title overlay, action bar, limbo state |
| TOTP time drift | ✅ ±1 step (30s tolerance) |
| Database schema (SQL) | ❌ Redis-only — no MySQL `mdn_auth_totp` table |
| IP lock enforcement | ⚠️ Parsed but never checked on verify |
| /2fa reset full impl | ⚠️ Stub — no username→UUID resolution |
| SHADOW_BAN action | ⚠️ Enum exists, never used |
| Backup code verification | ❌ No `/2fa verify-backup` command |
| Alt list TTL cleanup | ⚠️ Lists grow indefinitely |

**Gaps documented as**: ISSUES.md A-1 through A-7

---

## Phase 12 — MDN-Auth Gap Fixes & Production Hardening (Commit: `afda633`)

### Step 12.1 — A-1: MySQL Persistence for TOTP Records

**Root cause**: TOTP records were Redis-only — data loss on Redis flush. Design spec mandates MySQL `mdn_auth_totp` table.

**Fix**: Dual-write architecture — MySQL is source of truth, Redis is 24h cache.

| # | Change | File | Details |
|---|--------|------|---------|
| 121 | MySQL persistence | `TotpManager.java` | Constructor now accepts optional `HikariDataSource`. `generateSecret()` writes to MySQL via `INSERT ... ON DUPLICATE KEY UPDATE`. `getRecord()` tries Redis first → MySQL fallback → repopulates Redis cache. `deleteSecret()` deletes from both stores. `saveRecord()` updates backup_codes + ip_lock in both. |
| 122 | DB schema already exists | `DatabaseSchema.java` | `mdn_auth_totp` table DDL was already present (added in Phase 2). No schema changes needed. |

### Step 12.2 — A-2: IP Lock Enforcement

**Root cause**: `enforce-ip-lock: true` was parsed but never checked on 2FA verify.

**Fix**: IP prefix comparison on every 2FA verify, plus rate limiting.

| # | Change | File | Details |
|---|--------|------|---------|
| 123 | IP lock verification | `TotpManager.java` | New `verifyCodeWithIpLock()` method: checks rate limit (5 fails/15min), compares stored IP prefix vs current IP, verifies TOTP code with drift. Returns `IpVerifyResult` enum: SUCCESS/INVALID_CODE/IP_MISMATCH/RATE_LIMITED/NO_SECRET/ERROR. |
| 124 | IP lock UX | `TwoFactorCommand.java` | `handleVerify()` now uses `verifyTotpWithIpLock()` with switch on result. IP_MISMATCH → "reconnect from original network". RATE_LIMITED → "wait 15 minutes". On first successful verify, IP lock is set via `updateTotpIpLock()`. |

### Step 12.3 — A-3: Full /2fa Reset Implementation

**Root cause**: Stub — couldn't resolve username to UUID.

**Fix**: Dual-resolution strategy (online + offline).

| # | Change | File | Details |
|---|--------|------|---------|
| 125 | Username→UUID resolution | `AuthManager.java` | `resolveUsername()` tries ProxyServer API for online players, falls back to Redis `mdn:auth:username:<name>` key (30-day TTL). `recordUsernameMapping()` called on every `onLogin()`. |
| 126 | Full reset handler | `TwoFactorCommand.java` | `handleReset()` now uses `resolveUsername()` with `playerResolver` function. Clear success/failure messaging for both online and offline targets. |

### Step 12.4 — A-4: SHADOW_BAN Implementation

**Root cause**: Enum value existed but never used — dead code.

**Fix**: KICK→SHADOW_BAN conversion based on config, Redis set tracking.

| # | Change | File | Details |
|---|--------|------|---------|
| 127 | SHADOW_BAN logic | `AuthVelocityPlugin.java` | `onLogin()` now checks: if `action == KICK && altAction == "SHADOW_BAN"` → calls `authManager.shadowBan(uuid)` instead of disconnecting. Player is allowed but silently flagged. |
| 128 | SHADOW_BAN tracking | `AltDetector.java` | `shadowBan()` adds to Redis set `mdn:auth:shadow_banned`. `isShadowBanned()`/`getShadowBanCount()` for staff querying. |

### Step 12.5 — A-5: Backup Code Verification

**Root cause**: 8 backup codes generated but no command to use them.

**Fix**: `/2fa verify-backup <code>` subcommand.

| # | Change | File | Details |
|---|--------|------|---------|
| 129 | Backup code verification | `TotpManager.java` | `verifyBackupCode()` parses comma-separated codes, checks membership via HashSet, removes used code, persists to MySQL+Redis via `saveRecord()`. |
| 130 | Backup code command | `TwoFactorCommand.java` | New `handleVerifyBackup()` — verifies code, unlocks player, warns to set up new 2FA. Shares rate limiter with TOTP verification. Added to help text + hasPermission(). |

### Step 12.6 — A-6: Alt List TTL Cleanup

**Root cause**: `lpush` without TTL — IP/fingerprint lists grew indefinitely.

**Fix**: 24h TTL on all tracking keys + scheduled cleanup.

| # | Change | File | Details |
|---|--------|------|---------|
| 131 | TTL on tracking keys | `AltDetector.java` | `recordLogin()` now calls `expire()` on both IP and fingerprint keys (24h TTL). |
| 132 | Redis set operations | `RedisManager.java` | Added `expire(key, seconds)`, `sadd(key, member)`, `sismember(key, member)`, `scard(key)` — all with try-with-resources + error handling. |
| 133 | Scheduled cleanup | `AuthVelocityPlugin.java` | `startCleanupTask()` runs daemon thread every 6h — logs shadow-ban count. Properly shut down in `onProxyShutdown()`. |

### Step 12.7 — A-7: PreLoginEvent UUID Fix

**Root cause**: Used `UUID.randomUUID()` — meaningless for alt detection.

**Fix**: Real UUID resolution via Redis username mapping.

| # | Change | File | Details |
|---|--------|------|---------|
| 134 | PreLoginEvent fix | `AuthVelocityPlugin.java` | Now uses `event.getUsername()` + `authManager.resolveUsername()` to find real UUID from Redis. Falls back to `UUID.randomUUID()` for first-time users (acceptable for early-warning check). |

### Step 12.8 — Code Review Fixes

Issues caught by code-reviewer-deepseek:

| # | Issue | Fix |
|---|-------|-----|
| CR-1 | SHADOW_BAN dead code — `check()` never returned SHADOW_BAN | Changed `onLogin()` to convert KICK→SHADOW_BAN based on config (not return value) |
| CR-2 | Help text missing `verify-backup` | Added to `/2fa` default help output |
| CR-3 | No rate limiting on backup codes | Shares TOTP rate limiter via `verifyTotpWithIpLock(uuid, 0, ...)` check before backup code verification |

### Step 12.9 — Live Verification

Tested on Velocity 4.1.0 + Paper 26.2 test servers:

```
[18:09:50] Plugin 'MDN-Auth' fully verified — signature valid, hash matches. ✅
[18:09:50] Plugin 'MDN-Auth' passed signature verification. ✅
[18:09:50] MDN-Auth enabled. ✅
[18:09:50]   Alt limits: 3 per IP, 2 per fingerprint (action: KICK)
[18:09:50]   Staff 2FA: enabled (2 permission groups, ip-lock: on)
[18:09:50]   Commands: /2fa (setup|verify|verify-backup|reset), /auth (unblock)
[18:09:50]   Alt list cleanup task scheduled (every 6h)
```

---

## Phase 13 — Cracked-Mode Password Authentication System (Commit: `a32ddaa`)

### Step 13.1 — Database Schema Expansion

| # | Change | File | Details |
|---|--------|------|---------|
| 135 | 3 new tables | `DatabaseSchema.java` | `mdn_accounts` (uuid, username, status ENUM, password_hash, password_version, timestamps), `mdn_backup_codes` (code_hash, single-use, FK→accounts), `mdn_password_resets` (token_hash, expiry, FK→accounts) |

### Step 13.2 — Argon2id Password Hashing

| # | Change | File | Details |
|---|--------|------|---------|
| 136 | PasswordHasher | `PasswordHasher.java` | `de.mkammerer:argon2-jvm:2.11`, Argon2id with 64 MiB/3 iterations/2 parallelism. `char[]` API with auto-clear (`Arrays.fill(data, '\0')`). `hash()` returns `$argon2id$v=19$...` encoded string. `verify()` checks against stored hash. `needsUpgrade()` for future parameter changes. |
| 137 | Argon2 dependency | `build.gradle.kts` | Added `de.mkammerer:argon2-jvm:2.11` implementation dependency |

### Step 13.3 — Session Management with AUTH_UPDATE

| # | Change | File | Details |
|---|--------|------|---------|
| 138 | SessionManager | `SessionManager.java` | Redis-backed sessions (30-min TTL). Cryptographically random session IDs (32 bytes, Base64URL). `createSession()` → revoke old → store new → `publishAuthUpdate(true)`. `revokeAllSessions()` on disconnect/password change/suspend. Login lock: ConcurrentHashMap + Redis dual-layer (prevents TOCTOU race). Session states: AUTHENTICATED. |

### Step 13.4 — /register Command

| # | Change | File | Details |
|---|--------|------|---------|
| 139 | RegisterCommand | `RegisterCommand.java` | `/register <password>` — 12-char minimum, 128 max, username-as-password rejection, common password rejection. Validates → Argon2id hash → INSERT into `mdn_accounts` with ACTIVE status → auto-authenticate. Help text with registration instructions. No permission required. |

### Step 13.5 — /login Command

| # | Change | File | Details |
|---|--------|------|---------|
| 140 | LoginCommand | `LoginCommand.java` | `/login <password>` — loads hash from MySQL → Argon2id verify → check account status → if 2FA configured → TOTP_REQUIRED state → else → create session + AUTH_UPDATE(true) → lobby. Rate limiting: 5 fails/5 min per UUID+IP. Generic error messages (no account enumeration). Account suspension check. |

### Step 13.6 — AuthManager Account Management

| # | Change | File | Details |
|---|--------|------|---------|
| 141 | Account operations | `AuthManager.java` | Added: `isRegistered()`, `register()`, `verifyPassword()`, `isTotpRequired()` (now checks force-2FA permissions), `createAuthenticatedSession()`, `hasActiveSession()`, `revokeAllSessions()`, `isLoginRateLimited()`, `recordFailedLogin()`, `suspendAccount()`, `unsuspendAccount()`, `changePassword()`. `RegistrationResult` + `LoginResult` enums. `PasswordHasher` + `SessionManager` subsystems. MySQL datasource wired. |

### Step 13.7 — AuthVelocityPlugin State Machine

| # | Change | File | Details |
|---|--------|------|---------|
| 142 | Auth state machine | `AuthVelocityPlugin.java` | `onLogin()` now checks `isRegistered()` → applies registration-required or password-required state. `applyRegistrationRequiredState()` shows title + action bar prompting /register. `applyPasswordRequiredState()` prompts /login. `applyTotpRequiredState()` locks for 2FA. `removeLockdown()` now creates session + publishes AUTH_UPDATE. Login timeout task disconnects after 120s. MySQL config loading. MDNAPI datasource wiring. |
| 143 | Command registration | `AuthVelocityPlugin.java` | `/register`, `/login`, `/2fa`, `/auth` — all 4 registered. `/login` passes force-2FA checker for staff bypass prevention. `/auth` passes player resolver for suspend/unsuspend. |

### Step 13.8 — /auth suspend + unsuspend

| # | Change | File | Details |
|---|--------|------|---------|
| 144 | Account suspension | `AuthCommand.java` | `/auth suspend <player>` — resolves UUID via playerResolver → sets SUSPENDED in MySQL → revokes all sessions → publishes AUTH_UPDATE(false). `/auth unsuspend <player>` — sets ACTIVE. Permission upgraded to `mdn.auth.admin`. Player resolver wired from AuthVelocityPlugin. |

### Step 13.9 — Config Updates

| # | Change | File | Details |
|---|--------|------|---------|
| 145 | Expanded config | `config.yml` | Added `mysql` section (host, port, database, user, password). Added `auth.password` (min-length: 12, max-length: 128, require-in-cracked-mode). Added `auth.session` (ttl-seconds: 1800, duplicate-policy: REPLACE_OLD). Added `auth.login-timeout-seconds: 120`. |

### Step 13.10 — Code Review Fixes

Issues caught by code-reviewer-deepseek and fixed before commit:

| # | Issue | Fix |
|---|-------|-----|
| CR-4 | Staff 2FA bypass — `isTotpRequired()` only checked TOTP config, not permissions | Added `BooleanSupplier hasForcePermission` parameter — now checks both |
| CR-5 | Login lock TOCTOU race — `get()` then `setWithExpiry()` not atomic | Added `ConcurrentHashMap<UUID, String>` for in-memory fast-path check + Redis for cross-proxy safety |
| CR-6 | AuthCommand can't find online players — resolver always returned empty | Wired `createAuthCommand(playerResolver)` — Velocity ProxyServer now resolves online players |
| CR-7 | Fingerprint `substring(0,12)` still in AltDetector debug logs | Changed to `"present"` per spec §124 |
| CR-8 | AuthCommand permissions too broad — `mdn.auth.admin.unblock` also grants suspend | Added `mdn.auth.admin` permission check |
| CR-9 | No login timeout enforcement | Added `startLoginTimeoutTask()` — Velocity scheduler, 5s interval, disconnects after configurable seconds |

### Step 13.11 — Build Verification

```
mdn-auth:compileJava ✅ (deprecation warning only)
mdn-auth:shadowJar ✅ (4.0MB, signature: 75b60a2f...)
mdn-api:build ✅
mdn-bridge:build ✅
mdn-core:build ✅
All 4 plugins build + signature verified ✅
```

---

## Phase 14 — /auth clear + Documentation Update (Commits: `bd27a16` + upcoming)

### Step 14.1 — /auth clear <ip> Command

| # | Change | File | Details |
|---|--------|------|---------|
| 146 | AltDetector.clearIp() | `AltDetector.java` | Deletes `mdn:auth:alt:ip:<ip>` key + `mdn:auth:unblocked:<ip>` whitelist entry. Returns UUID count for feedback. |
| 147 | AuthCommand.handleClear() | `AuthCommand.java` | `/auth clear <ip>` — IPv4 validation, calls clearIp(), shows count. Difference from unblock: clear WIPES data; unblock only WHITELISTS. |

### Step 14.2 — COMMANDS.md

| # | Change | File | Details |
|---|--------|------|---------|
| 148 | Command reference | `documents/COMMANDS.md` | Complete reference: all 13 commands across mdn-core (10) + mdn-auth (10), with permissions, platforms, usage examples, error messages, flow diagrams. Command index by permission and by plugin. |

### Step 14.3 — All Docs Updated

| # | Change | Files | Details |
|---|--------|-------|---------|
| 149 | Phase 13 + 14 sections | `STEPS.md` | This section |
| 150 | Phase 13 + 14 entries | `TIMELINE.md` | Updated timeline, renumbered future phases (15-17), updated metrics |
| 151 | Round 11 section | `DIARY.md` | Password auth implementation + /auth clear + commands.md |
| 152 | Updated suggestions | `SUGGEST.md` | Moved suggestions to Implemented |
| 153 | Updated status | `ISSUES.md` | Resolved issues marked, new known gaps added |

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
| Phase 5 (Startup Fixes) | 1 | 1 | 6 | 0 |
| Phase 6 (Redis + Shadow Fix) | 1 | 0 | 4 | 0 |
| Phase 7 (Velocity JSON Dedup) | 1 | 0 | 2 | 0 |
| Phase 8 (Velocity Config Bootstrap) | 1 | 0 | 2 | 0 |
| Phase 9 (Handshake + Signature) | 1 | 0 | 8 | 0 |
| Phase 10 (Race + Hash Fixes) | 1 | 0 | 5 | 0 |
| Phase 11 (MDN-Auth Plugin #4) | 1 | 8 | 8 | 0 |
| Phase 12 (MDN-Auth Gap Fixes) | 1 | 0 | 7 | 0 |
| Phase 13 (Password Auth System) | 1 | 4 | 7 | 1 |
| Phase 14 (Commands + Docs) | 2 | 1 | 8 | 0 |
| **Total** | **17** | **107** | **82** | **13** |
