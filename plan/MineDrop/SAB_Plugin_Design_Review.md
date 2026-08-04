# Steal a BrainRot (SAB) — Plugin Design Review

\---

## Tech Stack

* PaperMC 26.1.2 (Java 25)
* MDN-Plot
* FAWE (FastAsyncWorldEdit) for async schematic pasting + region selection
* Gradle build system
* Custom entity system (abstracted interface, implementation added later)

\---

## Game Concept

* Players have bases, collect statues from a conveyor in the middle, others can steal them
* Game runs continuously as long as players are on the plot
* Game logic (conveyor etc.) handled separately
* This plugin handles: lobby system, plot management, team system, statue system, base lock mechanic

\---

## Plot Types

### Public

* One active public plot at a time
* One schematic only (one at a time, changeable by admin)
* Anyone can join via `/publiclobby`
* When full → next idle plot becomes active
* Filling priority — oldest/most occupied plots fill first before newer ones
* On empty → plot goes idle, stays in pool with schematic intact
* On restart → all public plots go idle, pool state saved

### Private

* Created by team leader via `/createlobby <schematic>`
* Only team members can join via `/privatelobby`
* Player limit = base count in schematic
* On empty → goes idle, reusable by other teams with same schematic
* If team disbands → all members TPed to hub, lobby goes idle
* On restart → schematic saved per plot, reassigned to teams

\---

## Plot Ownership

* All plots owned by server account
* Players only have join rights
* Private plots restrict entry to team members only

\---

## Plot Pool

* Grows lazily — reuse idle plots first
* Only create new plot + paste schematic when all existing plots are full
* `/createlobby` → checks for idle plot with same schematic first, reuses before creating new
* Stale schematic plots (old schematic) → re-pasted sequentially only after all idle plots are used
* Re-pastes happen one at a time, not simultaneously

### On Restart

* Public plots → all go idle, pool state saved (which have schematics pasted, which are stale)
* Private plots → schematic saved per plot, reassigned to teams on restart

\---

## Schematic System

* FAWE for async pasting
* One YAML file per schematic
* All coordinates stored relative to plot corner

### YAML Structure

```yaml
schematic: "castle"
total\_bases: 8
bases:
  1:
    spawn:
      x: 32.5
      y: 64.0
      z: 15.5
      yaw: 90.0
      pitch: 0.0
    region:
      min:
        x: 30.0
        y: 63.0
        z: 13.0
      max:
        x: 40.0
        y: 70.0
        z: 20.0
    lock\_plate:
      x: 33.0
      y: 64.0
      z: 16.0
    statue\_slots:
      1:
        x: 34.0
        y: 64.0
        z: 15.5
        yaw: 90.0
      2:
        x: 35.0
        y: 64.0
        z: 15.5
        yaw: 90.0
```

### Admin Setup Workflow (per schematic)

1. Paste schematic into a plot
2. `/setbaseregion <schematic> <base>` — FAWE wand selection first (required for all other validations)
3. `/setbase <schematic> <base>` — at each base spawn point
4. `/setlockplate <schematic> <base>` — standing on the pressure plate
5. `/setstatue <schematic> <base> <slot>` — at each statue slot

### Admin Validations

* `/setbase` — spawn point must be inside base region
* `/setbaseregion` — must have active FAWE selection, must not overlap existing regions in same schematic
* `/setlockplate` — must be inside base region
* `/setstatue` — must be inside base region

\---

## Player Join Flow

1. Player runs `/publiclobby` or `/privatelobby`
2. Cooldown check — block if joined too recently
3. Find most occupied plot with a free slot
4. If none → grab idle plot from pool OR create new plot and paste schematic
5. If stale schematic → re-paste sequentially, queue player
6. If plot being prepared (FAWE pasting) → queue player, TP when ready
7. Queue max size = plot's base count, overflow starts preparing next plot
8. Team size rechecked after paste completes before allowing joins (private only)
9. Assign player to a free base slot
10. TP player to base spawn coords (relative → absolute)
11. Load player's saved statues into base slots as custom entities (by slot number order, overflow stays saved unspawned)

\---

## Player Leave / Hub Exit Flow

* Hub exit is treated exactly the same as leaving the lobby
1. Statue rules applied at last known position on plot
2. All slotted statues → saved and despawned
3. All non-slotted statues inside their base → deleted immediately
4. If carrying statue outside any base → drops at last position on plot
5. If carrying statue inside any base → deleted
6. Everyone inside the leaving player's base → dies
7. Their carried statues → drop at death location → rules apply from there
8. Base slot freed, base completely clean
9. Warn player before voluntary leave if they have unslotted statues (disconnect = no warning, no grace period)
10. If plot fully empties → delete all remaining world statues, mark plot idle

