# MineDrop Network Specification — MDN-Communication

## 📋 1. Overview
* **Consolidated Name**: `MDN-Communication`
* **Target Environment**: Velocity & Paper
* **Purpose**: Manages communication across the MineDrop network. This includes real-time global chat synchronisation (via Redis), personal private messaging channels, staff-only communications, automated message translators, chat slowmode/filters, and a bidirectional Discord bot bridge.

---

## 🛠️ 2. Messaging Flow & Integrations
```
[Player Types Message] ──► [Local Filtering] (Caps, Swear check)
                                │
                                ▼
[Proxy Distribution]   ──► [Redis Bus Publish] ──► [All Paper Server Chats]
                                │
                                └─────────────────► [Discord Webhook Channel]
```

### Key Modules
1. **Cross-Server Sync**: Distributes global chat format blocks via Redis. A message typed in Lobby-1 appears instantly in SAM-Plot-02.
2. **Discord Bridge**:
   * Synchronises global Minecraft chat with a designated Discord channel.
   * Relays Discord messages back to Minecraft with custom rank prefixes.
   * Generates real-time embed notifications for staff alerts (e.g. anti-cheat triggers, server state changes).
3. **Private Channel Routing**: Implements dedicated chat routes for `/friend msg` and `/clan chat` channels. These channels bypass the global chat packet path.
4. **Chat Moderation Filter**: Applies regex-based filters to block links, advertisements, toxic content, and spam. Supports temporary slowmode.

---

## ⚙️ 3. Configuration Template
### `config.yml`
```yaml
chat:
  format:
    global: "&8[&aGlobal&8] &7%mdn_clan_tag% &f%luckperms_prefix%%player%&7: &f%message%"
    staff: "&8[&cStaff&8] &e%server% &f%player%&7: &e%message%"
    clan: "&8[&bClan&8] &f%player%&7: &b%message%"

  filters:
    anti-spam:
      cooldown-seconds: 1.5
      caps-percentage-limit: 75
    blocked-words:
      - "hackur"
      - "ip_hijack_site"

discord:
  bot-token: "DISCORD_BOT_TOKEN"
  channels:
    global-bridge: "123456789012345678"
    staff-alerts: "876543210987654321"
```

---

## 🎮 4. Commands & Permissions
### Commands (Velocity & Paper)

| Command | Action | Permission | Arguments |
| :--- | :--- | :--- | :--- |
| `/chat slowmode <seconds>`| Limits chat posting rates. | `mdn.chat.admin.slowmode` | `<seconds>` (Integer) |
| `/chat lock` | Mutes global chat network-wide. | `mdn.chat.admin.lock` | None |
| `/msg <player> <msg>` | Sends a private message. | `mdn.chat.msg` | `<player> <msg>` |
| `/sc <message>` | Enters the private Staff Channel. | `mdn.chat.staff` | `<message>` (String) |
| `/cc <message>` | Enters the private Clan Channel. | `mdn.chat.clan` | `<message>` (String) |

---

## 🛡️ 5. Edge Cases & Solutions
* **Raid Chat Flood Griefing**:
  * *Issue*: Spam in local chat during matches disrupts gameplay and visibility.
  * *Solution*: Enable automatic "Local Plot Chat" toggles. When inside a plot region, players can toggle `/chat plot` to only view communication from players in their immediate plot instances.
* **Discord Bridge Rate Limits**:
  * *Issue*: Heavy game activity causes the Discord Bot to exceed Discord API limits, causing dropped chat bridge packets.
  * *Solution*: Queue outgoing Discord messages in a buffer thread on the proxy. Batch individual messages into single webhook payloads (sending up to 10 lines at a time every 2 seconds) during high-throughput events.
