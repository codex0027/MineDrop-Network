# MineDrop Network — Issues & Improvement Audit

> **Purpose**: Every issue, bug, gap, code smell, and improvement opportunity found during deep analysis.  
> **Status**: NOTHING is fixed in this document — it's purely findings. Fixes happen separately.  
> **Last Deep Audit**: August 4, 2026  
> **Files Audited**: 30 source files across mdn-api, mdn-bridge, mdn-core

---

## 📊 Summary

| Severity | Count | Description |
|----------|-------|-------------|
| 🔴 Critical | 3 | Will cause crashes, data loss, or security holes at runtime |
| 🟠 High | 12 | Will cause bugs under specific conditions |
| 🟡 Medium | 14 | Code quality, maintainability, or edge case issues |
| 🟢 Low | 7 | Nice-to-have improvements, not urgent |
| **Total** | **36** | |

---

## 🔴 Critical (Will Crash or Lose Data)

### C-1 — `MDNAPI.createStandalone()` breaks `getInstance()`
**File**: `mdn-api/.../MDNAPI.java` — Line 107  
**Problem**: `createStandalone()` creates a new MDNAPI instance and sets `initialized = true`, but **never assigns it to the `instance` field**. Any subsequent call to `getInstance()` will see `instance == null` and throw `IllegalStateException`.  
**Impact**: All unit tests using `createStandalone()` will fail if they call `getInstance()` afterwards.  
**Fix direction**: Add `instance = api;` before returning.

### C-2 — `CorePaperPlugin` overwrites `packetDispatcher` field
**File**: `mdn-core/.../CorePaperPlugin.java` — Lines 89-91  
**Problem**: `packetDispatcher` is initialized with `new PacketDispatcher()` before line 89, then the Dead Letter Queue is wired to it. But on line 91, `packetDispatcher = new PacketDispatcher()` **replaces** it with a fresh instance that has NO Dead Letter Queue wired.  
**Impact**: The DLQ is wired to a dispatcher that gets garbage collected. The actual dispatcher used for Redis subscriptions has no DLQ — all handler exceptions silently fail.  
**Fix direction**: Remove the duplicate initialization on line 91.

### C-3 — `DeadLetterQueue.enqueue()` produces invalid JSON
**File**: `mdn-core/.../DeadLetterQueue.java` — Line 77  
**Problem**: `String.format("{\"json\":%s,...}", rawJson, ...)` — the raw JSON is inserted unescaped. If `rawJson` contains `"` characters, the resulting string is NOT valid JSON. The `extractJson()` method tries to parse this broken format with substring operations.  
**Impact**: DLQ entries are corrupt. The retry logic will fail to parse them. Packets are silently lost forever.  
**Fix direction**: Use Jackson `ObjectMapper` to build the DLQ entry properly, or at minimum `StringEscapeUtils.escapeJson(rawJson)`.

---

## 🟠 High (Will Cause Bugs Under Specific Conditions)

### H-1 — `Thread.sleep(3000)` blocks main thread during handshake
**File**: `mdn-bridge/.../BridgePaperPlugin.java` — Line 126  
**Problem**: `performHandshakeWithRetries()` calls `Thread.sleep(3000)` between retries. This is called from `onEnable()` which runs on the **server main thread**. The entire server freezes for 3 seconds per retry.  
**Impact**: Server hangs for up to 9 seconds on startup if handshake fails. TPS drops to 0 during this time.  
**Fix direction**: Use `Bukkit.getScheduler().runTaskLater()` or `CompletableFuture` with delayed retry.

### H-2 — Velocity config parsing is naive string splitting
**File**: `mdn-bridge/.../BridgeVelocityPlugin.java` — Lines 88-99 AND `mdn-core/.../CoreVelocityPlugin.java` — Lines 153-162  
**Problem**: Both Velocity plugins parse YAML configs by splitting on `:` and trimming. This breaks on: nested keys (`redis:\n  host:`), values containing `:`, comments after values, quoted strings with spaces.  
**Impact**: Config values silently parsed incorrectly. Wrong Redis host, missing password.  
**Fix direction**: Use a proper YAML parser (SnakeYAML comes with Velocity) or Velocity's built-in config adapter.

