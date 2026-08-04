# OraxenPlus 🚀

**OraxenPlus** is a next-generation, high-performance Minecraft Paper/Spigot plugin for **Minecraft 1.21.1+** inspired by Oraxen, enhanced with powerful modern capabilities including an **In-Game Visual Item Editor**, **Resource Pack Engine with Glyph/Emoji System**, **Dynamic Item Leveling**, **Item Sets**, **Item Fusion/Combiner**, **Cosmetic Skins**, **Custom Enchantments**, **Custom Drops**, and a **Developer API**.

---

## 🌟 Key Capabilities

### 1. Custom Items & Models System
- **YAML Configuration & Inheritance**: Create custom items with template inheritance (`parent: base_legendary`).
- **Modern 1.21.4+ Item Model & Custom Model Data Support**: Direct integration with `custom_model_data` and modern `item_model` data components.
- **Custom Attributes & Durability**: Configurable attack damage, attack speed, armor, knockback resistance, unbreakable status, and custom max durability stored via `PersistentDataContainer`.
- **Custom Consumables**: Food/potion items with custom nutrition, saturation, eating sounds, cooldowns, and potion effects.
- **Item Abilities**: Right-click, shift-right-click, on-hit, on-kill, and passive abilities with customizable particle effects, sound effects, potion buffs, command executions, lightning bolts, and explosions.

### 2. Automatic Resource Pack System
- **Built-in HTTP Server**: Auto-packages textures, models, sounds, and font glyphs into `pack.zip`, generates SHA-1 checksums, and hosts an embedded web server on port `8085` (configurable).
- **Auto-Distribution & Prompt**: Sends resource pack prompts to players on join with customizable MiniMessage text and hash verification.
- **Font & Glyph/Emoji System**: Custom character replacements (e.g., `:sword:`, `:coins:`, `:heart_custom:`) mapping custom bitmapped font textures into chat, item display names, lore, and GUIs.

### 3. Advanced Features (Over Standard Oraxen)
- **In-Game Item Editor GUI (`/oraxenplus editor`)**: Modify display names, lore, materials, custom model data, attributes, and unbreakable flags visually without opening YAML files!
- **Visual Item Browser (`/oraxenplus browser`)**: Browse, search, and click to give custom items in-game.
- **Item Fusion Station (`/oraxenplus fusion`)**: Anvil-style 3-slot workbench for combining primary items, secondary items, and catalysts into upgraded custom items.
- **Dynamic Item Leveling**: Items gain XP from killing mobs and breaking blocks, leveling up stats and updating lore dynamically.
- **Item Set Bonuses (`ItemSetManager`)**: Set bonuses when equipping multiple set pieces (e.g., 4/4 Dragon Armor grants permanent Strength & Fire Resistance).
- **Custom Enchantments**: Configurable custom enchantments (such as *Lifesteal*) with lore decoration and combat triggers.
- **Item Recycling Station (`/oraxenplus recycle`)**: Salvage unwanted custom items back into raw materials and XP refund.
- **Custom Projectiles**: Throwable items with customizable trail particles and impact explosions/lightning.
- **Custom Mob & Block Drops**: Per-entity and per-block drop tables with chance, looting modifiers, and quantity ranges.
- **Vault Economy Integration & Custom Item Shop (`/oraxenplus shop`)**: Buy and sell custom items using Vault economy or internal balance fallback.

---

## 🛠️ Installation & Setup

1. Requirements:
   - Minecraft Server: Paper or Spigot **1.21.1+**
   - Java Version: **Java 21+**
   - Soft Dependencies: Vault (for economy), PlaceholderAPI (for placeholders)
2. Drop `OraxenPlus-1.0.0.jar` into your server's `plugins/` directory.
3. Start the server. OraxenPlus will generate default configuration folders (`items/`, `recipes/`, `glyphs/`, `drops/`, `sets/`, `enchantments/`, `fusion/`, `pack/`).
4. Ensure port `8085` (or configured port) is open on your firewall if hosting the built-in HTTP server for resource pack distribution.

