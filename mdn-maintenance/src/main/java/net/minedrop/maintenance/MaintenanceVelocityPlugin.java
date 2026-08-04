package net.minedrop.maintenance;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

/** MDN-Maintenance — Velocity side. Blocks new connections, broadcasts warnings. */
@Plugin(id = "mdn-maintenance", name = "MDN-Maintenance", version = "1.0.0-SNAPSHOT",
        authors = {"MineDrop Network"}, dependencies = {@com.velocitypowered.api.plugin.Dependency(id = "mdn-core")})
public class MaintenanceVelocityPlugin {
    private final Logger logger;
    @Inject public MaintenanceVelocityPlugin(ProxyServer proxy, Logger logger) { this.logger = logger; }
    @Subscribe public void onProxyInitialize(ProxyInitializeEvent event) { logger.info("MDN-Maintenance (Velocity) loaded — TODO"); }
}