### H-3 — Handshake is still self-validated, not cross-server
**File**: `mdn-bridge/.../BridgePaperPlugin.java` — Lines 113-114  
**Problem**: `performHandshakeWithRetries()` generates the challenge AND computes the response locally via `bridgeManager.computeHandshakeResponse(challenge)`. Then validates it against itself. The Redis roundtrip to Velocity is **never actually performed**.  
**Impact**: The entire handshake security mechanism is bypassed. Any Paper server with the secret key can self-validate.  
**Fix direction**: Publish challenge to Redis channel, await response from Velocity on a separate response channel with a timeout.

### H-4 — `sendDiscordAlert()` never actually sends HTTP
**File**: `mdn-bridge/.../BridgeManager.java` — Lines 335-348  
**Problem**: The method builds a JSON payload but only `log.info(...)`. The comment says "In production, use java.net.http.HttpClient to POST to webhook" — meaning the alert is **never actually sent**.  
**Impact**: Security alerts are silently dropped. Staff never gets notified of plugin verification failures.  
**Fix direction**: Implement actual HTTP POST using `java.net.http.HttpClient`.

### H-5 — `PlayerCache` crashes if Redis is down
**File**: `mdn-core/.../PlayerCache.java` — Line 72  
**Problem**: `localCache.computeIfAbsent(uuid, this::loadFromRedis)` — `loadFromRedis` calls `redisManager.get()` which may throw if Redis is unreachable. `computeIfAbsent` does not catch exceptions.  
**Impact**: Any call to `getPlayer()` crashes with an unhandled exception when Redis is down.  
**Fix direction**: Wrap `loadFromRedis` in try/catch, return default on failure.

### H-6 — `DatabaseManager` may NPE on shutdown
**File**: `mdn-core/.../DatabaseManager.java` — Shutdown method  
**Problem**: If `DatabaseManager` constructor throws (e.g. DB unreachable), `dataSource` is never assigned. But `shutdown()` calls `dataSource.isClosed()` without null check.  
**Impact**: NullPointerException during plugin disable, potentially blocking clean shutdown.  
**Fix direction**: Add null check before accessing dataSource in shutdown.

### H-7 — Velocity `onLogin` hardcodes server as "lobby"
**File**: `mdn-core/.../CoreVelocityPlugin.java` — Line 132  
**Problem**: `sessionManager.createSession(player.getUniqueId(), player.getUsername(), "lobby")` — always sets server to "lobby" even if the player was routed to a different server.  
**Impact**: Session tracking is inaccurate. If a player joins directly to a game server, the session says "lobby".  
**Fix direction**: Use the actual server the player is connecting to, or leave as null until ServerConnectedEvent fires.

### H-8 — `RedisManager.subscriberThreads.shutdownNow()` may cause data loss
**File**: `mdn-core/.../RedisManager.java` — Shutdown method  
**Problem**: `shutdownNow()` interrupts running threads immediately. If a subscriber thread was in the middle of processing a message, that message is lost.  
**Impact**: In-flight Redis messages silently dropped during shutdown.  
**Fix direction**: Use `shutdown()` + `awaitTermination(5, SECONDS)` instead of `shutdownNow()`.

### H-9 — Step numbering bug in CorePaperPlugin
**File**: `mdn-core/.../CorePaperPlugin.java` — Lines 91-96  
**Problem**: There are TWO "Step 8" comments. Step 7 initializes DLQ, then the next block says "Step 8: Initialize subsystems" and the block after ALSO says "Step 8: Start periodic tasks".  
**Impact**: Confusing for new developers reading the startup sequence.  
**Fix direction**: Renumber properly (Step 7, Step 8, Step 9...).

### H-10 — `InventorySyncManager` produces potentially broken JSON
**File**: `mdn-core/.../InventorySyncManager.java` — Line 49  
**Problem**: `"{\"inv\":\"" + inventoryBase64 + "\",\"ec\":\"" + enderChestBase64 + "\"}"` — this string concatenation produces invalid JSON if the base64 strings contain `"` characters (possible with certain Base64 implementations).  
**Impact**: Corrupt inventory data in MySQL. Player loses their items on next login.  
**Fix direction**: Use Jackson `ObjectMapper` to serialize a proper JSON object.