---

## 📜 Commands & Permissions

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/oraxenplus give <player> <item> [amount]` | `oraxenplus.admin` | Give custom item to player |
| `/oraxenplus browser` | `oraxenplus.admin` | Open visual catalog of all registered custom items |
| `/oraxenplus editor [item]` | `oraxenplus.admin` | Open interactive in-game item editor GUI |
| `/oraxenplus shop` | `oraxenplus.user` | Open custom items buy/sell shop GUI |
| `/oraxenplus fusion` | `oraxenplus.user` | Open item fusion workbench |
| `/oraxenplus recycle` | `oraxenplus.user` | Open item recycling / salvaging station |
| `/oraxenplus pack [send\|reload]` | `oraxenplus.admin` | Send or regenerate the resource pack |
| `/oraxenplus reload` | `oraxenplus.admin` | Reload all configurations, items, and resource pack |
| `/oraxenplus debug` | `oraxenplus.admin` | View plugin diagnostics and resource pack status |

---

## 👨‍💻 Developer API

Other plugins can hook into `OraxenPlusAPI`:

```java
import io.github.oraxenplus.api.OraxenPlusAPI;
import io.github.oraxenplus.item.CustomItem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;

// Get Custom Item Definition
CustomItem item = OraxenPlusAPI.getItem("excalibur");

// Create ItemStack
ItemStack itemStack = OraxenPlusAPI.getItemStack("excalibur", 1);

// Check if ItemStack is custom
boolean isCustom = OraxenPlusAPI.isCustomItem(itemStack);
String customId = OraxenPlusAPI.getCustomItemId(itemStack);

// Prompt Resource Pack to Player
OraxenPlusAPI.sendResourcePack(player);
```

### Custom Events
- `CustomItemUseEvent`: Fired when a custom item ability is triggered.
- `CustomItemHitEvent`: Fired when a custom item hits an entity.
- `CustomItemCraftEvent`: Fired when crafting a custom item.
- `CustomItemLevelUpEvent`: Fired when an item gains enough XP to level up.
- `ResourcePackGenerateEvent`: Fired after the zip resource pack is compiled.
- `ResourcePackSendEvent`: Fired before sending the resource pack prompt to a player.

### PlaceholderAPI Integration
- `%oraxenplus_item_<id>_name%`
- `%oraxenplus_item_<id>_buy%`
- `%oraxenplus_item_<id>_sell%`
- `%oraxenplus_pack_hash%`

---

## 🎨 Sample Item Configuration (`items/weapons.yml`)

```yaml
excalibur:
  parent: "base_legendary"
  material: NETHERITE_SWORD
  display_name: "<gradient:#FFD700:#FFA500><bold>Excalibur</bold></gradient> :sword:"
  custom_model_data: 10001
  item_model: "oraxenplus:weapons/excalibur"
  lore:
    - "<gray>The legendary sword of kings.</gray>"
    - ""
    - "<yellow><b>Abilities:</b></yellow>"
    - " <gold>[Right Click]</gold> <yellow>Holy Wrath</yellow> (10s Cooldown)"
  attack_damage: 15.0
  attack_speed: 1.8
  durability: 2500
  levelable: true
  max_level: 10
  xp_per_kill: 25
  buy_price: 5000.0
  sell_price: 2500.0
  abilities:
    - trigger: RIGHT_CLICK
      cooldown: 10.0
      actions:
        - type: LIGHTNING
        - type: POTION
          effect: INCREASE_DAMAGE
          duration: 100
          amplifier: 1
        - type: SOUND
          sound: "entity.lightning_bolt.thunder"
          volume: 1.0
          pitch: 1.2
```

---
*Built with ❤️ by Antigravity Team for Minecraft 1.21.1+*
~/oraxenplus $