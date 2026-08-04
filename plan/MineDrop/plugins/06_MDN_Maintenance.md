# MineDrop Network Specification — MDN-Maintenance

## 📋 1. Overview
* **Consolidated Name**: `MDN-Maintenance`
* **Target Environment**: Velocity & Paper
* **Purpose**: Coordinates administrative maintenance routines. This plugin is responsible for whitelisting, emergency lockdowns, and executing synchronized, safe network restarts. It guarantees player data is fully saved before servers shut down.

---

## 🛠️ 2. Restarts & Lockdown Sequences
### Safe Restart Sequence
The plugin coordinates shutdowns to prevent rollbacks or connection drops:

```
[Trigger Restart] ──► Broadcast warnings ──► Lock new joins (Proxy level)
                                │
                                ▼
[Paper Game Phase] ──► Lock gameplay state ──► Trigger MDN-Core Async Saves
                                │
                                ▼
[Verification]    ──► Verify Lock Released (Redis) ──► Safely Route players to Hub
                                │
                                ▼
[Shutdown]        ──► Stop Game Process
```

1. **Initiation**: Admin executes `/networkrestart <minutes>`.
2. **Alert Loop**: Broadcasts warning countdowns via chat, titles, and bossbars at 10m, 5m, 1m, 30s, and 10s.
3. **Lobby Lock**: Velocity blocks new connections and routes incoming players to maintenance notice screens.
4. **SAM Game State Freeze (Paper)**:
   * Freezes conveyor belts, pauses Destroyer events, and blocks statue steal actions.
   * Forces a database sync lock for all active player profiles.
5. **Safe Transits**: Players are routed back to the main proxy hub or primary standby server as their instance finishes saving.
6. **Graceful Stop**: Spigot calls `Bukkit.shutdown()` once profile locks are confirmed empty.

---

## ⚙️ 3. Configuration Template
### `config.yml`
```yaml
maintenance:
  enabled: false
  kick-message: "&cMineDrop is currently under maintenance.\n&fReason: %reason%\n&7Join our Discord for updates: discord.gg/minedrop"
  bypass-permission: "mdn.maintenance.bypass"

restart:
  shutdown-command: "stop" # Command executed to stop Spigot/Velocity process
  default-delay-minutes: 10
  force-kick-message: "&cServer restarting. Routing you back to safety..."
```

---

## 🎮 4. Commands & Permissions
### Commands (Velocity & Paper)

| Command | Action | Permission | Arguments |
| :--- | :--- | :--- | :--- |
| `/maintenance on <reason>`| Activates maintenance mode. | `mdn.maintenance.admin` | `<reason>` (String) |
| `/maintenance off` | Deactivates maintenance mode. | `mdn.maintenance.admin` | None |
| `/networkrestart <time>` | Initiates a synchronized reboot. | `mdn.restart.admin` | `<time>` (Integer - Min) |
| `/networkrestart cancel` | Cancels an active restart sequence. | `mdn.restart.admin` | None |

---

## 🛡️ 5. Edge Cases & Solutions
* **Hung Saves Blocking Restarts**:
  * *Issue*: A player profile save hangs indefinitely due to network lag, locking the database and halting the shutdown sequence.
  * *Solution*: Set an absolute shutdown deadline (e.g. 45 seconds). If a player save fails to complete within this window, dump the player's memory state directly to a local JSON crash file (`plugins/MDN-Maintenance/emergencies/profile_<uuid>.json`) on the Spigot host, release the database lock, and force the process shutdown. The core plugin will load this crash file on the player's next login.
* **Orphaned Servers on Proxy Crash**:
  * *Issue*: If the Velocity proxy crashes, the Paper servers continue running without routing context.
  * *Solution*: Paper servers listen for a periodic heartbeat signal from the proxy (via Redis). If the proxy heartbeat is missing for more than 45 seconds, Paper servers enter a local lockdown mode, freeze gameplay elements, and safely save all active player data.