### H-11 — `ServerRegistry` health scoring doesn't exclude unhealthy servers
**File**: `mdn-core/.../ServerRegistry.java` — `findBestLobby()`  
**Problem**: The filter chain checks `s.isHealthy()`, but `isHealthy()` only checks TPS >= 17. Servers with TPS > 17 but packet loss or high latency are still considered "healthy".  
**Impact**: Players may be routed to laggy servers.  
**Fix direction**: Add latency/response-time check to `isHealthy()`.

### H-12 — `BridgeManager.readAndParseSignature()` reads entire JAR signature file synchronously
**File**: `mdn-bridge/.../BridgeManager.java` — Line 274  
**Problem**: `is.readAllBytes()` loads the entire signature.json into memory. While small, this is called during `onLoad()` on the main thread.  
**Impact**: Minor startup delay. Not severe since signature files are tiny.  
**Fix direction**: Already acceptable for signature files (< 1KB), but noted for completeness.

---

## 🟡 Medium (Code Quality, Maintainability, Edge Cases)

### M-1 — `@JsonIgnoreProperties` missing on packet subclasses
**Files**: All 8 packet files in `mdn-api/.../packet/`  
**Problem**: Only the base `MDNPacket` has Jackson annotations. If a future API version adds a new field to `EconomySyncPacket`, old servers receiving the new JSON will crash on `FAIL_ON_UNKNOWN_PROPERTIES`.  
**Impact**: Forward compatibility is partially broken. Adding a field to a packet breaks old servers.  
**Fix direction**: Add `@JsonIgnoreProperties(ignoreUnknown = true)` to each packet subclass, or configure globally on the ObjectMapper (already done, but verify it applies to subclasses through @JsonTypeInfo).

### M-2 — `SecurityUtil` instantiates MessageDigest on every call
**File**: `mdn-api/.../SecurityUtil.java` — Lines 30, 44, 67, 87  
**Problem**: `MessageDigest.getInstance("SHA-256")` is called on every `sha256Hex()`, `hmacSha256()`, `encryptAes()`, `decryptAes()` call. MessageDigest is not thread-safe so caching requires ThreadLocal.  
**Impact**: Minor performance overhead. Not critical but measurable at scale.  
**Fix direction**: Use ThreadLocal<MessageDigest> for caching.

### M-3 — `DatabaseSchema` missing migration tracking table
**File**: `mdn-api/.../DatabaseSchema.java`  
**Problem**: No `mdn_schema_migrations` table defined. When the DB migration framework is built, there's no table to track applied versions.  
**Impact**: Migration framework will need to create its own table, fragmenting schema management.  
**Fix direction**: Add a `mdn_schema_migrations (version VARCHAR, applied_at TIMESTAMP)` table.

### M-4 — Packet subclasses don't override `toString()`
**Files**: All 8 packet files  
**Problem**: No `toString()` override. Debugging/logging packets shows `ClassName@hashCode` instead of the packet content.  
**Impact**: Harder to debug. Logs are less useful.  
**Fix direction**: Add `@Override public String toString()` to each packet returning key fields.

### M-5 — No `@Nullable`/`@NotNull` annotations on public API methods
**Files**: `MDNAPI.java`, `BridgeManager.java`, all managers  
**Problem**: Public methods like `getDataSource()`, `getJedisPool()` return null in standalone mode but there's no `@Nullable` annotation. IDEs can't warn about potential NPEs.  
**Impact**: New developers may call these methods without null checks.  
**Fix direction**: Add `@Nullable` to methods that may return null, `@NotNull` to methods that never do.

### M-6 — `CircuitBreaker` doesn't differentiate error types
**File**: `mdn-core/.../CircuitBreaker.java` — `onFailure()`  
**Problem**: All exceptions trigger the failure counter. A `SocketTimeoutException` (transient) counts the same as `AccessDeniedException` (permanent).  
**Impact**: Permanent errors trip the circuit breaker unnecessarily, delaying recovery.  
**Fix direction**: Add a `Predicate<Exception>` parameter for "retryable vs non-retryable" errors.

