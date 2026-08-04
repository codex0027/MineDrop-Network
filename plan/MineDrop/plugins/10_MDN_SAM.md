# MineDrop Network Specification — MDN-SAM (Steal A Mineling)

## 📋 1. Overview
* **Consolidated Name**: `MDN-SAM`
* **Target Environment**: Paper (Primary Gameplay Engine)
* **Purpose**: Coordinates all gameplay elements for "Steal a Mineling" (SAM) / "Steal a BrainRot" (SAB). It manages dynamic plot allocation, schematic pasting, the central conveyor belt spawning loops, custom mob states, base locks, PvE events, and stealth-based stealing runs.

---

## 🛠️ 2. Core Systems & Mechanics

```
               [Plot Pool Manager] (Lazy Scaling)
                        │
                        ▼ (FAWE Paste)
               [Plot Instance Spawn]
             /          │           \
            ▼           ▼            ▼
     [Conveyor Loop] [Base Regions] [Lock Plates]
```

### 1. Plot Instance Scaling & Pools
* Plots are managed dynamically using the PlotSquared API.
* **Lazy Allocation**: When a player runs `/publiclobby` or `/privatelobby`, the system searches the database for an existing idle plot with the matching schematic pasted. It only generates a new plot and triggers a FAWE async paste if all existing plots are full.
* **FAWE Pasting**: Paste procedures are queued sequentially. If a plot is being prepared, players are placed in a queue and teleported once the paste action completes.

### 2. Conveyor Belt Logic
* Spawns entities ("Minelings") at starting coords defined in the schematic.
* **Movement**: Minelings move along a vector path toward the end of the belt using custom pathfinding goals or tick-based coordinate translations (translating armor stands or custom entities along a line).
* **Capture**: Players click a Mineling to capture it. The action deducts coins from the player's wallet. Captured Minelings are converted to inventory storage items.
* **Rarity Distribution**:
  * Common (Gray) | Rare (Blue) | Epic (Purple) | Legendary (Gold) | Mythic (Red) | Brainrot (Rainbow/Glow)
  * Multiplier values and coin generation are configured per tier.

### 3. Statue States
Statues exist in one of three states:

| State | Spawning Profile | Database Persistence | Interaction Limits |
| :--- | :--- | :--- | :--- |
| **Slotted** | Spawned as a custom armor stand with a block/model head at base slots. | Saved to Owner Profile (MySQL). | Exposes a passive coin income rate. Cannot be moved by anyone except the owner or a successful thief. |
| **Carried** | Attached as an item in the player's hand or represented as an entity riding the player. | Temporary (Lost on server restart). | Only one statue can be carried at a time. Speed is slowed by 25%. Cannot leave the plot. |
| **Dropped** | Rendered as a custom entity on the ground. | Temporary (Saved to Plot state). | Any nearby player can pick it up. Deleted if the plot goes idle. |

### 4. Base Lock Plate Mechanic
* Each base schematic defines a pressure plate location (`lock_plate`).
* **Trigger**: The base owner steps on the plate.
* **Lock Active**:
  * Intruders inside the base cannot leave. A barrier block barrier is dynamically spawned at base thresholds, or coordinate checking teleports them back to the base center if they cross the boundary.
  * Intruders outside the base cannot cross the threshold.
  * The owner can pass through the lock barriers freely.
* **Duration**: Stays active for `lock-duration-seconds` (e.g. 30s). Re-stepping on the plate resets the timer to full. No cooldown.

### 5. Destroyer Minelings (PvE Event)
* Spawns on the conveyor belt randomly after at least 10 normal Minelings have spawned.
* Triggers a siren sound (`ambient.cave`) and a bossbar warning.
* Targets a random player base and walks toward it, eating/destroying blocks in its path (stored in a roll-back history buffer).
* Players must defeat the Destroyer using tools purchased from the NPC merchant. Defeating the Destroyer yields ultra-rare rewards.

### 6. Join, Leave, & Assignment Cleanups
* **Leave/Logout**:
  * Slotted statues are saved to the database and despawned safely.
  * Non-slotted statues inside the owner's base are deleted.
  * If the owner logs out, all other players standing inside their base are instantly killed. Their carried statues drop at their death location and are deleted.
  * The base slot is cleared and flagged as clean.
* **New Base Assignment**:
  * Deletes all statues on the ground inside the target base.
  * Kills anyone inside the base (no exceptions).
  * Teleports the new owner into a clean base.

---

## ⚙️ 3. Configuration Template
### `config.yml`
```yaml
sam:
  lobby:
    join-cooldown-seconds: 10
    queue-max-overflow: 8

  plot-pool:
    pre-paste-idle-plots: 2
    sequential-paste-interval-ticks: 100

  base:
    lock-duration-seconds: 30
    grief-warning-seconds: 5 # Time before killing intruders on base claim

  conveyor:
    spawn-rate-ticks: 200
    base-movement-speed: 0.2
    rarities:
      common: { chance: 50.0, capture_cost: 10, multiplier: 1.0 }
      rare: { chance: 30.0, capture_cost: 25, multiplier: 1.5 }
      epic: { chance: 12.0, capture_cost: 50, multiplier: 2.0 }
      legendary: { chance: 5.0, capture_cost: 150, multiplier: 3.5 }
      mythic: { chance: 2.0, capture_cost: 300, multiplier: 5.0 }
      brainrot: { chance: 1.0, capture_cost: 500, multiplier: 8.0 }

  destroyer:
    min-minelings-before-spawn: 10
    base-damage-ticks: 20
```

---

## 🎮 4. Commands & Permissions
### Commands (Paper Game Server)

| Command | Action | Permission | Arguments |
| :--- | :--- | :--- | :--- |
| `/publiclobby` | Joins the active public plot lobby. | `mdn.sam.player` | None |
| `/privatelobby` | Joins the team's private plot lobby. | `mdn.sam.player` | None |
| `/createlobby <schem>`| Allocates a plot and pastes a schematic. | `mdn.sam.leader` | `<schem>` (String) |
| `/setbaseregion <s > <b >`| Sets base bounds via active WE wand. | `mdn.sam.admin` | `<s > <b >` |
| `/setbase <schem> <base>`| Sets base spawn coordinates. | `mdn.sam.admin` | `<schem> <base>` |
| `/setlockplate <s > <b >`| Maps the lock plate position. | `mdn.sam.admin` | `<s > <b >` |
| `/setstatue <s > <b > <sl>`| Maps a statue display slot. | `mdn.sam.admin` | `<s > <b > <sl>` |

---

## 🛡️ 5. Edge Cases & Solutions
* **FAWE Pasting Thread Congestion**:
  * *Issue*: Paste operations block the server's main thread during high-frequency plot setups.
  * *Solution*: Limit active pastes to one at a time. The plugin maintains a queue: if a paste operation is active, subsequent requests wait in a queue, pasting only after the previous operation triggers a FAWE `EditSession` completion callback.
* **Lock Plate Trapping Abuse**:
  * *Issue*: Players step on plates indefinitely to trap other players inside their bases forever.
  * *Solution*: Implement a "Decay Lock" rule. If a player holds a lock for more than 90 seconds, the plate is disabled for a 15-second cooldown period, allowing trapped players a window to escape.
* **Logout Loot Duplication**:
  * *Issue*: A player logs out while carrying a stolen statue, and the server crashes, resulting in the item existing both on the ground and in the player's inventory cache.
  * *Solution*: Make all "Statue Carried" events write to a temporary Redis inventory journal. If a player disconnects, clear the carried item from their inventory cache and write it to the plot ground files before releasing the player lock.
