# MineDrop Network Specification — MDN-Economy

## 📋 1. Overview
* **Consolidated Name**: `MDN-Economy`
* **Target Environment**: Paper (With Redis syncing)
* **Purpose**: Coordinates the server-wide transaction engine. This includes coin wallets, payment routing, Vault standard integration, a customized virtual Shop system, and a comprehensive global Auction House (`/ah`) system.

---

## 🛠️ 2. Core Modules & Mechanics

```
  [Vault API Calls] ────► [MDN-Economy Service]
                                │
          ┌─────────────────────┼─────────────────────┐
          ▼                     ▼                     ▼
    [Player Wallet]     [Auction House Engine]   [GUI Shop System]
(MySQL Read/Write Sync)     (MySQL + Redis)    (NPC Category Config)
```

### 1. Unified Wallet System
* Provides standard API hooks for Spigot's Vault framework, managing currency exchanges.
* Player balances are cached locally and synchronized via Redis Pub/Sub events on change to prevent cross-server desynchronization.

### 2. Global Auction House (`/ah`)
* **Listing**: Players can list items from their active hand using `/ah sell <price>`. Listed items are removed from player inventories, serialized (base64 NBT format), and saved to MySQL.
* **Browsing**: A dynamic inventory GUI. Supports pagination, item type category filters (e.g. Blocks, Tools, Minelings, Cosmetics), and search strings.
* **Purchase**: 
  * Left-Click: Spawns validation checks. If transaction succeeds, item is delivered and funds are routed.
  * Right-Click: Allows sellers to cancel listings and reclaim items.
* **Expiry**: Unsold listings expire automatically after a set duration (e.g. 48 hours) and are moved to an expired item queue for user recovery.

### 3. NPC merchant & GUI Shop
* Category-based item shops (e.g. buying structures, base protection, defense tools) configured through YAML.
* Restricts purchase actions if player balance is insufficient.

---

## ⚙️ 3. Configuration Template
### `config.yml`
```yaml
economy:
  starting-balance: 1000.0
  currency-symbol: "⛁"
  minimum-pay-amount: 1.0

auction-house:
  max-listings-per-player: 5
  listing-duration-hours: 48
  tax-percentage: 5.0 # Tax deducted on successful sale
  min-listing-price: 10.0
  max-listing-price: 10000000.0

shop:
  categories:
    protection:
      name: "&bBase Protection & Upgrades"
      npc-id: 12
      items:
        shield_generator:
          material: "BEACON"
          name: "&bShield Generator Block"
          lore:
            - "&7Protects base from Destroyer"
            - "&7Minelings for 2 hours."
          price: 5000.0
          commands:
            - "give %player% beacon 1"
```

---

## 🎮 4. Commands & Permissions
### Commands (Paper Game Server)

| Command | Action | Permission | Arguments |
| :--- | :--- | :--- | :--- |
| `/money` | Displays player's coin balance. | `mdn.economy.balance` | None |
| `/pay <player> <amount>`| Transfers coins to another player. | `mdn.economy.pay` | `<player> <amount>` |
| `/balancetop` | Shows the 10 richest players. | `mdn.economy.baltop` | None |
| `/ah` | Opens the Auction House UI. | `mdn.economy.ah.use` | None |
| `/ah sell <price>` | Lists held item for sale. | `mdn.economy.ah.sell` | `<price>` (Double) |
| `/ah expired` | Opens expired listing recovery UI. | `mdn.economy.ah.use` | None |
| `/eco <give/take/set> <p> <amt>`| Administrative wallet overrides. | `mdn.economy.admin` | `<give/take/set> <p> <amt>`|

---

## 🛡️ 5. Edge Cases & Solutions
* **Auction Purchase Duplication (Race Condition)**:
  * *Issue*: Two players attempt to click the same auction item at the exact same tick, resulting in both players receiving the item while only one pays.
  * *Solution*: Implement transaction locking in the code using sync locks. When a player clicks to purchase, mark the listing's ID as `status: processing` in memory. If another thread tries to access it, reject the query. Perform the balance verification and item database update inside a single synchronized SQL transaction block.
* **Offline Listing Claims**:
  * *Issue*: An item sells while the seller is offline, or a listing expires, leaving the seller's inventory full when they attempt to reclaim it.
  * *Solution*: Create an "Item Mailbox" delivery database table. Unclaimed or returned items are placed in this queue. When a player logs in with a full inventory, they are notified via chat to clear slots and use `/ah expired` or `/mailbox claim` to retrieve items.
* **Statue Rarity Price Floor Anti-Cheese**:
  * *Issue*: Players attempt to transfer massive amounts of money between accounts by listing a common item at an exorbitant price.
  * *Solution*: Establish dynamic price ceilings based on item types. For captured Minelings, define price ranges tied directly to their rarity tiers (e.g. Common Minelings cannot be listed for more than 5,000 coins).
