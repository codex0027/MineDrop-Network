package net.minedrop.auth;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

/**
 * MDN-Auth — Velocity-side authentication controller.
 *
 * <h3>TODO — Implementation Checklist</h3>
 * <ol>
 *   <li>Player login interception — check IP & device fingerprint</li>
 *   <li>Staff 2FA — block input until TOTP verified, blind screen</li>
 *   <li>Alt detection — max 3 accounts per IP, alert on exceed</li>
 *   <li>Private lobby service-secret gateway</li>
 *   <li>Commands: /2fa setup, /2fa verify, /2fa reset, /auth unblock</li>
 *   <li>Database: mdn_auth_totp table (see MDN-API DatabaseSchema)</li>
 * </ol>
 *
 * @see plan/MineDrop/plugins/03_MDN_Auth.md
 */
@Plugin(
        id = "mdn-auth",
        name = "MDN-Auth",
        version = "1.0.0-SNAPSHOT",
        authors = {"MineDrop Network"},
        dependencies = {
                @com.velocitypowered.api.plugin.Dependency(id = "mdn-bridge"),
                @com.velocitypowered.api.plugin.Dependency(id = "mdn-core")
        }
)
public class AuthVelocityPlugin {

    private final ProxyServer proxy;
    private final Logger logger;

    @Inject
    public AuthVelocityPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("MDN-Auth loaded — awaiting implementation.");
        // TODO: Implement authentication flow (see class Javadoc)
    }
}
