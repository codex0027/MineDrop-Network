# MineDrop Network — Command Reference

> **Last Updated**: August 11, 2026 — Lobby freeze system (AuthFreezeManager)
> **Total Commands**: 17 across 2 plugins (mdn-core, mdn-auth)

---

## MDN-Core Commands

All MDN-Core commands are registered on both Velocity proxy AND Paper servers (where applicable).

### `/hub` | `/lobby` | `/spawn`
**Permission**: None (all players)  
**Platform**: Velocity only  
**Usage**:
```
/hub
/lobby
/spawn
```
Routes the player to the best available lobby server. All three aliases behave identically.

---

### `/mdn <servers|health|reload>`
**Permission**: `mdn.admin.core`  
**Platform**: Velocity only  
**Usage**:
```
/mdn servers     — List all registered servers with TPS, player count, health status
/mdn health      — Network health overview (servers, players, sessions, Redis status)
/mdn reload      — Reload configuration (currently stub)
```
**Examples**:
```
/mdn servers
  ✓ lobby-01 12/50 TPS:20.0 lobby
  ✓ sam-public-01 8/30 TPS:19.8 sam-public

/mdn health
  Network Health:
    Servers: 5
    Online players (proxy): 23
    Active sessions: 23
    Redis: connected
```

---

### `/website`
**Permission**: None (all players)  
**Platform**: Velocity only  
**Usage**: `/website` — Displays the server website URL.

---

### `/store`
**Permission**: None (all players)  
**Platform**: Velocity only  
**Usage**: `/store` — Displays the server store URL.

---

### `/vote`
**Permission**: None (all players)  
**Platform**: Velocity only  
**Usage**: `/vote` — Displays the voting URL.

---

### `/discord`
**Permission**: None (all players)  
**Platform**: Velocity only  
**Usage**: `/discord` — Displays the Discord invite link.

---

### `/help`
**Permission**: None (all players)  
**Platform**: Velocity only  
**Usage**: `/help` — Shows available help commands.

---

### `/rules`
**Permission**: None (all players)  
**Platform**: Velocity only  
**Usage**: `/rules` — Displays server rules summary.

---

## MDN-Auth Commands

All MDN-Auth commands are Velocity-only (authentication authority runs on the proxy).

### `/register <password>`
**Permission**: None (all players — only works for unregistered accounts)  
**Platform**: Velocity only  
**Password Requirements**:
- Minimum 12 characters
- Maximum 128 characters
- Cannot contain your username
- Cannot be a common weak password (e.g., "password", "minecraft123")

**Usage**:
```
/register MySecurePassword123
```
**Flow**: Creates an MDN account with Argon2id password hash → auto-authenticates → routes to lobby.

**Error Messages**:
| Message | Meaning |
|---------|---------|
| `This account is already registered. Use /login` | Account exists |
| `Password must be at least 12 characters` | Too short |
| `Password cannot be the same as your username` | Invalid |
| `This password is too common` | Rejected |

---

### `/login <password>`
**Permission**: None (all players — requires registered account)  
**Platform**: Velocity only  
**Rate Limit**: 5 failed attempts per 5 minutes (per UUID + per IP)

**Usage**:
```
/login MySecurePassword123
```
**Flow**: Verifies Argon2id hash → checks account status → if 2FA configured → `/2fa verify` required → else → authenticated → lobby.

**States**:
| State | Result |
|-------|--------|
| `SUCCESS` | 2FA check → TOTP_REQUIRED or AUTHENTICATED |
| `INVALID_CREDENTIALS` | Generic error (no account enumeration) |
| `ACCOUNT_SUSPENDED` | Disconnected with suspension message |
| `RATE_LIMITED` | Disconnected with 5-minute cooldown |
| `DATABASE_ERROR` | Temporary unavailable message |

---

### `/2fa setup`
**Permission**: `mdn.auth.2fa.setup`  
**Platform**: Velocity only  

**Usage**:
```
/2fa setup
```
**Flow**: Generates TOTP secret → displays QR code link → player adds to authenticator app → verifies with `/2fa verify <code>`.

Cannot be used if 2FA is already configured. Use `/2fa reset` first.

---

### `/2fa verify <code>`
**Permission**: None (works even while locked)  
**Platform**: Velocity only  
**Rate Limit**: 5 failed attempts per 15 minutes

