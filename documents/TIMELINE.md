# MineDrop Network — Development Timeline

> **Purpose**: Visual roadmap of everything we've built, are building, and will build.  
> **Last Updated**: August 11, 2026 — Lobby freeze system (AuthFreezeManager, AUTH_UPDATE on connect)

---

## 🗓️ Timeline Overview

```
2026-08-04 ───────────────────────────────────────────────────────► 2026-08-05 ──► Future

[Phase 0-4]   [Phase 5-6]    [Phase 7-8]        [Phase 9-10]    [Phase 11]
Foundation →  Startup Fix →  JSON+Config →  Handshake+Sig →  MDN-Auth #4 →  Gap Fixes
 (6.5 hours)   (2.5 hours)     (50 min)      (3 hours)        (2 hours)      (1.5 hours)
    ✅ Done       ✅ Done         ✅ Done        ✅ Done          ✅ Done         ✅ Done

         [Phase 13]         [Phase 14]         [Phase 15]        [Phase 16]
    Password Auth →  Commands+Docs →  Hardening+Recovery →  Lobby Freeze
      (3 hours)        (30 min)         (2 hours)          (1.5 hours)
        ✅ Done          ✅ Done           ✅ Done             ✅ Done

                                                                              ▼
                                                                     [Phase 17] ───► [Phase 18]
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

### Phase 11 — MDN-Auth Plugin #4 Implementation
**Duration**: ~2 hours  
**Date**: August 6, 2026  
**Commit**: `2a4a469` (16 files, +1,200/-50)

```
New plugin:
  ✅ AuthManager               (central coordinator, pre-auth lockdown)
  ✅ TotpManager               (RFC 6238 TOTP, QR codes, backup codes, ±1 drift)
  ✅ DeviceFingerprinter        (SHA-256: brand + protocol + IP prefix)
  ✅ AltDetector               (Redis IP/fingerprint tracking, whitelist)
  ✅ AuthVelocityPlugin         (6-step init, 4 event handlers, config bootstrap)
  ✅ TwoFactorCommand           (/2fa setup|verify|reset)
  ✅ AuthCommand                (/auth unblock <ip>)
  ✅ config.yml                 (matches design spec exactly)
  ✅ build.gradle.kts           (shadow JAR, signature, exclusions, relocate)

Build fixes:
  ✅ 6 skeleton plugin.yml expand() missing version
  ✅ 2 skeleton duplicate velocity-plugin.json deleted
  ✅ 2 stale expand blocks removed

Code review fixes:
  ✅ removeLockdown callback wired (player no longer stuck after 2FA)
  ✅ UUID removed from fingerprint (alt detection now works correctly)
  ✅ Dead lock code removed from AltDetector

Spec comparison:
  ⚠️ 7 gaps found (ISSUES.md A-1 to A-7): no SQL table, IP lock stub,
     /2fa reset stub, SHADOW_BAN unused, no backup code verify, alt list no TTL

Status: ✅ JAR 4.0MB, signature verified, all 4 plugins building
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

### Phase 12 — MDN-Auth Gap Fixes & Production Hardening
**Duration**: ~1.5 hours  
**Date**: August 11, 2026  
**Commit**: `afda633` (7 files, +638/-94)

```
All 7 spec comparison gaps fixed:
  ✅ A-1 MySQL persistence      (dual-write: MySQL source of truth, Redis cache)
  ✅ A-2 IP lock enforcement     (verifyCodeWithIpLock, rate limit 5/15min)
  ✅ A-3 Full /2fa reset         (ProxyServer + Redis username→UUID resolution)
  ✅ A-4 SHADOW_BAN              (KICK→SHADOW_BAN conversion, Redis set tracking)
  ✅ A-5 Backup code verify      (/2fa verify-backup, consumes code, shares rate limit)
  ✅ A-6 Alt list TTL            (24h expire on IP+FP keys, scheduled cleanup)
  ✅ A-7 PreLoginEvent UUID      (resolves real UUID from Redis username mapping)

Production enhancements beyond spec:
  ✅ Rate limiting               5 failed 2FA attempts → 15-min lockout
  ✅ Username→UUID mapping       Redis key with 30-day TTL for offline ops
  ✅ Scheduled cleanup           Daemon thread every 6h, proper shutdown
  ✅ Redis set operations        expire/sadd/sismember/scard with error handling

Code review fixes:
  ✅ SHADOW_BAN dead code       Fixed: onLogin() now converts KICK→SHADOW_BAN
  ✅ Help text                   Added /2fa verify-backup to command list
  ✅ Backup rate limiting        Shares TOTP rate limiter

Verified on live servers:
  [MDN-Auth] fully verified — signature valid, hash matches.
  [MDN-Auth] passed signature verification.
  [MDN-Auth] MDN-Auth enabled.
  [MDN-Auth] Alt limits: 3/2, Staff 2FA: enabled (2 groups, ip-lock: on)
  [MDN-Auth] Commands: /2fa (setup|verify|verify-backup|reset), /auth (unblock)

Status: ✅ All 7 gaps fixed, mdn-auth production-ready
```

