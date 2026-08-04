# MineDrop Network 🎮

**A next-generation Minecraft minigames network** — home of "Steal a Mineling" (SAM), the first-of-its-kind conveyor belt base-defence PvPvE game.

---

## 🏗️ Architecture

This is a **Gradle monorepo** containing 10 plugins split across the **Velocity** (proxy) and **Paper** (game server) layers:

```
MineDrop-Network/
├── mdn-api/           ★ Shared library — packets, DB schema, security utils
├── mdn-bridge/        ★ Security foundation — plugin validation & handshake
├── mdn-core/          ★ Network heartbeat — sessions, routing, cache, sync
│
├── mdn-auth/          ◻ Skeleton — Authentication & 2FA
├── mdn-security/      ◻ Skeleton — Anti-cheat & exploit prevention
├── mdn-economy/       ◻ Skeleton — Coins, auction house, NPC shop
├── mdn-social/        ◻ Skeleton — Friends & clans
├── mdn-communication/ ◻ Skeleton — Chat & Discord bridge
├── mdn-maintenance/   ◻ Skeleton — Whitelist, restarts, lockdown
├── mdn-moderation/    ◻ Skeleton — Staff tools
└── mdn-sam/           ◻ Skeleton — "Steal a Mineling" gameplay
```

**★ = Fully implemented** | **◻ = Skeleton for new developers to complete**

---

## 🚀 Quick Start

### Prerequisites
- **Java 21+** (JDK)
- **Gradle 8.x** (wrapper included — use `./gradlew`)

### Build Everything
```bash
./gradlew build
```

### Build a Specific Plugin
```bash
./gradlew :mdn-api:build        # Shared library
./gradlew :mdn-core:build       # Network heartbeat
./gradlew :mdn-sam:shadowJar    # Game plugin (fat JAR)
```

### Output
All plugin JARs are generated in each module's `build/libs/` directory:
- `mdn-bridge/build/libs/mdn-bridge-1.0.0-SNAPSHOT.jar`
- `mdn-core/build/libs/mdn-core-1.0.0-SNAPSHOT.jar`
- etc.

---

## 📚 Plugin Dependency Graph

```
MDN-API  ◄────────────────────────────────────────────┐
   │                                                    │
MDN-Bridge  ◄──────────────────────────────────────┐   │
   │                                                 │   │
MDN-Core  ◄─────────────────────────────────────┐  │   │
   │                                              │  │   │
   ├── MDN-Economy ──────────────────────────────┤  │   │
   ├── MDN-Social ───────────────────────────────┤  │   │
   ├── MDN-Communication ────────────────────────┤  │   │
   ├── MDN-Maintenance ──────────────────────────┤  │   │
   ├── MDN-Moderation ───────────────────────────┤  │   │
   └── MDN-SAM ──────────────────────────────────┘  │   │
                                                      │   │
MDN-Auth ─────────────────────────────────────────────┘   │
MDN-Security ─────────────────────────────────────────────┘
```

---

## 🧩 For New Developers

### How to add a new feature to an existing plugin

1. Find the plugin's TODO checklist in the main class Javadoc (e.g., `EconomyPaperPlugin.java`)
2. Read the full specification in `plan/MineDrop/plugins/0X_MDN_*.md`
3. Follow the conventions established in `mdn-core` — it's the reference implementation

### How to complete a skeleton plugin

Each skeleton contains:
- `build.gradle.kts` — Dependencies already configured
- `plugin.yml` / `velocity-plugin.json` — Plugin metadata ready
- Main class with numbered TODO checklist matching the spec document

**Just start coding in the main class.** The infrastructure (database, Redis, caching) is already available through `MDNAPI.getInstance()`.

### Code conventions (follow these!)
- **Package**: `net.minedrop.<plugin>`
- **Main class**: `<PluginName>PaperPlugin` or `<PluginName>VelocityPlugin`
- **Use Lombok** for boilerplate (getters, setters, constructors)
- **Async all database operations** — never block the main thread
- **Shade dependencies** — use the Shadow plugin to avoid classpath conflicts

---

## 🛠️ Technology Stack

| Component | Technology |
|-----------|-----------|
| Build System | Gradle 8.x (Kotlin DSL) |
| Language | Java 21 |
| Proxy | Velocity 3.3.0 |
| Game Server | Paper 1.21.1 |
| Database | MySQL (HikariCP connection pool) |
| Cache / Messaging | Redis (Jedis client, Pub/Sub) |
| Serialization | Jackson JSON |
| Boilerplate | Lombok |

---

## 📖 Full Design Documents

All game design specifications are in `plan/MineDrop/`:

| File | Content |
|------|---------|
| `MINEDROP - A MINIGAMES SERVER...md` | Original game concept & vision |
| `plugins-roadmap.md` | Consolidation plan & dependency graph |
| `Plugin-making ranking.md` | Build priority & phase breakdown |
| `SAB_Plugin_Design_Review.md` | Detailed SAM game mechanics |
| `Updated Core System - Velocity & Paper.md` | Original plugin specifications |
| `plugins/00_MDN_API.md` | API library specification |
| `plugins/01_MDN_Bridge.md` | Bridge security specification |
| `plugins/02_MDN_Core.md` | Core infrastructure specification |
| `plugins/03-10_MDN_*.md` | Remaining plugin specifications |

---

*Built with ❤️ by the MineDrop Network Team*
