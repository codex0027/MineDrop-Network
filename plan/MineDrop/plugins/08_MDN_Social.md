# MineDrop Network Specification — MDN-Social

## 📋 1. Overview
* **Consolidated Name**: `MDN-Social`
* **Target Environment**: Paper
* **Purpose**: Coordinates social features on the network. This plugin manages the unified Friendship Engine (lists, statuses, requests) and the Clan/Team infrastructure. It tracks clan rosters, experience points, levels, upgrades, and provides integrations for team permissions and private plot allocations.

---

## 🛠️ 2. Core Engines & Mechanics
### 1. Friendship Engine
* Players can issue `/friend invite` requests, which are stored in Redis with an expiration timer (e.g. 30 seconds).
* Accepted friendships are saved to MySQL. Enables cross-server online status indicators and private messaging.

### 2. Team & Clan Infrastructure
* **Unified Entities**: Clans and teams are unified. A player can belong to only one clan at a time.
* **Creation**: Created via `/team create <name>`, which designates the creator as Leader.
* **Invites**: Invites expire after 30 seconds. A player declining an invite triggers a cooldown before another invite can be issued by the same clan (configured in seconds).
* **Ranks**: Ranks are hierarchical: Leader, Co-Leader, Officer, Member.
* **Disbandment**: If the leader disbands the clan via `/team disband`, the system alerts all members, kicks them from private clan instances back to the hub, and marks the associated private plot as idle.
* **Clan Leveling**: Actions (like capturing rare Minelings, successfully defending bases, or selling to the merchant) award Clan Experience points (CXP). Level progression unlocks larger member capacity, coin multiplier boosts, and expanded vault storage.
* **Shared Storage (Vaults)**: A GUI chest vault stored as serialized Base64 NBT. Only accessible to designated clan ranks.

---

## ⚙️ 3. Configuration Template
### `config.yml`
```yaml
social:
  friends:
    max-friends-limit: 100
    invite-expiry-seconds: 30

  clans:
    min-name-length: 3
    max-name-length: 16
    invite-expiry-seconds: 30
    reinvite-cooldown-seconds: 60
    default-max-members: 10
    creation-cost: 5000.0 # Coins required to create a team
    levels:
      1: { xp_needed: 1000, max_members: 10, vaults: 1 }
      2: { xp_needed: 2500, max_members: 12, vaults: 1 }
      3: { xp_needed: 5000, max_members: 15, vaults: 2 }
```

---

## 🎮 4. Commands & Permissions
### Commands (Paper Game Server)

| Command | Action | Permission | Arguments |
| :--- | :--- | :--- | :--- |
| `/friend add <player>`| Sends a friend request. | `mdn.social.friend` | `<player>` (String) |
| `/friend accept <p>` | Accepts a pending friend request.| `mdn.social.friend` | `<p>` (String) |
| `/friend list` | Shows online/offline friends. | `mdn.social.friend` | None |
| `/team create <name>` | Establishes a new team/clan. | `mdn.social.team.create` | `<name>` (String) |
| `/team invite <player>`| Invites a player to join. | `mdn.social.team.invite` | `<player>` (String) |
| `/team accept` | Accepts a pending team invite. | None | None |
| `/team leave` | Exits the current clan. | None | None |
| `/team disband` | Disbands the current clan. | `mdn.social.team.disband`| None |
| `/team vault` | Opens the clan's shared storage. | `mdn.social.team.vault` | None |

---

## 🛡️ 5. Edge Cases & Solutions
* **Offline Leader Handover**:
  * *Issue*: A clan leader remains offline indefinitely, locking the clan members from managing rosters or disbands.
  * *Solution*: Implement a "Demote Idle Leader" system. If a clan leader does not log into the network for 30 consecutive days, the next highest-ranking online member (Co-Leader, then Officer) is promoted to Leader.
* **Vault Duplication Exploits**:
  * *Issue*: Two clan members attempt to open the shared GUI vault at the exact same time, pulling out items simultaneously to duplicate them.
  * *Solution*: Create a Redis state lock on the clan vault (`clan:vault:lock:<clan_id>`). When Member A opens the vault, write this lock key. If Member B tries to run `/team vault`, block the action and display a message: "The clan vault is currently being accessed by %player%." Release the lock as soon as the inventory UI closes.
* **Clan Disband Plot Cleanup**:
  * *Issue*: Disbanding a clan leaves active players inside the clan's private plot instance, causing memory leaks and pathing orphans.
  * *Solution*: The disband event fires a global `ClanDisbandEvent`. The SAM gameplay plugin listens to this event, grabs all players currently inside the disbanded clan's private plot coordinates, teleports them to the proxy hub, and initiates the plot reset sequence.
