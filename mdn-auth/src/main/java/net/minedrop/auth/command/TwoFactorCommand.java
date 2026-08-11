package net.minedrop.auth.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minedrop.auth.AuthManager;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Handles 2FA commands: /2fa setup, /2fa verify, /2fa reset.
 */
public final class TwoFactorCommand implements SimpleCommand {

    private final AuthManager authManager;
    private final Logger logger;
    private final java.util.function.Consumer<com.velocitypowered.api.proxy.Player> onVerified;

    public TwoFactorCommand(AuthManager authManager, Logger logger,
                            java.util.function.Consumer<com.velocitypowered.api.proxy.Player> onVerified) {
        this.authManager = authManager;
        this.logger = logger;
        this.onVerified = onVerified;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length == 0) {
            invocation.source().sendMessage(Component.text()
                    .append(Component.text("2FA Commands:", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text("  /2fa setup", NamedTextColor.GRAY))
                    .append(Component.text(" — Set up two-factor authentication", NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("  /2fa verify <code>", NamedTextColor.GRAY))
                    .append(Component.text(" — Verify your 2FA code", NamedTextColor.WHITE))
                    .build());
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "setup" -> handleSetup(invocation);
            case "verify" -> handleVerify(invocation, args);
            case "reset" -> handleReset(invocation, args);
            default -> invocation.source().sendMessage(Component.text(
                    "Usage: /2fa <setup|verify|reset>", NamedTextColor.RED));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        // /2fa verify is allowed even while locked (no permission required)
        String[] args = invocation.arguments();
        if (args.length > 0 && "verify".equalsIgnoreCase(args[0])) {
            return true;
        }
        // setup and reset need permissions
        return invocation.source().hasPermission("mdn.auth.2fa.setup")
                || invocation.source().hasPermission("mdn.auth.2fa.admin.reset");
    }

    // ── Sub-command handlers ──

    private void handleSetup(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("Only players can set up 2FA.", NamedTextColor.RED));
            return;
        }

        if (!player.hasPermission("mdn.auth.2fa.setup")) {
            player.sendMessage(Component.text("You don't have permission to set up 2FA.", NamedTextColor.RED));
            return;
        }

        UUID uuid = player.getUniqueId();
        String username = player.getUsername();

        // Check if already configured
        if (authManager.hasTotpConfigured(uuid)) {
            player.sendMessage(Component.text()
                    .append(Component.text("⚠ ", NamedTextColor.YELLOW))
                    .append(Component.text("2FA is already set up. Ask an admin to reset it first with ", NamedTextColor.WHITE))
                    .append(Component.text("/2fa reset " + username, NamedTextColor.RED))
                    .build());
            return;
        }

        // Generate secret
        String otpauthUrl = authManager.setupTotp(uuid, username);
        if (otpauthUrl == null) {
            player.sendMessage(Component.text("Failed to generate 2FA secret. Please try again.", NamedTextColor.RED));
            return;
        }

        // Send setup instructions
        player.sendMessage(Component.text()
                .append(Component.text("══════ 2FA Setup ══════", NamedTextColor.GOLD, TextDecoration.BOLD))
                .build());
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text()
                .append(Component.text("1. ", NamedTextColor.GRAY))
                .append(Component.text("Open Google Authenticator or Authy", NamedTextColor.WHITE))
                .build());
        player.sendMessage(Component.text()
                .append(Component.text("2. ", NamedTextColor.GRAY))
                .append(Component.text("Scan the QR code or click the link below:", NamedTextColor.WHITE))
                .build());
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text()
                .append(Component.text("[Click to add to Authenticator]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(otpauthUrl)))
                .build());
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text()
                .append(Component.text("3. ", NamedTextColor.GRAY))
                .append(Component.text("Verify with: ", NamedTextColor.WHITE))
                .append(Component.text("/2fa verify <code>", NamedTextColor.YELLOW))
                .build());
        player.sendMessage(Component.text(""));

        logger.info("2FA setup initiated for {} ({})", username, uuid);
    }

    private void handleVerify(Invocation invocation, String[] args) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("Only players can verify 2FA.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /2fa verify <code>", NamedTextColor.RED));
            return;
        }

        // Parse code
        int code;
        try {
            code = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid code — must be a 6-digit number.", NamedTextColor.RED));
            return;
        }

        UUID uuid = player.getUniqueId();

        // Check if player is actually locked
        if (!authManager.isPlayerLocked(uuid)) {
            player.sendMessage(Component.text("You are not in a 2FA-locked state.", NamedTextColor.YELLOW));
            return;
        }

        // Verify
        if (authManager.verifyTotp(uuid, code)) {
            authManager.unlockPlayer(uuid);
            // Route player to lobby + clear title overlay
            onVerified.accept(player);
            player.sendMessage(Component.text()
                    .append(Component.text("✓ ", NamedTextColor.GREEN))
                    .append(Component.text("2FA verified! You are now authenticated.", NamedTextColor.GREEN))
                    .build());
            logger.info("2FA verification succeeded for {} ({})", player.getUsername(), uuid);
        } else {
            player.sendMessage(Component.text()
                    .append(Component.text("✗ ", NamedTextColor.RED))
                    .append(Component.text("Invalid 2FA code. Please try again.", NamedTextColor.RED))
                    .build());
            logger.warn("2FA verification failed for {} ({})", player.getUsername(), uuid);
        }
    }

    private void handleReset(Invocation invocation, String[] args) {
        if (!invocation.source().hasPermission("mdn.auth.2fa.admin.reset")) {
            invocation.source().sendMessage(Component.text("You don't have permission to reset 2FA.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            invocation.source().sendMessage(Component.text("Usage: /2fa reset <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[1];

        // Try to find the player from current online players
        // UUID lookup for offline players requires a database (future update)

        invocation.source().sendMessage(Component.text()
                .append(Component.text("⚠ ", NamedTextColor.YELLOW))
                .append(Component.text("2FA reset for " + targetName + " requested.", NamedTextColor.WHITE))
                .build());
        // Reset the target's TOTP secret if we can find their UUID
        var targetPlayer = java.util.Optional.<com.velocitypowered.api.proxy.Player>empty();
        // Note: proxy.getPlayer() returns Optional<Player> — we can't access proxy from here.
        // The reset requires the AuthVelocityPlugin to resolve the name to UUID.
        // For now, this is a stub — full implementation needs database-backed UUID lookup.
        invocation.source().sendMessage(Component.text()
                .append(Component.text("⚠ ", NamedTextColor.YELLOW))
                .append(Component.text("2FA reset requested. Full implementation requires database-backed UUID lookup.", NamedTextColor.GRAY))
                .build());

        logger.info("2FA reset requested for {} by {}", targetName,
                invocation.source() instanceof Player p ? p.getUsername() : "console");
    }
}
