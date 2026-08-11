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
 * Handles /register command — creates a new MDN account with password.
 * <p>
 * Password requirements:
 * <ul>
 *   <li>Minimum 12 characters</li>
 *   <li>Maximum 128 characters</li>
 *   <li>Cannot contain the username</li>
 *   <li>Cannot be a common weak password</li>
 * </ul>
 * <p>
 * On success, the player is automatically authenticated.
 */
public final class RegisterCommand implements SimpleCommand {

    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final AuthManager authManager;
    private final Logger logger;
    private final java.util.function.Consumer<Player> onAuthenticated;

    public RegisterCommand(AuthManager authManager, Logger logger,
                           java.util.function.Consumer<Player> onAuthenticated) {
        this.authManager = authManager;
        this.logger = logger;
        this.onAuthenticated = onAuthenticated;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("Only players can register.", NamedTextColor.RED));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length < 1) {
            showHelp(player);
            return;
        }

        String password = args[0];
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        // Check if already registered
        if (authManager.isRegistered(uuid)) {
            player.sendMessage(Component.text()
                    .append(Component.text("⚠ ", NamedTextColor.YELLOW))
                    .append(Component.text("This account is already registered. Use ", NamedTextColor.WHITE))
                    .append(Component.text("/login <password>", NamedTextColor.GOLD))
                    .append(Component.text(" to authenticate.", NamedTextColor.WHITE))
                    .build());
            return;
        }

        // Validate password
        String validationError = validatePassword(password, username);
        if (validationError != null) {
            player.sendMessage(Component.text("✗ " + validationError, NamedTextColor.RED));
            return;
        }

        // Register
        AuthManager.RegistrationResult result = authManager.register(
                uuid, username, password.toCharArray(), ip);

        switch (result) {
            case SUCCESS -> {
                player.sendMessage(Component.text());
                player.sendMessage(Component.text()
                        .append(Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD))
                        .append(Component.text("Account created successfully!", NamedTextColor.GREEN))
                        .build());
                player.sendMessage(Component.text()
                        .append(Component.text("Welcome to MineDrop Network, " + username + "!", NamedTextColor.GOLD))
                        .build());
                player.sendMessage(Component.text()
                        .append(Component.text("We recommend setting up 2FA with ", NamedTextColor.GRAY))
                        .append(Component.text("/2fa setup", NamedTextColor.YELLOW))
                        .append(Component.text(" for extra security.", NamedTextColor.GRAY))
                        .build());
                player.sendMessage(Component.text());

                // Auto-authenticate after registration
                onAuthenticated.accept(player);
                logger.info("New account registered: {} ({}) from {}", username, uuid, ip);
            }
            case ALREADY_REGISTERED -> {
                player.sendMessage(Component.text("This account is already registered. Use /login.", NamedTextColor.YELLOW));
            }
            case DATABASE_ERROR -> {
                player.sendMessage(Component.text()
                        .append(Component.text("✗ ", NamedTextColor.RED))
                        .append(Component.text("Registration service temporarily unavailable. Please try again.", NamedTextColor.RED))
                        .build());
            }
            case ACCOUNT_SUSPENDED -> {
                player.sendMessage(Component.text("This account has been suspended.", NamedTextColor.RED));
            }
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        // Anyone can register (no permission required)
        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text());
        player.sendMessage(Component.text()
                .append(Component.text("══════ MineDrop Registration ══════", NamedTextColor.GOLD, TextDecoration.BOLD))
                .build());
        player.sendMessage(Component.text());
        player.sendMessage(Component.text()
                .append(Component.text("Welcome! This Minecraft account is not registered.", NamedTextColor.YELLOW))
                .build());
        player.sendMessage(Component.text()
                .append(Component.text("Create your MineDrop password with:", NamedTextColor.GRAY))
                .build());
        player.sendMessage(Component.text());
        player.sendMessage(Component.text()
                .append(Component.text("  /register <password>", NamedTextColor.GOLD, TextDecoration.BOLD))
                .build());
        player.sendMessage(Component.text());
        player.sendMessage(Component.text()
                .append(Component.text("Requirements: ", NamedTextColor.GRAY))
                .append(Component.text("12+ characters, not your username", NamedTextColor.WHITE))
                .build());
        player.sendMessage(Component.text()
                .append(Component.text("⚠ ", NamedTextColor.RED))
                .append(Component.text("Your password protects your account. Do not share it!", NamedTextColor.RED))
                .build());
        player.sendMessage(Component.text());
    }

    /**
     * Validates password strength. Returns error message or null if valid.
     */
    private String validatePassword(String password, String username) {
        if (password == null || password.isEmpty()) {
            return "Password cannot be empty.";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.";
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            return "Password must be at most " + MAX_PASSWORD_LENGTH + " characters.";
        }
        if (password.equalsIgnoreCase(username)) {
            return "Password cannot be the same as your username.";
        }
        // Check for common weak passwords
        if (isCommonPassword(password)) {
            return "This password is too common. Please choose a stronger one.";
        }
        return null;
    }

    private boolean isCommonPassword(String password) {
        String lower = password.toLowerCase();
        return lower.equals("password") || lower.equals("123456789012")
                || lower.equals("qwertyuiopas") || lower.equals("minecraft123")
                || lower.equals("minedrop123") || lower.equals("aaaaaaaaaaaa");
    }
}
