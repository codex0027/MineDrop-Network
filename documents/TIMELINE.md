# MineDrop Network — Development Timeline

> **Purpose**: Visual roadmap of everything we've built, are building, and will build.  
> **Last Updated**: August 6, 2026 — signature auto-gen + Velocity allowed-hashes + server eviction fix

---

## 🗓️ Timeline Overview

```
2026-08-04 ───────────────────────────────────────────────────────► 2026-08-05 ──► Future

[Phase 0-4]   [Phase 5-6]    [Phase 7-8]        [Phase 9]
Foundation →  Startup Fix →  JSON+Config →  Handshake+Signature
 (6.5 hours)   (2.5 hours)     (50 min)      (2 hours)
    ✅ Done       ✅ Done         ✅ Done        ✅ Done

                                                                              ▼
                                                                     [Phase 11] ───► [Phase 12]
                                                                      Planned         Dreams
```

---

## ✅ Completed

### Phase 0 — Analysis & Planning
**Duration**: ~30 minutes  
**Date**: August 4, 2026 (early)  
**Commit**: N/A (no code changes)

| What | Details |
|------|---------|
| Read all design docs | 15 markdown files in `plan/MineDrop/` |
| Architecture decisions | Monorepo, Gradle Kotlin DSL, Java 21, Shadow, full skeletons |
| Tech stack finalized | Paper 1.21.1, Velocity 3.3.0, MySQL + Redis, HikariCP + Jedis |

---

### Phase 1 — Monorepo Foundation
**Duration**: ~3 hours  
**Date**: August 4, 2026  
**Commit**: `9522d09` (70 files, 3,412 insertions)

```
Created:
  ✅ Root build system         (4 files: build.gradle.kts, settings, properties, gitignore)
  ✅ mdn-api                   (11 source files — packets, events, security, database)
  ✅ mdn-bridge                (5 source + 3 config files)
  ✅ mdn-core                  (10 source + 3 config files)
  ✅ 7 skeleton plugins        (14 source files — auth through sam)
  ✅ README.md                 (onboarding guide)
  ✅ .gitignore                (build exclusions)

Build fixes: 7 errors resolved (Java version, plugin application, shadow, duplicates)
Status: ✅ All 3 main plugins compiling
```

---

### Phase 2 — Production Hardening
**Duration**: ~2 hours  
**Date**: August 4, 2026  
**Commit**: Bundled with `9522d09`

```
Fixes applied:
  ✅ 12 critical bugs fixed    (JAR hashing, handshake, cache leak, dead servers, etc.)
  ✅ 6 security improvements   (AES/GCM, debug mode restriction, config validation, webhook)
  ✅ 11 missing features added (packets, events, dispatcher, commands, configs, heartbeat)
  ✅ 3 dev experience adds     (shutdown, isInitialized, standalone mode)

Build fixes: 4 errors resolved (jackson-jsr310, file naming, State visibility, task deps)
Status: ✅ Production-grade quality achieved
```

---

### Phase 3 — Resilience & Observability
**Duration**: ~1.5 hours  
**Date**: August 4, 2026  
**Commit**: `1687d56` (28 files, 2,035 additions)

```
New systems:
  ✅ Circuit Breaker           (CLOSED→OPEN→HALF_OPEN, 5 failures, 30s cooldown)
  ✅ API Versioning            (semver, compatibility checks, Bridge enforcement)
  ✅ Config Validation         (startup checks, health probes, fail-fast)
  ✅ Correlation IDs           (per-packet tracing, instance ID logging)

Tests:
  ✅ SecurityUtilTest          (8 tests — SHA, HMAC, AES roundtrip)
  ✅ ApiVersionTest            (9 tests — parsing, compatibility, comparison)
  ✅ CircuitBreakerTest        (7 tests — transitions, reset, rejection)

Status: ✅ 24/24 tests passing, all 3 plugins building
```

---

### Phase 4 — Dead Letter Queue & Documentation
**Duration**: ~1 hour  
**Date**: August 4, 2026  
**Commit**: `61c063a` (6 files, 914 additions)

---

### Phase 5 — Startup Lifecycle Fixes
**Duration**: ~1 hour  
**Date**: August 4, 2026  
**Commit**: `847745f` (6 files modified)

