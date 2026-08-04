package net.minedrop.communication;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

/** MDN-Communication — Velocity side. Handles Discord bridge and cross-proxy chat routing. */
@Plugin(id = "mdn-communication", name = "MDN-Communication", version = "1.0.0-SNAPSHOT",
        authors = {"MineDrop Network"}, dependencies = {@com.velocitypowered.api.plugin.Dependency(id = "mdn-core")})
public class CommunicationVelocityPlugin {
    private final ProxyServer proxy; private final Logger logger;
    @Inject public CommunicationVelocityPlugin(ProxyServer proxy, Logger logger) { this.proxy = proxy; this.logger = logger; }
    @Subscribe public void onProxyInitialize(ProxyInitializeEvent event) { logger.info("MDN-Communication (Velocity) loaded — TODO"); }
}