### M-7 — `DeadLetterQueue.extractJson()` substring parsing is fragile
**File**: `mdn-core/.../DeadLetterQueue.java` — Lines 158-167  
**Problem**: Uses `indexOf("\"json\":")` and `indexOf(",\"error\"")` with substring math. Any format change in the DLQ entry breaks this.  
**Impact**: If the DLQ entry format is ever changed, extraction silently fails and packets are discarded.  
**Fix direction**: Use Jackson to serialize/deserialize a proper `DLQEntry` object instead of string hacking.

### M-8 — No packet for `PLAYER_KICK` or `SERVER_SHUTDOWN`
**Files**: `mdn-api/.../packet/`  
**Problem**: Missing packet types for common network events: player kick (with reason), server graceful shutdown notification.  
**Impact**: These events can't be broadcast across the network. Other servers don't know when a player was kicked.  
**Fix direction**: Add `PlayerKickPacket` and `ServerShutdownPacket`.

### M-9 — `SessionManager` has no session timeout
**File**: `mdn-core/.../SessionManager.java`  
**Problem**: Sessions are created on login but only removed on explicit disconnect. If a player's connection drops without a clean disconnect event, the session leaks forever.  
**Impact**: Memory leak. Ghost sessions accumulate over time.  
**Fix direction**: Add a scheduled task that removes sessions older than N minutes without activity.

### M-10 — `CoreVelocityPlugin` health command uses mixed color codes
**File**: `mdn-core/.../CoreVelocityPlugin.java` — Line 233  
**Problem**: `"§aconnected"` uses legacy `§` color codes inside a `Component.text()` call. The rest of the codebase uses `NamedTextColor`.  
**Impact**: Inconsistent styling. The `§` codes may not render correctly in newer Minecraft versions.  
**Fix direction**: Replace with `NamedTextColor.GREEN`.

### M-11 — `DataSyncEngine.savePlayerProfile()` swallows SQL exceptions
**File**: `mdn-core/.../DataSyncEngine.java` — Line 94  
**Problem**: The CompletableFuture catches SQLException, logs it, and dumps to crash buffer... but it returns a `CompletableFuture<Void>` that **always succeeds**. The caller has no way to know the save failed.  
**Impact**: Callers think saves succeeded when they didn't. Silent data loss.  
**Fix direction**: Return `CompletableFuture<Boolean>` or use `exceptionally()` to propagate errors.

### M-12 — `RedisManager.lpush/rpop/llen` have no circuit breaker protection
**File**: `mdn-core/.../RedisManager.java` — Lines 139-158  
**Problem**: These DLQ-specific methods bypass the circuit breaker. If Redis is down, they throw uncaught exceptions.  
**Impact**: DLQ processing crashes when Redis is unavailable.  
**Fix direction**: Wrap in circuit breaker or try/catch with fallback.

### M-13 — `PacketDispatcher` only supports one handler per packet type
**File**: `mdn-core/.../PacketDispatcher.java` — Line 38  
**Problem**: `handlers.put(packetType, handler)` — only one handler per type. If MDN-Economy AND MDN-Social both want to listen for `ECONOMY_SYNC`, the second registration silently overwrites the first.  
**Impact**: Hard-to-debug issues where some plugins stop receiving packets.  
**Fix direction**: Use `Map<String, List<Consumer<MDNPacket>>>` for multi-handler support.

### M-14 — `ServerInfo` class uses public fields with getters/setters inconsistently
**File**: `mdn-core/.../ServerRegistry.java` — `ServerInfo` class  
**Problem**: Fields are private with getters/setters, but the constructor and other code constructs `ServerInfo` objects with empty constructor + setters. Some flows use the parameterized constructor.  
**Impact**: Inconsistent initialization pattern. Easy to forget setting a required field.  
**Fix direction**: Use a Builder pattern or make the full constructor the only way to create valid ServerInfo objects.

---

## 🟢 Low (Nice-to-Have, Not Urgent)

### L-1 — No `package-info.java` files for API documentation
**Files**: All packages in mdn-api  
**Problem**: Javadoc at package level is missing. IDE's "package summary" view is empty.  
**Impact**: Minor documentation gap.  
**Fix direction**: Add `package-info.java` with `@PackageDocumentation` annotations.