\---

## New Player Assigned to Previously Empty Base

* Delete all statues on ground inside that base
* Kill anyone inside that base (no exceptions, teammates included)
* Killed player's carried statue → drops at death location → rules apply from there
* TP new player into clean base

\---

## Statue System

### Statue States

|State|Description|
|-|-|
|Slotted|In a base slot, saved to owner's data permanently|
|Carried|In a player's hand, temporary|
|Dropped|On the ground, temporary|

### Data Tracked Per In-World Statue

* Original owner UUID
* Current state (slotted / carried / dropped)
* Current world position
* Which base it's currently inside (null if outside)

### Carrying Mechanic

* Anyone can pick up any statue (own or others)
* One statue at a time
* Cannot leave lobby directly — must go to hub (treated as player leave)
* Players can ONLY place statues in their OWN base slots
* Steal only successful when placed in own base slot
* If own base is full → can pick up but cannot place until a slot is freed
* Occupied slot → cannot place, must free slot first

### Dropping Mechanic

* Can be dropped anywhere on the plot
* Anyone walking over it can pick it up
* No timer — persists until rules trigger deletion

### Unified Statue Deletion Rules

|Situation|Result|
|-|-|
|Slotted in base slot|Saved, persists always|
|Inside any base (carried or dropped)|Deleted when that base's owner leaves|
|Outside any base (carried or dropped)|Persists until plot goes idle → deleted|
|Carrier dies|Drops at death location → rules apply based on location|
|Base owner leaves|Slotted statues saved/despawned, all non-slotted inside base deleted|
|Everyone inside base dies on owner leave|Carried statues drop at death location inside base → deleted|
|Plot goes idle|All remaining world statues deleted|

### Statue Slot Loading on Join

* Load by slot number order (slot 1 first, slot 2 etc.)
* More saved statues than available slots → load what fits, rest stay saved unspawned

\---

## Base Lock Mechanic

* Each base has a pressure plate inside it
* Owner steps on plate → base locks for X seconds (globally configurable)
* Stepping on plate again at any time → resets timer to full (intended mechanic, no cooldown)
* Unoccupied base → no lock mechanic, freely enterable by anyone

### While Locked

* Nobody outside can enter except the owner
* Nobody inside can leave (trapped)
* Owner can enter and leave freely

### Lock Expiry

* Lock expires → trapped players free to leave

### Owner Leaves While Base is Locked

* Everyone inside the base dies (same as normal owner leave rule)
* Their carried statues drop at death location inside base → deleted
* Base fully cleaned

\---

## Team System

* One team per player at a time
* Invites expire after 30 seconds
* Reinvite cooldown after expiry or decline
* Leader leaves team → leadership passes to next member
* Team drops below min size → leader warned, cannot create lobby
* Team disbands → all members TPed to hub, private lobby goes idle
* Block multiple `/createlobby` calls (one lobby per team at a time)
* Team size rechecked after paste completes before allowing joins
* Minimum team size to create private lobby → configurable
* Team data persists across restarts (saved to YAML)

\---

## Config

```yaml
public-lobby:
  schematic: "public"

private-lobby:
  min-team-size: 2

teams:
  invite-expiry-seconds: 30
  reinvite-cooldown-seconds: 60

lobby:
  join-cooldown-seconds: 10

base:
  lock-duration-seconds: 30
```

\---

## Full Command List

### Player Commands

|Command|Who|What|
|-|-|-|
|`/publiclobby`|Any player|Join active public lobby|
|`/privatelobby`|Team member|Join team's private lobby|
|`/createlobby <schematic>`|Team leader|Create a private lobby|
|`/team create <name>`|Any player|Create a team|
|`/team invite <player>`|Leader|Invite a player (30s expiry)|
|`/team accept`|Invited player|Accept pending invite|
|`/team leave`|Any member|Leave the team|
|`/team disband`|Leader|Disband the team|

### Admin Commands

|Command|Who|What|
|-|-|-|
|`/setbase <schematic> <base>`|Admin|Set base spawn point (must be inside region)|
|`/setbaseregion <schematic> <base>`|Admin|Set base boundary via FAWE selection (no overlaps allowed)|
|`/setlockplate <schematic> <base>`|Admin|Set pressure plate location (must be inside region)|
|`/setstatue <schematic> <base> <slot>`|Admin|Set statue slot position (must be inside region)|

\---

## 