---

### Phase 13 — Password Authentication System
**Duration**: ~3 hours  
**Date**: August 11, 2026  
**Commit**: `a32ddaa` (11 files, +1353/-67)

```
Major features:
  ✅ Argon2id password hashing  (64 MiB, 3 iterations, char[] API, auto-clear)
  ✅ mdn_accounts table         (uuid, status, password_hash, timestamps)
  ✅ /register command           (12-char min, validation, auto-auth)
  ✅ /login command              (rate-limited, 2FA transition, suspend check)
  ✅ SessionManager              (Redis sessions, AUTH_UPDATE, login locks)
  ✅ /auth suspend|unsuspend     (account admin commands)
  ✅ Auth state machine          (register→password→2FA→authenticated)
  ✅ Login timeout               (120s disconnect for abandoned connections)

New files: PasswordHasher.java, SessionManager.java, RegisterCommand.java, LoginCommand.java
Modified: AuthManager (+327), AuthVelocityPlugin (+208), AuthCommand (+95),
          DatabaseSchema (+78), config.yml, build.gradle.kts

Code review fixes:
  ✅ Staff 2FA bypass fixed     (isTotpRequired now checks force-2fa permissions)
  ✅ Login lock race fixed      (ConcurrentHashMap + Redis dual-layer)
  ✅ AuthCommand resolver       (playerResolver wired for online player lookup)
  ✅ Fingerprint safety         (substring→"present" in AltDetector logs)
  ✅ Permissions tightened      (mdn.auth.admin for suspend, separate from unblock)

Status: ✅ All 4 plugins building, 13 total commands registered
```

---

### Phase 14 — /auth clear + Commands Reference
**Duration**: ~30 minutes  
**Date**: August 11, 2026  
**Commits**: `bd27a16` (3 files, +69/-2), upcoming (docs)

```
New commands + docs:
  ✅ /auth clear <ip>           (wipes alt tracking data + removes whitelist)
  ✅ COMMANDS.md                (13 commands, permissions, usage, flow diagrams)
  ✅ All 5 docs updated         (STEPS, TIMELINE, DIARY, SUGGEST, ISSUES)

Status: ✅ Documentation complete — 9 markdown files in documents/
```

---

### Phase 15 — Spec Phase 5-7: Password Hardening, Recovery, Audit
**Duration**: ~2 hours  
**Date**: August 11, 2026  
**Commit**: `e84714c` (7 files, +558/-28)

```
All 6 remaining spec gaps implemented:
  ✅ /password change|reset     (4 reset methods: totp/backup/recovery)
  ✅ Paper AUTH_UPDATE          (PlayerMoveEvent blocker, fail-closed)
  ✅ TOTP secret encryption     (AES/GCM, env var key, legacy fallback)
  ✅ Backup code hash storage   (SHA-256, hash-compare on verify)
  ✅ Admin recovery flow        (/auth recovery, 15-min token)
  ✅ Audit logging              (mdn_auth_audit table, non-blocking)

New file: PasswordCommand.java (230 lines)
Modified: AuthManager (+95), TotpManager (+103), CorePaperPlugin (+55),
          AuthVelocityPlugin (+30), AuthCommand (+49), DatabaseSchema (+24)

Build fix:
  ✅ MDNAPI NPE guarded        (Core loads after Auth on Velocity)

Live verification on Velocity 4.1.0:
  [mdn-auth] Commands: /register, /login, /password, /2fa, /auth
  [mdn-auth] Password auth: enabled (Argon2id, min 12 chars)
  [mdn-auth] MDN-Auth enabled.

Status: ✅ All 157 spec points addressed, 17 commands live
```

---

### Phase 16 — Lobby Freeze System (Final Auth Architecture)
**Duration**: ~1.5 hours  
**Date**: August 11, 2026  
**Commit**: `bad7dbb` (6 files, +710/-36)

