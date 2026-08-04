# MineDrop Network Specification — MDN-Moderation

## 📋 1. Overview
* **Consolidated Name**: `MDN-Moderation`
* **Target Environment**: Paper (Primary UI & Actions) & Velocity (Proxy Kick/Mute Sync)
* **Purpose**: Provides administrative control over player behavior. It manages network-wide mute, ban, kick, and warning histories. It also houses advanced staff tools, such as `/vanish` triggers, staff inspection inventories, player coordinate freezing, and screenshare modes.

---

## 🛠️ 2. Punishments & Staff Tools
### 1. Unified Punishment Engine
* Bans and mutes are saved to MySQL with unique ID signatures.
* When a ban is executed, a Redis event (`mdn:moderation:action`) is dispatched to notify Velocity to instantly sever the target player's proxy connection.
* When a mute is executed, the player's profile cache is updated, and chat packet listeners intercept and discard messages sent by the muted user.

### 2. Freeze & Screenshare Loop
* Staff can lock a player's movements using `/freeze <player>`.
* **Behavior**:
  * The frozen player is block-locked (teleported back to their freeze coordinates if they try to move).
  * A title message flashes: `"&c&lYOU ARE UNDER INSPECTION &7- &fDo not log out!"`
  * If the player disconnects while frozen, they are automatically banned for "Logging out under freeze/screenshare check."

### 3. Vanish Mode
* Hides staff players from normal users via Spigot's `Player.hidePlayer()` API.
* Staff in vanish bypass physical pressure plates (e.g. they do not trigger base lock plates or alarm traps), make no footsteps, and are excluded from online player counts.

---

## ⚙️ 3. Configuration Template
### `config.yml`
```yaml
moderation:
  ban-format:
    kick-message: "&cYou are banned from MineDrop!\n&7Reason: &f%reason%\n&7Expires in: &f%expiry%\n&8ID: #%id%"
    appeal-link: "appeal.minedrop.net"

  screenshare:
    auto-ban-duration-days: 30
    auto-ban-reason: "Log out during screenshare inspection"
    teleport-to-coords:
      world: "ss_world"
      x: 0.5
      y: 64.0
      z: 0.5
```

---

## 🎮 4. Commands & Permissions
### Commands (Velocity & Paper)

| Command | Action | Permission | Arguments |
| :--- | :--- | :--- | :--- |
| `/ban <p> <t> <r>` | Issues a temporary or permanent ban. | `mdn.mod.ban` | `<p> <t> <r>` (String, Time, Reason)|
| `/unban <player>` | Revokes an active ban. | `mdn.mod.unban` | `<player>` (String) |
| `/mute <p> <t> <r>`| Mutes a player's chat across servers. | `mdn.mod.mute` | `<p> <t> <r>` (String, Time, Reason)|
| `/unmute <player>` | Restores a player's chat privileges. | `mdn.mod.unmute` | `<player>` (String) |
| `/freeze <player>` | Suspends player movement and actions. | `mdn.mod.freeze` | `<player>` (String) |
| `/ss <player>` | Places player into screenshare mode. | `mdn.mod.screenshare`| `<player>` (String) |
| `/vanish` | Toggles staff invisibility. | `mdn.mod.vanish` | None |
| `/staffmode` | Equips staff tools (inventory inspector).| `mdn.mod.staffmode` | None |

---

## 🛡️ 5. Edge Cases & Solutions
* **Vanish Detection via Tab List**:
  * *Issue*: Standard Bukkit `hidePlayer()` hides the entity model but can leave the player listed in the TAB menu or third-party scoreboard plugins.
  * *Solution*: Hook into the network's `TAB` plugin API. When `/vanish` is activated, dispatch a packet to remove the player's name slot from the tab list for all non-staff clients.
* **Muted Players Bypass via Book Writing**:
  * *Issue*: Muted players write notes or sign books and drop them, bypassing the chat ban.
  * *Solution*: Intercept Spigot's `PlayerEditBookEvent` and `SignChangeEvent`. If a player is flagged as muted in the database cache, cancel the events and notify the player.
* **Frozen Player Combat Abuse**:
  * *Issue*: If a player is frozen in the middle of a raid, other players can hit or kill them.
  * *Solution*: When a player is frozen, temporarily apply invulnerability flags (`EntityDamageEvent` cancelled) and block all damage packets targeting the frozen entity.