**Usage**:
```
/2fa verify 123456
```
**Flow**:
1. Checks IP lock (if `enforce-ip-lock: true`) — rejects if IP prefix changed
2. Validates 6-digit TOTP code (±30s drift)
3. On success: unlocks player → authenticates → routes to lobby

**IP Lock**: After first successful 2FA, the player's IP /24 prefix is locked. Future 2FA attempts from a different network will be rejected with: `IP address changed! Please reconnect from your original network.`

---

### `/2fa verify-backup <code>`
**Permission**: None (works even while locked)  
**Platform**: Velocity only  
**Shares rate limiter with `/2fa verify`**

**Usage**:
```
/2fa verify-backup 12345678
```
**Flow**: Validates an 8-digit backup recovery code → consumes it (single-use) → authenticates → prompts to set up new 2FA.

Used when TOTP device is lost but backup codes are available.

---

### `/2fa reset <player>`
**Permission**: `mdn.auth.2fa.admin.reset`  
**Platform**: Velocity only  

**Usage**:
```
/2fa reset Steve
```
**Flow**: Resolves username→UUID → deletes TOTP secret from MySQL+Redis → unlocks player if stuck → prompts re-setup.

Player must have logged in at least once (Redis username→UUID mapping required).

---

### `/auth unblock <ip>`
**Permission**: `mdn.auth.admin.unblock` or `mdn.auth.admin`  
**Platform**: Velocity only  

**Usage**:
```
/auth unblock 192.168.1.100
```
**Effect**: Adds IP to permanent whitelist — alt tracking limits no longer apply. Alt data is preserved.

---

### `/auth clear <ip>`
**Permission**: `mdn.auth.admin.unblock` or `mdn.auth.admin`  
**Platform**: Velocity only  

**Usage**:
```
/auth clear 192.168.1.100
```
**Effect**: Deletes ALL alt tracking data for the IP AND removes whitelist entry. More aggressive than `unblock` — completely wipes the slate clean. Shows count of UUIDs removed.

---

### `/auth suspend <player>`
**Permission**: `mdn.auth.admin`  
**Platform**: Velocity only  

**Usage**:
```
/auth suspend Steve
```
**Effect**:
1. Sets account status to `SUSPENDED` in MySQL
2. Revokes all active sessions
3. Publishes `AUTH_UPDATE(false)`
4. Player cannot `/login` (gets disconnected with suspension message)

**Reverse**: `/auth unsuspend <player>`

---

### `/password change <current> <new>`
**Permission**: None (must be authenticated)  
**Platform**: Velocity only  

**Usage**:
```
/password change MyOldPass MyNewPass123
```
**Flow**: Verifies current password → hashes new password with Argon2id → updates MySQL → revokes all other sessions.

---

### `/password reset <totp|backup|recovery> <code> <new>`
**Permission**: None (proof-based)  
**Platform**: Velocity only  

**Usage**:
```
/password reset totp 123456 MyNewPass123
/password reset backup 12345678 MyNewPass123
/password reset recovery abc123def456 MyNewPass123
```
**Flow**:
- `totp` — verifies 6-digit TOTP code → resets password → revokes all sessions
- `backup` — verifies backup code (consumed) → resets password → revokes all sessions → prompts new 2FA
- `recovery` — validates admin-generated token → resets password → clears TOTP → revokes all sessions

---

### `/auth recovery <player>`
**Permission**: `mdn.auth.admin`  
**Platform**: Velocity only  

**Usage**:
```
/auth recovery Steve
```
**Effect**: Generates one-time recovery token (15-min TTL, SHA-256 hashed in Redis). Admin gives token to player securely. Player uses `/password reset recovery <token> <new_password>`. Clears TOTP + backup codes on successful recovery.

---

### `/auth unsuspend <player>`
**Permission**: `mdn.auth.admin`  
**Platform**: Velocity only  

**Usage**:
```
/auth unsuspend Steve
```
**Effect**: Sets account status to `ACTIVE` — player can authenticate again.

---

## Command Index by Permission

