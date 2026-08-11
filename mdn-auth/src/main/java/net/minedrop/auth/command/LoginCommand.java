package net.minedrop.auth.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minedrop.auth.AuthManager;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Handles /login command — password-based authentication.
 * <p>
 * Flow:
 * <ol>
 *   <li>Verify password via Argon2id</li>
 *   <li>Check account status (ACTIVE/SUSPENDED)</li>
 *   <li>If 2FA enabled → transition to TOTP_REQUIRED state</li>
 *   <li>If no 2FA → create session, publish AUTH_UPDATE, route to lobby</li>
 * </ol>
 * <p>
 * Rate limiting: 5 failed attempts per 5 minutes per UUID+IP.
 */
public final class LoginCommand implements SimpleCommand {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int FAILED_TTL_SECONDS = 300; // 5 minutes

    private final AuthManager authManager;
    private final Logger logger;
    private final java.util.function.Consumer<Player> onAuthenticated;
    private final java.util.function.Consumer<Player> onPasswordVerified;
    private final java.util.function.Predicate<Player> isForce2fa;

    public LoginCommand(AuthManager authManager, Logger logger,
                        java.util.function.Consumer<Player> onAuthenticated,
                        java.util.function.Consumer<Player> onPasswordVerified,
                        java.util.function.Predicate<Player> isForce2fa) {
        this.authManager = authManager;
        this.logger = logger;
        this.onAuthenticated = onAuthenticated;
        this.onPasswordVerified = onPasswordVerified;
        this.isForce2fa = isForce2fa;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("Only players can log in.", NamedTextColor.RED));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /login <password>", NamedTextColor.RED));
            return;
        }

        String password = args[0];
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        // Check if already authenticated
        if (authManager.hasActiveSession(uuid)) {
            player.sendMessage(Component.text()
                    .append(Component.text("✓ ", NamedTextColor.GREEN))
                    .append(Component.text("You are already authenticated!", NamedTextColor.GREEN))
                    .build());
            return;
        }

        // Check if account is registered
        if (!authManager.isRegistered(uuid)) {
            player.sendMessage(Component.text()
                    .append(Component.text("This account is not registered. Use ", NamedTextColor.YELLOW))
                    .append(Component.text("/register", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" to create an account.", NamedTextColor.YELLOW))
                    .build());
            return;
        }

        // ── Rate limiting ──
        if (authManager.isLoginRateLimited(uuid, ip)) {
            player.disconnect(Component.text()
                    .append(Component.text("⏳ Too many login attempts", NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text("Please wait 5 minutes before trying again.", NamedTextColor.GRAY))
                    .build());
            logger.warn("Login rate-limited for {} ({}) from {}", username, uuid, ip);
            return;
        }

        // ── Verify password ──
        AuthManager.LoginResult result = authManager.verifyPassword(
                uuid, password.toCharArray(), ip);

        switch (result) {
            case SUCCESS -> {
                // Check if 2FA is required
                if (authManager.isTotpRequired(uuid, () -> isForce2fa.test(player))) {
                    // Password OK, now require 2FA
                    authManager.lockPlayer(uuid);
                    onPasswordVerified.accept(player);
                    logger.info("Password verified for {} — 2FA now required", username);
                } else {
                    // Full authentication — no 2FA needed
                    authManager.createAuthenticatedSession(uuid, ip);
                    onAuthenticated.accept(player);
                    logger.info("Login successful for {} ({}) — no 2FA", username, uuid);
                }
            }
            case INVALID_CREDENTIALS -> {
                authManager.recordFailedLogin(uuid, ip);
                player.sendMessage(Component.text("✗ Invalid credentials.", NamedTextColor.RED));
                logger.info("Login failed for {} ({}) — wrong password", username, uuid);
            }
            case ACCOUNT_SUSPENDED -> {
                player.disconnect(Component.text()
                        .append(Component.text("⛔ Account Suspended", NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.newline())
                        .append(Component.text("Your account has been suspended. Contact staff for assistance.", NamedTextColor.GRAY))
                        .build());
                logger.warn("Suspended account {} ({}) attempted login", username, uuid);
            }
            case RATE_LIMITED -> {
                player.sendMessage(Component.text()
                        .append(Component.text("⏳ ", NamedTextColor.YELLOW))
                        .append(Component.text("Too many attempts. Please wait 5 minutes.", NamedTextColor.YELLOW))
                        .build());
            }
            case DATABASE_ERROR -> {
                player.sendMessage(Component.text()
                        .append(Component.text("✗ ", NamedTextColor.RED))
                        .append(Component.text("Authentication service temporarily unavailable.", NamedTextColor.RED))
                        .build());
            }
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        // Anyone can attempt login (no permission required)
        return true;
    }
}