```
Critical fixes from real server logs:
  ✅ PacketDispatcher NPE           (reordered Steps 7-8 in CorePaperPlugin)
  ✅ CoreVelocity MDNAPI not init   (added MDNAPI.initialize() before PlayerCache)
  ✅ BridgeManager Jackson coupling (standalone ObjectMapper, removed MDNAPI dep)
  ✅ Redis health false-negatives   (dedicated healthCheckExecutor, not ForkJoinPool)
  ✅ onDisable() Redis thread leak  (added redisManager.shutdown())
  ✅ DEPLOY.md created              (complete Pterodactyl + VPS-direct guide)

Status: ✅ All 3 plugins compiling, real-server ready
```

---

### Phase 6 — Redis Connection Reset & Shadow JAR Build Fix
**Duration**: ~1.5 hours  
**Date**: August 4, 2026  
**Commit**: `dddaa76` (4 files, 23 insertions)

```
Critical fixes from real server logs:
  ✅ JedisPoolConfig validation     (testOnCreate/Borrow/WhileIdle, eviction timing)
  ✅ Shadow JAR overwrite on clean  (archiveClassifier="original" on jar task)
  ✅ MDN-Bridge NoClassDefFoundError(ded Jackson deps + build fix)
  ✅ Bridge JAR: 19KB → 4.0MB       (Jackson now properly bundled)

Status: ✅ Both JARs 4.0MB, all deps bundled
```

---

### Phase 7 — Duplicate velocity-plugin.json Fix
**Duration**: ~20 minutes  
**Date**: August 4, 2026 (night)  
**Commit**: `ae71f4f` (4 files, 4 insertions, 33 deletions)

```
Build quality fix:
  ✅ Deleted manual velocity-plugin.json templates (both bridge + core)
  ✅ Velocity annotation processor now sole source of truth
  ✅ jar tf | grep velocity-plugin → 1 entry (was 2)
  ✅ Removed processResources expand blocks for velocity-plugin.json

Status: ✅ Clean build — no duplicate resources
```

```
New systems:
  ✅ Dead Letter Queue         (5 retries, exponential backoff 1s→16s, permanent DLQ)
  ✅ Operation Timeouts        (DB: 10s, Redis: 5s, CompletableFuture wrapping)
  ✅ DLQ wired to dispatcher   (handler exceptions auto-enqueue)

Documentation:
  ✅ DIARY.md                  (500+ line development journal)
  ✅ STEPS.md                  (72-step chronological log)
  ✅ SUGGEST.md                (41 suggestions cataloged)
  ✅ TIMELINE.md               (this file)

Build fixes: 1 error resolved (effectively final lambda variable)
Status: ✅ Complete Phase 4
```

---

---

### Phase 11 — Signature Auto-Gen + Eviction Fix (August 6, 2026)

```
Fixes:
  ✅ signature.json auto-gen      (finalizedBy link for both bridge + core shadowJar)
  ✅ Velocity allowed-build-hashes (BridgeVelocityPlugin now reads config list)
  ✅ Server eviction fix           (discoverServers no longer pre-registers)
  ✅ Duplicate register removed    (BridgeVelocityPlugin had double self-registration)
  ✅ Startup script rewrite        (setsid + Java 25 + wait loops + log capture)
  ✅ mdn-core signature.json      (now generated alongside mdn-bridge)

Verified on live servers: Paper 26.2 + Velocity 4.1.0
```

---

### Phase 10 — Handshake Race Fix & Signature Hash Fix
**Duration**: ~1 hour  
**Date**: August 5, 2026  
**Commit**: `de86137` (4 files, +79/-26)

```
Fixes:
  ✅ Handshake race fixed           (2s initial delay → SUCCESS on attempt 1)
  ✅ Signature hash matching fixed  (sorted-entry hashing + Python ZIP_STORED injection)
  ✅ MDN-Core self-registration     (BridgeManager.register in onLoad)
  ✅ Both plugins verified           (debug-mode: false, real hashes — both passed)

Verified on live servers: Paper 26.2 + Velocity 4.1.0
```

---

### Phase 9 — Cross-Server Handshake & Signature Verification
**Duration**: ~2 hours  
**Date**: August 5, 2026  
**Commit**: `ed69f5d` (8 files, +377/-30)