```
Implements the final authentication architecture (spec §1-114):
  ✅ AuthFreezeManager.java    (519 lines, 15 event handlers)
  ✅ Movement freeze            (lock X/Y/Z, allow yaw/pitch — no teleport spam)
  ✅ Damage protection          (EntityDamageEvent + by-entity)
  ✅ Block protection           (Break + Place)
  ✅ Interaction protection     (Interact + InteractEntity + InteractAtEntity)
  ✅ Inventory protection       (Open + Click + Drag)
  ✅ Item protection            (Drop + Pickup + Consume)
  ✅ Command whitelist          (register, login, password, 2fa, help only)
  ✅ Teleport protection        (only to auth spawn, lenient distance check)
  ✅ BossBar UX                 (Yellow bar with instructions)
  ✅ Title UX                   (join + unfreeze success messages)
  ✅ Auth timeout               (configurable, default 120s, async checker)
  ✅ Auto-freeze on join        (250ms delay for AUTH_UPDATE, fail-closed)

Velocity-side:
  ✅ AUTH_UPDATE(false) on LoginEvent    (lobby freezes player on connect)
  ✅ AUTH_UPDATE(false) on DisconnectEvent (lobby cleans up)
  ✅ Config: authentication.publish-on-connect: true

Critical safety fix:
  ✅ wasAlreadyAuth guard       (duplicate connection AUTH_UPDATE(false) does NOT
                                freeze authenticated player — spec §52-53)

Config:
  ✅ Paper config.yml           authentication section (spawn, freeze, timeout,
                                allowed-commands, messages, show-title)
  ✅ Velocity config-velocity.yml authentication.publish-on-connect

Code review: 5 issues found → all 5 fixed (1 critical, 2 medium, 2 low)

Verified: Proxy starts with auth-publish=true, 5 commands loading
Status: ✅ Code complete — requires MySQL for full lobby enable
```

---

## 🔜 Upcoming — Phase 17 (Planned)

**Target**: Next development session  
**Focus**: MDN-Security plugin #5 — Anti-cheat & exploit prevention

```
Planned work:
  🔜 MDN-Security plugin #5   Anti-cheat, anti-bot, anti-VPN, exploit prevention
  🔜 Packet validation         Rate limiting, suspicious pattern detection
  🔜 Machine fingerprinting    Advanced hardware ID (move from basic auth fingerprint)

Estimated effort: 2-3 hours
```

---

## 📋 Phase 18 — Future (Medium Term)

**Focus**: Rate Limiting + Graceful Degradation + Monitoring

```
Future work:
  📋 Rate Limiter              Per-IP/player limits on packet publishing
  📋 Graceful Degradation      Local-only mode when Redis is down
  📋 Local Event Bus           In-process pub/sub without Redis
  📋 Connection Pool Metrics   HikariCP stats in /mdn health
  📋 DB Migration Framework    Auto-run schema changes
  📋 Prometheus Metrics        /metrics endpoint, Grafana dashboards
  📋 Developer Debug Kit       /mdn debug packets, cache peek, Redis ping

Estimated effort: 4-6 hours
```

---

## 🌟 Phase 19 — Dreams (Long Term)

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
| Total commits | 20 |
| Total files created | 95 |
| Total source lines | ~6,200 |
| Total test lines | ~400 |
| Build errors encountered & fixed | 14 |
| Unit tests passing | 24/24 |
| Plugins fully implemented | 4 (mdn-api, mdn-bridge, mdn-core, mdn-auth) |
| Plugins as skeletons | 6 |
| Issues fixed (all time) | 58 |
| Suggestions cataloged | 67 |
| Documentation pages | 9 (README + 8 docs in documents/) |
| Total commands registered | 17 across 2 plugins |
| Spec gaps remaining | 0 (all 13 fixed — 7 auth gaps + 6 hardening) |
| Freeze event handlers | 15 (move, damage×2, block×2, interact×3, inventory×3, items×3, cmd, teleport) |

---

## 🏗️ Plugin Build Order (from design docs)

Per `plan/MineDrop/Plugin-making ranking.md`, the full build order is:

```
Phase 1 (Foundation) — ✅ DONE
  1. MDN-API       ✅ Implemented
  2. MDN-Bridge    ✅ Implemented
  3. MDN-Core      ✅ Implemented

Phase 2 (Global Services) — 🔜 NEXT
  4. MDN-Auth      ✅ Implemented (Aug 6, 2026)
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
