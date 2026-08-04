# MineDrop Network Specification — MDN-Security

## 📋 1. Overview
* **Consolidated Name**: `MDN-Security`
* **Target Environment**: Velocity & Paper
* **Purpose**: Protections against malicious traffic, exploits, and cheating. It houses packet rate limiters, anti-VPN API hooks, automated anti-bot tests, transaction validation for the Economy module, and anti-teleport/anti-noclip validation logic to secure the "Steal a Mineling" stealth gameplay mechanics.

---

## 🛠️ 2. Architectural Security Zones
```
[Inbound Connections] ──► [Velocity Shield] (IP Rate limits, Anti-VPN check)
                                │
                                ▼
[Active Session]      ──► [Packet Inspector] (NBT exploits, rate caps)
                                │
                                ▼
[Paper Game State]    ──► [Gameplay Guard] (No-clip checks, transaction locks)
```

### Key Protections
1. **Velocity Anti-Bot**: Tracks connection spikes (e.g., more than 5 connections per second per IP block) and forces suspected bot accounts to solve a simple client-side captcha check (such as matching a block pattern or clicking an item in a GUI inventory) before they can join.
2. **Anti-VPN Verification**: Queries public databases (IP-API, ProxyCheck) dynamically on player join. Disallows connections from common hosting providers or public proxies.
3. **Exploit Filters (Paper)**: Blocks crashes associated with malformed NBT packets, oversized book updates, and creative mode item spawning exploits.
4. **SAM Gameplay Guard (Anti-Abuse)**: Prevents players from phase-teleporting inside locked enemy bases using third-party utility clients. Monitors player coordinate delta distances against block boundaries and validates all statue pick/drop interactions.

---

## ⚙️ 3. Configuration Template
### `config.yml`
```yaml
security:
  anti-vpn:
    enabled: true
    kick-message: "&cVPN/Proxy connections are not allowed on MineDrop."
    cache-duration-hours: 24
    api-endpoints:
      proxycheck: "http://proxycheck.io/v2/%ip%?key=api_key_placeholder"

  anti-bot:
    connection-threshold: 5 # Connections per second before locking down
    lockdown-duration-seconds: 60
    captcha-type: "GUI_ITEM_MATCH" # Options: GUI_ITEM_MATCH, MAP_RENDER

  exploit-prevention:
    packet-limits:
      max-packets-per-second: 150
      action: "KICK"
    nbt-max-size-bytes: 4096

  gameplay-guard:
    noclip-teleport-detection: true
    statue-pickup-distance: 4.5 # Max block distance to pick up a statue
```

---

## 🎮 4. Commands & Permissions
### Commands (Velocity & Paper)

| Command | Action | Permission | Arguments |
| :--- | :--- | :--- | :--- |
| `/security alert` | Toggles staff alerts for suspicious actions. | `mdn.security.alerts` | None |
| `/security blocklist`| Lists all blacklisted IPs and ranges. | `mdn.security.blacklist.view`| None |
| `/security addip <ip>`| Manually blocks an IP/Range. | `mdn.security.blacklist.add` | `<ip>` (String) |
| `/security bypass <p>`| Whitelists a player from VPN/packet checks. | `mdn.security.bypass.manage` | `<p>` (String) |

---

## 🛡️ 5. Edge Cases & Solutions
* **False Positives on Legitimate Multi-Connections**:
  * *Issue*: Siblings playing from the same household share the same IP, triggering the alt/bot threshold.
  * *Solution*: Allow staff to issue a static subnet exemption command `/security addsubnet <ip>`. Exempted IPs bypass connection threshold checks but are still subject to packet validation rules.
* **Stealth Teleportation (Base Phase Bypass)**:
  * *Issue*: Cheaters use fly/no-clip cheats to bypass base wall coordinates to steal Minelings.
  * *Solution*: Register a spatial validator thread on Spigot. If a player triggers a coordinate translation through solid blocks without having registered a valid gate opening, the event is cancelled, they are teleported to their own base spawn, and an alert is flagged to staff.
