package net.minedrop.auth.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minedrop.auth.AuthManager;
import net.minedrop.auth.TotpManager;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Handles /password change and /password reset commands (spec §23-27).
 * <p>
 * /password change <current> <new> — requires current password, session remains valid
 * /password reset <totp|backup> <code> <new> — requires TOTP or backup code, all sessions revoked
 */
public final class PasswordCommand implements SimpleCommand {

    private final AuthManager authManager;
    private final Logger logger;
    private final boolean enforceIpLock;
    private final Runnable onPasswordChanged;

    public PasswordCommand(AuthManager authManager, Logger logger,
                           boolean enforceIpLock, Runnable onPasswordChanged) {
        this.authManager = authManager;
        this.logger = logger;
        this.enforceIpLock = enforceIpLock;
        this.onPasswordChanged = onPasswordChanged;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("Only players can change passwords.", NamedTextColor.RED));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            showHelp(player);
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "change" -> handleChange(player, args);
            case "reset" -> handleReset(player, args);
            default -> player.sendMessage(Component.text("Usage: /password <change|reset>", NamedTextColor.RED));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true; // any authenticated player
    }

    // ── /password change <current> <new> ──

    private void handleChange(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /password change <current_password> <new_password>", NamedTextColor.RED));
            return;
        }

        UUID uuid = player.getUniqueId();
        String currentPassword = args[1];
        String newPassword = args[2];
        String username = player.getUsername();

        if (newPassword.length() < 12) {
            player.sendMessage(Component.text("✗ New password must be at least 12 characters.", NamedTextColor.RED));
            return;
        }
        if (newPassword.equalsIgnoreCase(username)) {
            player.sendMessage(Component.text("✗ Password cannot be your username.", NamedTextColor.RED));
            return;
        }

        boolean success = authManager.changePassword(
                uuid,
                currentPassword.toCharArray(),
                newPassword.toCharArray()
        );

        if (success) {
            player.sendMessage(Component.text()
                    .append(Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text("Password changed successfully!", NamedTextColor.GREEN))
                    .build());
            player.sendMessage(Component.text()
                    .append(Component.text("All other sessions have been revoked. You'll need to re-authenticate.", NamedTextColor.GRAY))
                    .build());
            onPasswordChanged.run();
            logger.info("Password changed for {} ({})", username, uuid);
        } else {
            player.sendMessage(Component.text("✗ Current password is incorrect.", NamedTextColor.RED));
        }
    }

    // ── /password reset <totp|backup> <code> <new_password> ──

    private void handleReset(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("Usage: /password reset <totp|backup> <code> <new_password>", NamedTextColor.RED));
            return;
        }

        String method = args[1].toLowerCase();
        String code = args[2];
        String newPassword = args[3];
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        if (newPassword.length() < 12) {
            player.sendMessage(Component.text("✗ New password must be at least 12 characters.", NamedTextColor.RED));
            return;
        }

        boolean verified = false;

        if ("recovery".equals(method)) {
            // Admin recovery token
            String token = code;
            boolean success = authManager.validateRecoveryToken(uuid, token, newPassword.toCharArray());
            if (success) {
                authManager.revokeAllSessions(uuid, "Password reset via admin recovery token");
                player.sendMessage(Component.text()
                        .append(Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD))
                        .append(Component.text("Password has been reset!", NamedTextColor.GREEN))
                        .build());
                player.sendMessage(Component.text()
                        .append(Component.text("All sessions revoked. TOTP has been cleared — set up new 2FA with ", NamedTextColor.GRAY))
                        .append(Component.text("/2fa setup", NamedTextColor.GOLD))
                        .build());
                onPasswordChanged.run();
                logger.info("Password reset for {} ({}) via admin recovery token", username, uuid);
            } else {
                player.sendMessage(Component.text("✗ Invalid or expired recovery token.", NamedTextColor.RED));
            }
            return;
        }

        if ("totp".equals(method)) {
            // Verify TOTP code
            int totpCode;
            try {
                totpCode = Integer.parseInt(code);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("✗ Invalid TOTP code format — must be 6 digits.", NamedTextColor.RED));
                return;
            }
            TotpManager.IpVerifyResult result = authManager.verifyTotpWithIpLock(
                    uuid, totpCode, ip, enforceIpLock);
            verified = (result == TotpManager.IpVerifyResult.SUCCESS);
            if (!verified) {
                player.sendMessage(Component.text("✗ Invalid TOTP code.", NamedTextColor.RED));
            }
        } else if ("backup".equals(method)) {
            // Verify backup code
            verified = authManager.verifyBackupCode(uuid, code);
            if (!verified) {
                player.sendMessage(Component.text("✗ Invalid or already-used backup code.", NamedTextColor.RED));
            }
        } else {
            player.sendMessage(Component.text("Usage: /password reset <totp|backup> <code> <new_password>", NamedTextColor.RED));
            return;
        }

        if (verified) {
            // Force password change without current password (recovery mode)
            boolean success = forceResetPassword(uuid, newPassword.toCharArray());
            if (success) {
                authManager.revokeAllSessions(uuid, "Password reset via " + method + " verification");
                player.sendMessage(Component.text()
                        .append(Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD))
                        .append(Component.text("Password has been reset!", NamedTextColor.GREEN))
                        .build());
                player.sendMessage(Component.text()
                        .append(Component.text("All sessions revoked. Please ", NamedTextColor.GRAY))
                        .append(Component.text("/login", NamedTextColor.GOLD))
                        .append(Component.text(" with your new password.", NamedTextColor.GRAY))
                        .build());
                if ("backup".equals(method)) {
                    player.sendMessage(Component.text()
                            .append(Component.text("⚠ ", NamedTextColor.YELLOW))
                            .append(Component.text("Set up new 2FA with /2fa setup to get fresh backup codes.", NamedTextColor.YELLOW))
                            .build());
                }
                onPasswordChanged.run();
                logger.info("Password reset for {} ({}) via {}", username, uuid, method);
            } else {
                player.sendMessage(Component.text("✗ Failed to reset password. Contact an admin.", NamedTextColor.RED));
            }
        }
    }

    /**
     * Resets a password WITHOUT requiring the current password (recovery flow).
     */
    private boolean forceResetPassword(UUID uuid, char[] newPassword) {
        // Reuse the hasher to create new hash, then update DB directly
        String newHash = authManager.hashPassword(newPassword);
        if (newHash == null) return false;

        return authManager.updatePasswordHash(uuid, newHash);
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text()
                .append(Component.text("Password Commands:", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("  /password change <current> <new>", NamedTextColor.GRAY))
                .append(Component.text(" — Change your password", NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("  /password reset totp <code> <new>", NamedTextColor.GRAY))
                .append(Component.text(" — Reset using TOTP", NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("  /password reset backup <code> <new>", NamedTextColor.GRAY))
                .append(Component.text(" — Reset using backup code", NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("  /password reset recovery <token> <new>", NamedTextColor.GRAY))
                .append(Component.text(" — Reset using admin recovery token", NamedTextColor.WHITE))
                .build());
    }
}
