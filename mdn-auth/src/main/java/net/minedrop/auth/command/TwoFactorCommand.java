package net.minedrop.auth.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minedrop.auth.AuthManager;
import net.minedrop.auth.TotpManager;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Handles 2FA commands: /2fa setup, /2fa verify, /2fa reset.
 */
public final class TwoFactorCommand implements SimpleCommand {

    private final AuthManager authManager;
    private final Logger logger;
    private final Consumer<Player> onVerified;
    private final Function<String, Optional<Player>> playerResolver;
    private final boolean enforceIpLock;

    public TwoFactorCommand(AuthManager authManager, Logger logger,
                            Consumer<Player> onVerified,
                            Function<String, Optional<Player>> playerResolver,
                            boolean enforceIpLock) {
        this.authManager = authManager;
        this.logger = logger;
        this.onVerified = onVerified;
        this.playerResolver = playerResolver;
        this.enforceIpLock = enforceIpLock;
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
                    .append(Component.newline())
                    .append(Component.text("  /2fa verify-backup <code>", NamedTextColor.GRAY))
                    .append(Component.text(" — Use a backup recovery code", NamedTextColor.WHITE))
                    .build());
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "setup" -> handleSetup(invocation);
            case "verify" -> handleVerify(invocation, args);
            case "verify-backup" -> handleVerifyBackup(invocation, args);
            case "reset" -> handleReset(invocation, args);
            default -> invocation.source().sendMessage(Component.text(
                    "Usage: /2fa <setup|verify|verify-backup|reset>", NamedTextColor.RED));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        // /2fa verify and verify-backup are allowed even while locked (no permission required)
        String[] args = invocation.arguments();
        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            if ("verify".equals(sub) || "verify-backup".equals(sub)) {
                return true;
            }
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
        String currentIp = player.getRemoteAddress().getAddress().getHostAddress();

        // Check if player is actually locked
        if (!authManager.isPlayerLocked(uuid)) {
            player.sendMessage(Component.text("You are not in a 2FA-locked state.", NamedTextColor.YELLOW));
            return;
        }

        // ── Verify with IP lock (A-2) ──
        TotpManager.IpVerifyResult result = authManager.verifyTotpWithIpLock(uuid, code, currentIp, enforceIpLock);

        switch (result) {
            case SUCCESS -> {
                // Set IP lock on first successful verify (A-2)
                if (enforceIpLock) {
                    authManager.updateTotpIpLock(uuid, currentIp);
                }
                authManager.unlockPlayer(uuid);
                onVerified.accept(player);
                player.sendMessage(Component.text()
                        .append(Component.text("✓ ", NamedTextColor.GREEN))
                        .append(Component.text("2FA verified! You are now authenticated.", NamedTextColor.GREEN))
                        .build());
                logger.info("2FA verification succeeded for {} ({})", player.getUsername(), uuid);
            }
            case IP_MISMATCH -> {
                player.sendMessage(Component.text()
                        .append(Component.text("✗ ", NamedTextColor.RED))
                        .append(Component.text("IP address changed! Please reconnect from your original network and try again.", NamedTextColor.RED))
                        .build());
                logger.warn("2FA IP lock mismatch for {} ({})", player.getUsername(), uuid);
            }
            case RATE_LIMITED -> {
                player.sendMessage(Component.text()
                        .append(Component.text("⏳ ", NamedTextColor.YELLOW))
                        .append(Component.text("Too many failed attempts. Please wait 15 minutes and try again.", NamedTextColor.YELLOW))
                        .build());
                logger.warn("2FA rate-limited for {} ({})", player.getUsername(), uuid);
            }
            default -> {
                player.sendMessage(Component.text()
                        .append(Component.text("✗ ", NamedTextColor.RED))
                        .append(Component.text("Invalid 2FA code. Please try again.", NamedTextColor.RED))
                        .build());
                logger.warn("2FA verification failed for {} ({})", player.getUsername(), uuid);
            }
        }
    }

    // ── Backup code verification (A-5) ──

    private void handleVerifyBackup(Invocation invocation, String[] args) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("Only players can verify backup codes.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /2fa verify-backup <code>", NamedTextColor.RED));
            return;
        }

        String backupCode = args[1];
        UUID uuid = player.getUniqueId();

        // Share rate limiter with TOTP verification (A-5 security)
        TotpManager.IpVerifyResult rateCheck = authManager.verifyTotpWithIpLock(uuid, 0,
                player.getRemoteAddress().getAddress().getHostAddress(), false);
        if (rateCheck == TotpManager.IpVerifyResult.RATE_LIMITED) {
            player.sendMessage(Component.text()
                    .append(Component.text("⏳ ", NamedTextColor.YELLOW))
                    .append(Component.text("Too many failed attempts. Please wait 15 minutes and try again.", NamedTextColor.YELLOW))
                    .build());
            return;
        }

        if (!authManager.isPlayerLocked(uuid)) {
            player.sendMessage(Component.text("You are not in a 2FA-locked state.", NamedTextColor.YELLOW));
            return;
        }

        if (authManager.verifyBackupCode(uuid, backupCode)) {
            authManager.unlockPlayer(uuid);
            onVerified.accept(player);
            player.sendMessage(Component.text()
                    .append(Component.text("✓ ", NamedTextColor.GREEN))
                    .append(Component.text("Backup code accepted! This code has been consumed.", NamedTextColor.GREEN))
                    .build());
            player.sendMessage(Component.text()
                    .append(Component.text("⚠ ", NamedTextColor.YELLOW))
                    .append(Component.text("Set up a new 2FA with /2fa setup to get fresh backup codes.", NamedTextColor.YELLOW))
                    .build());
            logger.info("Backup code used for 2FA by {} ({})", player.getUsername(), uuid);
        } else {
            player.sendMessage(Component.text()
                    .append(Component.text("✗ ", NamedTextColor.RED))
                    .append(Component.text("Invalid backup code.", NamedTextColor.RED))
                    .build());
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
        String executorName = invocation.source() instanceof Player p ? p.getUsername() : "console";

        // ── Full UUID resolution (A-3) ──
        Optional<UUID> targetUuid = authManager.resolveUsername(targetName, playerResolver);

        if (targetUuid.isPresent()) {
            UUID uuid = targetUuid.get();
            authManager.resetTotp(uuid);
            authManager.unlockPlayer(uuid); // also unlock if they were stuck
            invocation.source().sendMessage(Component.text()
                    .append(Component.text("✓ ", NamedTextColor.GREEN))
                    .append(Component.text("2FA has been reset for " + targetName + ".", NamedTextColor.GREEN))
                    .build());
            invocation.source().sendMessage(Component.text()
                    .append(Component.text("They will need to run /2fa setup on their next login.", NamedTextColor.GRAY))
                    .build());
            logger.info("2FA reset for {} ({}) by {}", targetName, uuid, executorName);
        } else {
            invocation.source().sendMessage(Component.text()
                    .append(Component.text("✗ ", NamedTextColor.RED))
                    .append(Component.text("Player '" + targetName + "' not found. They must have logged in at least once.", NamedTextColor.RED))
                    .build());
            logger.warn("2FA reset failed for {} — UUID not resolvable (by {})", targetName, executorName);
        }
    }
}
