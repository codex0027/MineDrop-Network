package net.minedrop.auth.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minedrop.auth.AuthManager;
import org.slf4j.Logger;

/**
 * Handles /auth admin commands:
 * /auth unblock <ip>, /auth clear <ip>, /auth suspend <player>, /auth unsuspend <player>
 */
public final class AuthCommand implements SimpleCommand {

    private final AuthManager authManager;
    private final Logger logger;
    private final java.util.function.Function<String, java.util.Optional<com.velocitypowered.api.proxy.Player>> playerResolver;

    public AuthCommand(AuthManager authManager, Logger logger,
                       java.util.function.Function<String, java.util.Optional<com.velocitypowered.api.proxy.Player>> playerResolver) {
        this.authManager = authManager;
        this.logger = logger;
        this.playerResolver = playerResolver;
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
                    .append(Component.newline())
                    .append(Component.text("  /auth clear <ip>", NamedTextColor.GRAY))
                    .append(Component.text(" — Clear alt tracking data for an IP", NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("  /auth suspend <player>", NamedTextColor.GRAY))
                    .append(Component.text(" — Suspend an account", NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("  /auth unsuspend <player>", NamedTextColor.GRAY))
                    .append(Component.text(" — Unsuspend an account", NamedTextColor.WHITE))
                    .build());
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "unblock" -> handleUnblock(invocation, args);
            case "clear" -> handleClear(invocation, args);
            case "suspend" -> handleSuspend(invocation, args);
            case "unsuspend" -> handleUnsuspend(invocation, args);
            default -> invocation.source().sendMessage(Component.text(
                    "Usage: /auth <unblock|clear|suspend|unsuspend>", NamedTextColor.RED));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        // Allow access if player has ANY of the auth admin permissions
        return invocation.source().hasPermission("mdn.auth.admin")
                || invocation.source().hasPermission("mdn.auth.admin.unblock");
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

    private void handleClear(Invocation invocation, String[] args) {
        if (args.length < 2) {
            invocation.source().sendMessage(Component.text("Usage: /auth clear <ip>", NamedTextColor.RED));
            return;
        }

        String ip = args[1];

        // Basic IPv4 validation
        if (!ip.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            invocation.source().sendMessage(Component.text("Invalid IP address format.", NamedTextColor.RED));
            return;
        }

        long cleared = authManager.clearIp(ip);

        invocation.source().sendMessage(Component.text()
                .append(Component.text("✓ ", NamedTextColor.GREEN))
                .append(Component.text("Cleared " + cleared + " UUID(s) from alt tracking for IP " + ip + ".", NamedTextColor.GREEN))
                .build());
        invocation.source().sendMessage(Component.text()
                .append(Component.text("The IP whitelist entry was also removed — it can now be re-tracked.", NamedTextColor.GRAY))
                .build());

        logger.info("IP {} alt data cleared ({} UUIDs) by {}",
                ip, cleared,
                invocation.source() instanceof com.velocitypowered.api.proxy.Player p
                        ? p.getUsername() : "console");
    }

    private void handleSuspend(Invocation invocation, String[] args) {
        if (args.length < 2) {
            invocation.source().sendMessage(Component.text("Usage: /auth suspend <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[1];
        String executorName = invocation.source() instanceof com.velocitypowered.api.proxy.Player p
                ? p.getUsername() : "console";

        var targetUuid = authManager.resolveUsername(targetName, playerResolver);

        // Fallback: try Redis username→UUID
        if (targetUuid.isEmpty()) {
            invocation.source().sendMessage(Component.text()
                    .append(Component.text("✗ ", NamedTextColor.RED))
                    .append(Component.text("Player '" + targetName + "' not found. They must have logged in at least once.", NamedTextColor.RED))
                    .build());
            return;
        }

        if (authManager.suspendAccount(targetUuid.get(), "Suspended by " + executorName)) {
            invocation.source().sendMessage(Component.text()
                    .append(Component.text("✓ ", NamedTextColor.GREEN))
                    .append(Component.text("Account " + targetName + " has been suspended.", NamedTextColor.GREEN))
                    .build());
            logger.info("Account {} suspended by {}", targetName, executorName);
        } else {
            invocation.source().sendMessage(Component.text("Failed to suspend account. Check database connectivity.", NamedTextColor.RED));
        }
    }

    private void handleUnsuspend(Invocation invocation, String[] args) {
        if (args.length < 2) {
            invocation.source().sendMessage(Component.text("Usage: /auth unsuspend <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[1];
        String executorName = invocation.source() instanceof com.velocitypowered.api.proxy.Player p
                ? p.getUsername() : "console";

        var targetUuid = authManager.resolveUsername(targetName, playerResolver);

        if (targetUuid.isEmpty()) {
            invocation.source().sendMessage(Component.text()
                    .append(Component.text("✗ ", NamedTextColor.RED))
                    .append(Component.text("Player '" + targetName + "' not found.", NamedTextColor.RED))
                    .build());
            return;
        }

        if (authManager.unsuspendAccount(targetUuid.get())) {
            invocation.source().sendMessage(Component.text()
                    .append(Component.text("✓ ", NamedTextColor.GREEN))
                    .append(Component.text("Account " + targetName + " has been unsuspended.", NamedTextColor.GREEN))
                    .build());
            logger.info("Account {} unsuspended by {}", targetName, executorName);
        } else {
            invocation.source().sendMessage(Component.text("Failed to unsuspend account.", NamedTextColor.RED));
        }
    }
}
