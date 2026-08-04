# MineDrop Network — Suggestions & Enhancement Catalog

> **Purpose**: Every idea, suggestion, and enhancement — whether implemented, planned, or deferred — is cataloged here.  
> **For**: Future planning, prioritizing work, and ensuring no good idea gets lost.  
> **Last Updated**: August 4, 2026 (night — Velocity config bootstrap fix)

---

## 📊 Summary

| Status | Count |
|--------|-------|
| ✅ Implemented | 25 |
| 🔜 Planned (Near Future) | 5 |
| 📋 Future Consideration | 10 |
| ❌ Rejected / Deferred | 2 |
| **Total** | **44** |

> **📋 See also**: [ISSUES.md](ISSUES.md) — 36 bugs, gaps, and code smells found during deep audit (Aug 4, 2026)

---

## ✅ Implemented

These suggestions have been fully implemented and are in the codebase.

### Critical Bug Fixes

| # | Suggestion | Source | Implemented In |
|---|-----------|--------|---------------|
| 1 | Fix `computeJarHash()` — hash actual JAR bytes, not file path | Code review | `BridgeManager.java` (Step 18) |
| 2 | Parse `signature.json` content, not just check file exists | Code review | `BridgeManager.java` (Step 19) |
| 3 | Thread-safe `getInstance()` in BridgeManager | Code review | `BridgeManager.java` (Step 20) |
| 4 | Handshake retry buffer (3 retries × 3s spacing) | Design doc | `BridgePaperPlugin.java` (Step 21) |
| 5 | Disable plugin on verification failure (not just log) | Code review | `BridgeManager.java` + `BridgePaperPlugin.java` (Step 22) |
| 6 | Debug mode restricted to localhost only | Design doc | `BridgePaperPlugin.java` (Step 23) |
| 7 | ServerRegistry heartbeat timeout (dead server eviction) | Code review | `ServerRegistry.java` (Step 27) |
| 8 | PlayerCache TTL eviction (prevent memory leak) | Code review | `PlayerCache.java` (Step 29) |
| 9 | Cancellable Redis subscriptions (thread leak) | Code review | `RedisManager.java` (Step 30) |
| 10 | `saveAll()` real implementation (was no-op) | Code review | `DataSyncEngine.java` (Step 31) |
| 11 | Save enderChest data (was silently ignored) | Code review | `InventorySyncManager.java` (Step 32) |
| 12 | Crash recovery buffer for hung saves | Design doc | `DataSyncEngine.java` (Step 33) |

### Security Improvements

| # | Suggestion | Source | Implemented In |
|---|-----------|--------|---------------|
| 13 | AES/GCM instead of ECB mode | Code review | `SecurityUtil.java` (Step 8) |
| 14 | Discord webhook alert on security failures | Design doc | `BridgeManager.java` (Step 24) |
| 15 | Config validation on startup (fail-fast on bad config) | Self | `CorePaperPlugin.java` (Steps 50-52) |
| 16 | Startup health checks (SELECT 1, PING) | Self | `CorePaperPlugin.java` (Step 51) |

### Missing Features Added

| # | Suggestion | Source | Implemented In |
|---|-----------|--------|---------------|
| 17 | 3 missing packet types (ServerHeartbeat, PlayerSwitchServer, InventoryLock) | Design doc | `mdn-api/packet/` (Steps 12-14) |
| 18 | 3 sync events (PlayerJoinSync, PlayerQuitSync, InventorySync) | Design doc | `mdn-api/events/` (Steps 15-17) |
| 19 | PacketDispatcher for routing incoming Redis messages | Code review | `PacketDispatcher.java` (Step 34) |
| 20 | Velocity config files for Bridge and Core | Code review | Both `config-velocity.yml` (Steps 25, 35) |
| 21 | Standard commands (/website, /store, /vote, /discord, /help, /rules, /spawn) | Design doc | Both plugins (Step 36) |
| 22 | `/mdn health` command with full infrastructure report | Self | Both plugins (Step 37) |
| 23 | Server heartbeat publishing (every 5s) | Self | `CorePaperPlugin.java` (Step 38) |
| 24 | Health scoring for smarter server routing | Self | `ServerRegistry.java` (Step 28) |

### Resilience & Observability

| # | Suggestion | Source | Implemented In |
|---|-----------|--------|---------------|
| 25 | Circuit Breaker pattern (Redis + DB) | Self | `CircuitBreaker.java` (Steps 42-45) |
| 26 | API Versioning (semver, compatibility checks) | Self | `ApiVersion.java` (Steps 46-49) |
| 27 | Correlation IDs for distributed tracing | Self | `MDNPacket.java` + `MDNAPI.java` (Steps 53-56) |
| 28 | Dead Letter Queue with exponential backoff retry | Self | `DeadLetterQueue.java` (Steps 57-61) |
| 29 | Operation Timeouts on all Redis/DB calls | Self | `DatabaseManager.java` + `RedisManager.java` (Steps 62-67) |

### Testing

| # | Suggestion | Source | Implemented In |
|---|-----------|--------|---------------|
| 30 | SecurityUtil unit tests (8 tests) | Self | `SecurityUtilTest.java` (Step 39) |
| 31 | ApiVersion unit tests (9 tests) | Self | `ApiVersionTest.java` (Step 40) |
| 32 | CircuitBreaker unit tests (7 tests) | Self | `CircuitBreakerTest.java` (Step 41) |

### Developer Experience

