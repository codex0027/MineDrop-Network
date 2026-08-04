# MineDrop Network Specification — MDN-Auth

## 📋 1. Overview
* **Consolidated Name**: `MDN-Auth`
* **Target Environment**: Velocity (Primary Auth Controller)
* **Purpose**: Coordinates player verification, device logging, two-factor authentication (2FA) for staff members, IP/Alt account tracking, and lobby token handshakes. It also verifies `service-secret` passwords for teams attempting to connect to private game lobbies.

---

## 🛠️ 2. Authentication Flow & Logic
```
[Player Connects]
       │
       ▼
[Check IP & Device Fingerprint]
       │
       ├─► (New Device Detected + Staff) ──► Block input ──► Request 2FA Code
       ├─► (Alt Limit Exceeded) ──► Kick player
       ▼
[Regular Verification]
       │
       ├─► Check Active Sessions ──► Join lobby
       ▼
[For Private Lobby Joins]
       │
       └─► Match Team Service-Secret ──► Allow/Deny transit
```

### Features
1. **Device Fingerprinting**: Captures client metrics on join (e.g., custom packets sent by the client, client locale, interface settings, network roundtrips) to flag suspicious alts or compromised accounts.
2. **Staff Two-Factor Authentication (2FA)**: Staff players are restricted from executing commands or moving until they provide a 6-digit authenticator code (Google Authenticator/Authy).
3. **Alt Detection**: Aggregates account logins by IP and fingerprint to flag when a user is exceeding the maximum allowable simultaneous connections (limit: 3 for public players).
4. **Service Secret Gateway**: When a user connects to a private lobby server via `/privatelobby`, the Auth module inspects the target server's secret token and matches it against the player's active team session data.

---

## ⚙️ 3. Configuration Template
### `config.yml`
```yaml
auth:
  alt-detection:
    max-accounts-per-ip: 3
    max-accounts-per-fingerprint: 2
    action: "ALERT" # Actions: KICK, ALERT, SHADOW_BAN

  staff-2fa:
    enabled: true
    enforce-ip-lock: true # Lock session to IP, requiring re-auth on IP change
    totp-issuer: "MineDropNetwork"
    force-for-permissions:
      - "mdn.group.admin"
      - "mdn.group.staff"

  private-lobbies:
    token-lifetime-seconds: 60
    secret-hashing-algorithm: "SHA-256"
```

---

## 🎮 4. Commands & Permissions
### Commands (Velocity Proxy)

| Command | Action | Permission | Arguments |
| :--- | :--- | :--- | :--- |
| `/2fa setup` | Generates a 2FA QR Code link. | `mdn.auth.2fa.setup` | None |
| `/2fa verify <code>`| Enters the 6-digit TOTP verification token. | None (usable while locked) | `<code>` (Integer) |
| `/2fa reset <player>`| Resets a staff member's 2FA key. | `mdn.auth.2fa.admin.reset` | `<player>` (String) |
| `/auth unblock <ip>`| Whitelists an IP from alt restrictions. | `mdn.auth.admin.unblock` | `<ip>` (String) |

---

## 💾 5. Database Schema (Internal Mapping)
Managed inside the SQL connection pool:
```sql
CREATE TABLE IF NOT EXISTS mdn_auth_totp (
    uuid VARCHAR(36) PRIMARY KEY,
    totp_secret VARCHAR(32) NOT NULL,
    backup_codes TEXT NOT NULL, -- Serialized JSON array of recovery codes
    ip_lock VARCHAR(45) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 🛡️ 6. Edge Cases & Solutions
* **Locked Input State Exploitation**:
  * *Issue*: While a player is awaiting 2FA inputs, they might receive chat messages, view surroundings, or trigger server plugins through packets.
  * *Solution*: The Auth plugin intercepts all inbound packets from the client (specifically movement, block interactions, inventory opens, and chat) during the pre-auth stage. The client is blinded via a black screen (using standard blindness status effects) and teleported to a void loading sector.
* **TOTP Time Drift**:
  * *Issue*: Host system clock drift causes staff 2FA validation codes to fail.
  * *Solution*: Program a drift buffer of `+/- 1` steps (30 seconds before and after) into the verification class to accommodate minor clock mismatches.