```
Major features:
  ✅ Cross-server handshake       (Redis Pub/Sub: Paper challenge → Velocity HMAC → Paper validate)
  ✅ HandshakeTransport interface (avoids circular mdn-bridge → mdn-core dependency)
  ✅ Build-time signature.json    (Gradle generateSignature task, ZIP entry hash skipping signature)
  ✅ computeJarHash() fixed       (Now mirrors build-time hash — skips signature.json in ZIP)
  ✅ ClassLoader conflict fixed   (mdn-core shadowJar excludes net/minedrop/bridge/**)
  ✅ End-to-end tested            (Paper 26.2 + Velocity 4.1.0 — handshake VERIFIED)

Handshake flow: Paper → Redis(mdn:bridge:handshake) → Velocity → HMAC → Redis(mdn:bridge:handshake:response) → Paper
Status: ✅ Production-ready — session token established
```

---

### Phase 8 — Velocity Config Bootstrap Fix (Previous)
**Duration**: ~30 minutes  
**Date**: August 4, 2026 (night)  
**Commit**: `81da386` (2 files, 89 insertions, 5 deletions)

```
Critical fix from real server logs:
  ✅ saveDefaultConfig() for Velocity     (no data dir was ever created)
  ✅ config-velocity.yml → config.yml     (copied from JAR on first startup)
  ✅ routing.default-region support       (Velocity config layout was different)
  ✅ plugins/mdn-*/ folders now created   (mirrors Paper behavior)
  ✅ Unused imports cleaned               (PluginManager, CommandMeta)

Status: ✅ Config now works on Velocity exactly like Paper
```

---

## 🔜 Upcoming — Phase 11 (Planned)

**Target**: Next development session  
**Focus**: Rate Limiting + Graceful Degradation

```
Planned work:
  🔜 Rate Limiter              Per-IP/player limits on packet publishing
  🔜 Graceful Degradation      Local-only mode when Redis is down
  🔜 Local Event Bus           In-process pub/sub without Redis
  🔜 Connection Pool Metrics   HikariCP stats in /mdn health
  🔜 DB Migration Framework    Auto-run schema changes

Estimated effort: 2-3 hours
```

---

## 📋 Phase 12 — Future (Medium Term)

**Focus**: Monitoring + Developer Tools

```
Future work:
  📋 Prometheus Metrics        /metrics endpoint, Grafana dashboards
  📋 Developer Debug Kit       /mdn debug packets, cache peek, Redis ping
  📋 Packet Batching           Queue + flush every 50ms for performance
  📋 Hot-Reload Safety         Clean resource cleanup on reload
  📋 Structured Logging        JSON format for ELK/Splunk

Estimated effort: 4-6 hours
```

---

## 🌟 Phase 13 — Dreams (Long Term)

**Focus**: Scale + High Availability

```
Dream features:
  🌟 Multi-Region Support      EU/NA/ASIA with latency-based routing
  🌟 Redis Sentinel/Cluster    High availability Redis
  🌟 MySQL Read Replicas       Separate read/write connections
  🌟 Webhook Notifications     Discord/Slack alerts for key events
  🌟 Admin Dashboard API       REST endpoints for web panel

Estimated effort: 8-12 hours (spread across weeks)
```

---

## 📊 By the Numbers

| Metric | Count |
|--------|-------|
| Total commits | 11 |
| Total files created | 94 |
| Total source lines | ~5,700 |
| Total test lines | ~400 |
| Build errors encountered & fixed | 13 |
| Unit tests passing | 24/24 |
| Plugins fully implemented | 3 (mdn-api, mdn-bridge, mdn-core) |
| Plugins as skeletons | 7 |
| Suggestions cataloged | 49 |
| Documentation pages | 8 (README + 7 docs in documents/) |

---

## 🏗️ Plugin Build Order (from design docs)

Per `plan/MineDrop/Plugin-making ranking.md`, the full build order is:

```
Phase 1 (Foundation) — ✅ DONE
  1. MDN-API       ✅ Implemented
  2. MDN-Bridge    ✅ Implemented
  3. MDN-Core      ✅ Implemented

Phase 2 (Global Services) — 🔜 NEXT
  4. MDN-Auth      ◻ Skeleton ready
  5. MDN-Security  ◻ Skeleton ready
  6. MDN-Economy   ◻ Skeleton ready
  7. MDN-Social    ◻ Skeleton ready
  8. MDN-Communication ◻ Skeleton ready
  9. MDN-Maintenance   ◻ Skeleton ready

Phase 3 (Staff) — 📋 FUTURE
  10. MDN-Moderation   ◻ Skeleton ready

Phase 4 (Gameplay) — 📋 FUTURE
  11. MDN-SAM         ◻ Skeleton ready
```

---

*Timeline updated every development session. New phases added as work progresses.*
