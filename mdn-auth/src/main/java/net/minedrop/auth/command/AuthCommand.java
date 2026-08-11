package net.minedrop.auth.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minedrop.auth.AuthManager;
import org.slf4j.Logger;

/**
 * Handles /auth commands: /auth unblock <ip>
 */
public final class AuthCommand implements SimpleCommand {

    private final AuthManager authManager;
    private final Logger logger;

    public AuthCommand(AuthManager authManager, Logger logger) {
        this.authManager = authManager;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length == 0) {
            invocation.source().sendMessage(Component.text()
                    .append(Component.text("Auth Commands:", NamedTextColor.YELLOW))
                    .append(Component.newline())
                    .append(Component.text("  /auth unblock <ip>", NamedTextColor.GRAY))
                    .append(Component.text(" — Whitelist an IP from alt restrictions", NamedTextColor.WHITE))
                    .build());
            return;
        }

        String subCommand = args[0].toLowerCase();

        if ("unblock".equals(subCommand)) {
            handleUnblock(invocation, args);
        } else {
            invocation.source().sendMessage(Component.text(
                    "Usage: /auth <unblock>", NamedTextColor.RED));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("mdn.auth.admin.unblock");
    }

    private void handleUnblock(Invocation invocation, String[] args) {
        if (args.length < 2) {
            invocation.source().sendMessage(Component.text("Usage: /auth unblock <ip>", NamedTextColor.RED));
            return;
        }

        String ip = args[1];

        // Basic IPv4 validation
        if (!ip.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            invocation.source().sendMessage(Component.text("Invalid IP address format.", NamedTextColor.RED));
            return;
        }

        authManager.unblockIp(ip);

        invocation.source().sendMessage(Component.text()
                .append(Component.text("✓ ", NamedTextColor.GREEN))
                .append(Component.text("IP " + ip + " has been whitelisted from alt restrictions.", NamedTextColor.GREEN))
                .build());

        logger.info("IP {} unblocked by {}",
                ip, invocation.source() instanceof com.velocitypowered.api.proxy.Player p
                        ? p.getUsername() : "console");
    }
}
