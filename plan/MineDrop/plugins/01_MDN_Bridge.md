# MineDrop Network Specification — MDN-Bridge

## 📋 1. Overview
* **Consolidated Name**: `MDN-Bridge`
* **Target Environment**: Velocity & Paper
* **Purpose**: Serves as the security boundary and handshake module. No MineDrop plugin is allowed to load or execute unless MDN-Bridge validates its cryptographic signature, build hash, and runtime licensing key. It also manages secure key exchanges between local Paper servers and the Velocity proxy.

---

## 🏗️ 2. Mechanics & Security Flow
Every MDN plugin must include a `signature.json` compiled inside its jar containing an encrypted token.

```
[Target Plugin Load]
       │
       ▼
[MDN-Bridge Intercepts] ──► Reads signature.json (build hash + API key)
       │
       ▼
[Local Signature Validate]
       │
       ├─► (Invalid / Modified Build) ──► Hard stop: Disable plugin
       ▼
[Velocity Handshake Validation]
       │
       ├─► (Valid Response) ──► Enable Plugin
       └─► (Invalid / Unrecognized) ──► Emergency Shutdown Server
```

### Handshake Sequence
1. **Bootstrap Interception**: On Spigot `onLoad()` / Velocity `ProxyInitializeEvent`, plugins register their instance with `BridgeManager.register(this)`.
2. **Signature Verification**:
   * Bridge reads `signature.json` from the target plugin's resource stream.
   * Compares the SHA-256 build hash against the runtime Jar hash to verify the jar was not modified (prevents injection of backdoors).
3. **Velocity Callback**:
   * Game server sends a verification challenge to Velocity using the secret encryption key.
   * If Velocity does not acknowledge with a matching HMAC signature within 10 seconds, the Paper server shuts down automatically to prevent isolated, unverified instances from starting.

---

## ⚙️ 3. Configuration Template
### `config.yml`
```yaml
# MDN-Bridge Security Configuration
bridge:
  server-identity: "paper-lobby-01"
  secret-api-key: "ENC(mD9xK1w8P2qR4sT5uW8zY1x3v5b7n9m0)" # Encrypted AES key
  handshake-timeout-seconds: 10
  allowed-build-hashes:
    - "a1e89b4f2c03e6d19f8a3c57e2d960b123456789abcdef0123456789abcdef01" # MDN-Core-1.0.0
    - "f9d8c7b6a5e4d3c2b1a0f9e8d7c6b5a43210fedcba9876543210fedcba987654" # MDN-SAM-1.0.0

verification-failure:
  action: "SHUTDOWN" # Options: SHUTDOWN, DISABLE_PLUGIN, WARN_STAFF
  alert-webhook: "https://discord.com/api/webhooks/security-alerts"
```

---

## 📡 4. Developer API Hook
Other plugins call this to check security clearance.

```java
package net.minedrop.bridge.api;

public interface BridgeSecurityProvider {
    /**
     * Checks if a plugin is verified and running in a secure context.
     * @param pluginId Unique name of the plugin (e.g., "MDN-SAM")
     * @return true if signature is valid and active
     */
    boolean isPluginSecure(String pluginId);

    /**
     * Retrieves the decrypted session token for inter-server communication.
     */
    String getActiveSessionToken();
}
```

---

## 🛡️ 5. Edge Cases & Solutions
* **Local Developer Environment Bypass**:
  * *Issue*: Developers need to test compilation without checking against production build hashes.
  * *Solution*: Allow a `debug-mode: true` flag in the configuration, but **only** if the server is running on `localhost` (127.0.0.1). If `debug-mode` is true and a public IP is detected on startup, override the flag, trigger an alert, and shut down.
* **Network Lag Triggers Timeout**:
  * *Issue*: High server load during startups causes handshake packets to delay, causing premature server shutdowns.
  * *Solution*: Implement a retry buffer (3 attempts spaced 3 seconds apart) before firing the shutdown sequence.