| # | Suggestion | Source | Implemented In |
|---|-----------|--------|---------------|
| 33 | `MDNAPI.shutdown()` for clean lifecycle | Code review | `MDNAPI.java` (Step 8) |
| 34 | `MDNAPI.isInitialized()` safety check | Code review | `MDNAPI.java` (Step 9) |
| 35 | `MDNAPI.createStandalone()` for testing | Self | `MDNAPI.java` (Step 10) |
| 36 | ObjectMapper pre-configured (JavaTimeModule, ignoreUnknown) | Self | `MDNAPI.java` (Step 11) |
| 37 | Jackson `@JsonTypeInfo` on MDNPacket for auto-dispatch | Code review | `MDNPacket.java` (Step 1.8) |

### Documentation

| # | Suggestion | Source | Implemented In |
|---|-----------|--------|---------------|
| 38 | DIARY.md — development journal | User request | `DIARY.md` (Step 68) |
| 39 | STEPS.md — step-by-step log | User request | `STEPS.md` (Step 69) |
| 40 | SUGGEST.md — suggestion catalog | User request | `SUGGEST.md` (Step 70) |
| 41 | TIMELINE.md — roadmap timeline | User request | `TIMELINE.md` (Step 71) |

### Build Quality

| # | Suggestion | Source | Implemented In |
|---|-----------|--------|---------------|
| 42 | velocity-plugin.json deduplication — remove manual template, rely on @Plugin annotation processor | Build audit | Deleted both `src/main/resources/velocity-plugin.json` files + removed expand blocks from build.gradle.kts (Phase 7, Steps 85-87) |
| 43 | Velocity config bootstrap — saveDefaultConfig() for Velocity plugins, copy config-velocity.yml from JAR on first startup | Real server logs | Added `saveDefaultConfig()` to both Velocity plugins (Phase 8, Steps 88-91) |

---

## 🔜 Planned (Near Future)

These are targeted for the next development session.

| # | Suggestion | Priority | Effort | Why It Matters |
|---|-----------|----------|--------|---------------|
| 44 | **Rate Limiter** per IP/player on packet publishing | 🔥 High | Medium | Prevents one buggy plugin from DoS-ing the Redis bus |
| 45 | **Local Event Bus** for in-process pub/sub | 🟡 Medium | Medium | Plugins can fire/listen without going through Redis |
| 46 | **Graceful Degradation Mode** — switch to local-only when Redis is down | 🔥 High | High | Server stays playable even if Redis crashes |
| 47 | **Database Migration Framework** — auto-run schema changes | 🟡 Medium | High | No more manual ALTER TABLE; track applied versions |
| 48 | **Connection Pool Metrics** — expose HikariCP stats in /mdn health | 🟡 Medium | Low | Know pool exhaustion BEFORE server dies |

---

## 📋 Future Consideration

Longer-term ideas — good to have but not urgent.

| # | Suggestion | Priority | Effort | Notes |
|---|-----------|----------|--------|-------|
| 49 | **Prometheus Metrics Export** — `/metrics` endpoint | 🟢 Low | High | Grafana dashboards for the whole network |
| 50 | **Developer Debug Kit** — `/mdn debug packets`, `/mdn debug cache` | 🟢 Low | Medium | Live packet tracing, cache peeking, Redis ping |
| 51 | **Packet Batching** — queue packets and flush every 50ms | 🟡 Medium | Medium | Reduces Redis roundtrips at scale |
| 52 | **Plugin Hot-Reload Safety** — clean resource cleanup on reload | 🟢 Low | Medium | Prevent thread/connection leaks on /mdn reload |
| 53 | **Structured Logging** — JSON log format for log aggregation | 🟢 Low | Medium | ELK/Splunk integration |
| 54 | **Multi-Region Support** — latency-based routing | 🟢 Low | High | For EU/NA/ASIA deployment |
| 55 | **Redis Sentinel/Cluster Support** — high availability Redis | 🟢 Low | Medium | Production HA setup |
| 56 | **MySQL Read Replicas** — separate read/write connections | 🟢 Low | Medium | Scale read-heavy operations |
| 57 | **Webhook Notifications** — configurable webhooks for events | 🟢 Low | Low | Admins get Discord/Slack alerts for key events |
| 58 | **Admin Dashboard API** — REST endpoints for web admin panel | 🟢 Low | High | Future web-based server management |

---

## ❌ Rejected / Deferred

Ideas considered but decided against (with reasons).

| # | Suggestion | Reason |
|---|-----------|--------|
| 59 | **Java 25 target** | Paper 1.21.1 ecosystem is built for Java 21; Java 25 is too new and Gradle tooling doesn't fully support it yet |
| 60 | **Separate repos per plugin** | Would require cross-repo version sync and complex build orchestration. Monorepo keeps everything atomic |

---

## 📝 How to Use This File

### Adding a new suggestion
1. Add it to the appropriate section (Planned / Future / Rejected)
2. Include: brief description, priority (🔥/🟡/🟢), estimated effort (Low/Medium/High), and why it matters
3. Don't delete old suggestions — mark them as ✅ Implemented instead

### Promoting a suggestion
1. Move it from "Future" → "Planned" when you decide to work on it
2. Move it from "Planned" → "Implemented" when complete
3. Add the step number from STEPS.md for traceability

### Rejecting a suggestion
- Move it to "Rejected" with a clear reason
- If the situation changes, it can be moved back

---

*Last updated: August 4, 2026 (night) — 44 suggestions cataloged*