### L-2 — Lombok could reduce boilerplate significantly
**Files**: `ServerInfo`, `CachedPlayer`, `PlayerSession`, all packet classes  
**Problem**: Each class has 30-50 lines of manual getters/setters. Lombok `@Data` or `@Getter @Setter` would reduce this to 2 lines.  
**Impact**: More code to maintain, more places for bugs.  
**Fix direction**: Apply `@Getter @Setter` to data classes. Lombok is already a dependency.

### L-3 — `PlayerCache.CachedPlayer` getter naming is inconsistent
**File**: `mdn-core/.../PlayerCache.java` — Lines 147-148  
**Problem**: `isHasActiveBoost()` should be `hasActiveBoost()` per JavaBean convention (Lombok would generate the correct name).  
**Impact**: Confusing for developers using reflection or property-based access.  
**Fix direction**: Rename to `hasActiveBoost()` or use Lombok `@Getter`.

### L-4 — No validation on `ServerHeartbeatPacket` senderId
**File**: `mdn-core/.../CorePaperPlugin.java` — Line 216  
**Problem**: `ServerHeartbeatPacket` is created with `null` senderId. The base `MDNPacket` constructor requires a UUID, so this works... but the correlation ID generation checks `MDNAPI.isInitialized()` which may throw if API not yet ready.  
**Impact**: If heartbeat fires before MDNAPI is initialized, it throws.  
**Fix direction**: Guard the heartbeat task with an `isInitialized()` check.

### L-5 — `DatabaseSchema` hardcodes MySQL-specific syntax
**File**: `mdn-api/.../DatabaseSchema.java`  
**Problem**: `ON UPDATE CURRENT_TIMESTAMP`, `BOOLEAN`, `TEXT` are MySQL-specific. Migration to PostgreSQL would require rewriting all DDL.  
**Impact**: Database portability is zero.  
**Fix direction**: Abstract DDL behind a dialect interface if multi-DB support is ever needed.

### L-6 — `extractValue()` in Velocity plugins could use `String.strip()`
**File**: `mdn-core/.../CoreVelocityPlugin.java` — Line 170  
**Problem**: Uses `.trim()` which handles ASCII whitespace only. `strip()` is Unicode-aware (Java 11+).  
**Impact**: Extremely minor — only matters if config files have Unicode whitespace (rare).  
**Fix direction**: Replace `.trim()` with `.strip()`.

### L-7 — `BridgeManager` overhead `register()` Javadoc
**File**: `mdn-bridge/.../BridgeManager.java` — Lines 117-126  
**Problem**: Two consecutive Javadoc blocks for the same method (one incomplete, then the real one).  
**Impact**: Javadoc rendering may be confusing.  
**Fix direction**: Remove the first incomplete Javadoc block.

---

## 📝 Summary by Plugin

| Plugin | 🔴 Critical | 🟠 High | 🟡 Medium | 🟢 Low | Total |
|--------|-----------|---------|----------|--------|-------|
| mdn-api | 1 | 0 | 4 | 2 | 7 |
| mdn-bridge | 0 | 3 | 1 | 1 | 5 |
| mdn-core | 2 | 9 | 9 | 4 | 24 |
| **Total** | **3** | **12** | **14** | **7** | **36** |

---

## 🔍 Code Smells Not Counted Above

These are patterns that aren't bugs but indicate design weaknesses:

- **God class forming**: `CorePaperPlugin` is 310 lines and growing. The `onEnable()` method does 10+ things. Consider extracting initialization into an `InfrastructureBootstrapper`.
- **Stringly-typed config**: Redis channels, permissions, and config keys are scattered as string literals. Centralize in `MDNCore` constants (already partially done, could go further).
- **Duplicate config parsing**: Both `BridgeVelocityPlugin` and `CoreVelocityPlugin` have nearly identical YAML parsing code. Extract to a shared `VelocityConfigParser`.
- **Magic numbers**: `100L` (heartbeat interval), `300` (save interval), `1200` (cache TTL), `20` (pool max), `5` (failure threshold), `30` (cooldown seconds) — these should be constants.
- **No integration tests**: All tests are unit tests. No test starts a real Redis or MySQL. The circuit breaker, DLQ, and cache behavior with actual Redis is untested.

---

*Audit completed August 4, 2026. 30 files analyzed. 36 issues found.*  
*Next step: Prioritize and fix starting with the 3 critical issues.*