| Permission | Commands |
|-----------|----------|
| *(none)* | `/hub`, `/lobby`, `/spawn`, `/website`, `/store`, `/vote`, `/discord`, `/help`, `/rules`, `/register`, `/login`, `/password change`, `/password reset`, `/2fa verify`, `/2fa verify-backup` |
| `mdn.auth.2fa.setup` | `/2fa setup` |
| `mdn.auth.2fa.admin.reset` | `/2fa reset` |
| `mdn.auth.admin.unblock` | `/auth unblock`, `/auth clear` |
| `mdn.auth.admin` | `/auth suspend`, `/auth unsuspend`, `/auth unblock`, `/auth clear`, `/auth recovery` |
| `mdn.admin.core` | `/mdn servers`, `/mdn health`, `/mdn reload` |

---

## Command Index by Plugin

### MDN-Core (Velocity)
| Command | Aliases | Permission |
|---------|---------|------------|
| `/hub` | `/lobby`, `/spawn` | None |
| `/mdn servers` | `/mdn list` | `mdn.admin.core` |
| `/mdn health` | — | `mdn.admin.core` |
| `/mdn reload` | — | `mdn.admin.core` |
| `/website` | — | None |
| `/store` | — | None |
| `/vote` | — | None |
| `/discord` | — | None |
| `/help` | — | None |
| `/rules` | — | None |

### MDN-Auth (Velocity)
| Command | Permission |
|---------|------------|
| `/register <password>` | None |
| `/login <password>` | None |
| `/password change <current> <new>` | None |
| `/password reset <method> <code> <new>` | None |
| `/2fa setup` | `mdn.auth.2fa.setup` |
| `/2fa verify <code>` | None |
| `/2fa verify-backup <code>` | None |
| `/2fa reset <player>` | `mdn.auth.2fa.admin.reset` |
| `/auth unblock <ip>` | `mdn.auth.admin.unblock` |
| `/auth clear <ip>` | `mdn.auth.admin.unblock` |
| `/auth suspend <player>` | `mdn.auth.admin` |
| `/auth unsuspend <player>` | `mdn.auth.admin` |
| `/auth recovery <player>` | `mdn.auth.admin` |
| `/auth status <player>` | `mdn.auth.admin` |

---

## 🌐 Network Features (Non-Command)

### Lobby Freeze System (Paper-side, MDN-Core)

When a player joins the lobby before authenticating via Velocity/MDN-Auth:

- **Spawn**: Teleported to configurable auth spawn location
- **Movement**: X/Y/Z locked, yaw/pitch allowed (player can look around)
- **Damage**: EntityDamageEvent + EntityDamageByEntityEvent blocked
- **Blocks**: BlockBreakEvent, BlockPlaceEvent blocked
- **Interaction**: PlayerInteractEvent, PlayerInteractEntityEvent, PlayerInteractAtEntityEvent blocked
- **Inventory**: InventoryOpenEvent, InventoryClickEvent, InventoryDragEvent blocked
- **Items**: PlayerDropItemEvent, EntityPickupItemEvent, PlayerItemConsumeEvent blocked
- **Commands**: Only whitelisted auth commands allowed (register, login, password, 2fa, help)
- **Teleport**: Only to auth spawn location
- **BossBar**: Yellow bar with auth instructions
- **Title**: Join + unfreeze success titles
- **Timeout**: Configurable (default 120s), player kicked with message
- **Fail-closed**: Unknown state = frozen, never assume authenticated

**Config** (`plugins/mdn-core/config.yml`):
```yaml
authentication:
  enabled: true
  spawn:
    world: "world"
    x: 0.5
    y: 100.0
    z: 0.5
    yaw: 180.0
    pitch: 0.0
  freeze:
    allow-look: true
  timeout-seconds: 120
  allowed-commands:
    - "register"
    - "login"
    - "password"
    - "2fa"
    - "help"
  show-title: true
```

### AUTH_UPDATE Flow (Velocity → Paper)

1. Player connects → Velocity publishes `AUTH_UPDATE(false)` to Redis `mdn_auth` channel
2. Lobby receives → freezes player at auth spawn
3. Player authenticates → MDN-Auth publishes `AUTH_UPDATE(true)`
4. Lobby receives → unfreezes player, gameplay begins
5. Player disconnects → Velocity publishes `AUTH_UPDATE(false)` → lobby cleans up

**Duplicate connection protection**: A new connection for the same UUID does NOT freeze/kick the existing authenticated player.
