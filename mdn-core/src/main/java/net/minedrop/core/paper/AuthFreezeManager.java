package net.minedrop.core.paper;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the lobby authentication freeze system (spec §11-24).
 * <p>
 * When a player joins the lobby without being authenticated by Velocity/MDN-Auth,
 * they are:
 * <ul>
 *   <li>Teleported to the configured auth spawn location</li>
 *   <li>Frozen — position locked but yaw/pitch allowed (player can look around)</li>
 *   <li>Protected from damage, block interaction, inventory use, item pickup/drop</li>
 *   <li>Shown a BossBar and chat message with authentication instructions</li>
 *   <li>Subject to an auth timeout (default 120 seconds) — disconnected if not authenticated in time</li>
 *   <li>Only allowed to execute whitelisted commands (register, login, 2fa, help)</li>
 * </ul>
 * <p>
 * When AUTH_UPDATE(true) arrives from Velocity, the player is unfrozen and normal gameplay resumes.
 * <p>
 * <b>Fail-closed design (spec §63, §78):</b> unknown/error state = frozen. A player is only
 * unfrozen when MDN-Auth explicitly confirms authentication via AUTH_UPDATE(true).
 */
public final class AuthFreezeManager implements Listener {

    private final Plugin plugin;
    private final Logger logger;
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();

    // ── Per-player BossBars (cleaned up on unfreeze/disconnect) ──
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    // ── Per-player join timestamps for timeout tracking ──
    private final Map<UUID, Long> joinTimestamps = new ConcurrentHashMap<>();

    // ── Configuration ──
    private boolean enabled = true;
    private boolean allowLook = true;
    private Location spawnLocation;
    private int timeoutSeconds = 120;
    private Set<String> allowedCommands = new HashSet<>(Arrays.asList(
            "register", "login", "2fa", "password", "help"
    ));

    // Use neutral message that suggests both options since lobby doesn't know registration state
    private String registeredMessage =
            "§ePlease authenticate to play!\n§a/login <password> §7or §a/register <password>";
    // ── 2FA required hint ──
    private String totpMessage =
            "§ePassword accepted. Two-factor authentication is required:\n§a/2fa verify <code>";

    // ── Title display ──
    private boolean showTitle = true;

    // ── Warning frequency (anti-spam, spec §73) ──
    private final Map<UUID, Long> lastWarning = new ConcurrentHashMap<>();
    private static final long WARNING_COOLDOWN_MS = 5_000;

    public AuthFreezeManager(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    // ── Configuration ──

    /**
     * Loads freeze configuration from the plugin config.yml.
     * Call after saveDefaultConfig() / reloadConfig().
     */
    public void loadConfig(ConfigurationSection config) {
        if (config == null) {
            logger.warning("No 'authentication' config section found — freeze system DISABLED");
            enabled = false;
            return;
        }

        enabled = config.getBoolean("enabled", true);
        if (!enabled) {
            logger.info("Auth freeze system is DISABLED in config");
            return;
        }

        // ── Spawn location ──
        ConfigurationSection spawnSec = config.getConfigurationSection("spawn");
        if (spawnSec != null) {
            String worldName = spawnSec.getString("world", "world");
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                logger.warning("Auth spawn world '" + worldName + "' not found! Freeze will use player's join location.");
                spawnLocation = null;
            } else {
                double x = spawnSec.getDouble("x", 0.5);
                double y = spawnSec.getDouble("y", 100.0);
                double z = spawnSec.getDouble("z", 0.5);
                float yaw = (float) spawnSec.getDouble("yaw", 180.0);
                float pitch = (float) spawnSec.getDouble("pitch", 0.0);
                spawnLocation = new Location(world, x, y, z, yaw, pitch);
                logger.info("Auth spawn: " + worldName + " @ " + x + "," + y + "," + z);
            }
        } else {
            spawnLocation = null;
        }

        // ── Freeze options ──
        ConfigurationSection freezeSec = config.getConfigurationSection("freeze");
        if (freezeSec != null) {
            allowLook = freezeSec.getBoolean("allow-look", true);
        }

        // ── Timeout ──
        timeoutSeconds = config.getInt("timeout-seconds", 120);
        if (timeoutSeconds < 10) timeoutSeconds = 10; // minimum sanity

        // ── Allowed commands ──
        List<String> cmds = config.getStringList("allowed-commands");
        if (!cmds.isEmpty()) {
            allowedCommands = new HashSet<>(cmds);
        }

        // ── Messages ──
        ConfigurationSection msgSec = config.getConfigurationSection("messages");
        if (msgSec != null) {
            registeredMessage = msgSec.getString("registered",
                    "§ePlease authenticate to play!\n§a/login <password> §7or §a/register <password>");
            totpMessage = msgSec.getString("totp-required",
                    "§ePassword accepted. Two-factor authentication is required:\n§a/2fa verify <code>");
        }

        // ── UX ──
        showTitle = config.getBoolean("show-title", true);

        // ── Start timeout checker ──
        startTimeoutChecker();

        logger.info("Auth freeze system loaded: timeout=" + timeoutSeconds + "s, allow-look=" + allowLook
                + ", spawn=" + (spawnLocation != null ? "configured" : "dynamic") + ", allowed-commands=" + allowedCommands);
    }

    /**
     * Freezes a player using a neutral message (both /login and /register options).
     * Use when the lobby doesn't know if the account is registered.
     */
    public void freeze(Player player) {
        freeze(player, false); // Uses neutral message below
    }

    /**
     * Freezes a player — teleport to spawn, lock movement (allow look),
     * display BossBar + messages, start timeout.
     */
    public void freeze(Player player, boolean isRegistered) {
        if (!enabled) return;
        UUID uuid = player.getUniqueId();

        // Don't double-freeze (prevents BossBar leak on race with auto-freeze)
        if (frozenPlayers.contains(uuid)) return;

        frozenPlayers.add(uuid);
        joinTimestamps.put(uuid, System.currentTimeMillis());

        // ── Teleport to auth spawn ──
        if (spawnLocation != null) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.teleportAsync(spawnLocation));
        }

        // ── BossBar ──
        BossBar bar = BossBar.bossBar(
                Component.text("⏳ Authentication Required — ", NamedTextColor.YELLOW)
                        .append(Component.text(isRegistered ? "/login" : "/register", NamedTextColor.GREEN)),
                0.0f,
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS
        );
        player.showBossBar(bar);
        bossBars.put(uuid, bar);

        // ── Title ──
        if (showTitle) {
            player.showTitle(Title.title(
                    Component.text("Authentication Required", NamedTextColor.YELLOW, TextDecoration.BOLD),
                    Component.text(isRegistered ? "Use /login <password>" : "Use /register <password>",
                            NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofMillis(500))
            ));
        }

        // ── Chat message ──
        String msg = isRegistered ? registeredMessage : registeredMessage; // always neutral
        for (String line : msg.split("\n")) {
            player.sendMessage(line);
        }

        logger.info("[freeze] " + player.getName() + " (" + uuid + ") frozen — "
                + (isRegistered ? "registered, waiting for /login" : "new, waiting for /register"));
    }

    /**
     * Unfreezes a player — remove all restrictions, clear BossBar, allow gameplay.
     */
    public void unfreeze(Player player) {
        UUID uuid = player.getUniqueId();

        if (!frozenPlayers.remove(uuid)) return; // was not frozen

        joinTimestamps.remove(uuid);

        // ── Clear BossBar ──
        BossBar bar = bossBars.remove(uuid);
        if (bar != null) {
            player.hideBossBar(bar);
        }

        // ── Success message ──
        player.sendMessage("§a✓ Authentication successful! Welcome to MineDrop Network.");
        player.showTitle(Title.title(
                Component.text("✓ Authenticated!", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("Welcome to MineDrop Network", NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(300))
        ));

        logger.info("[unfreeze] " + player.getName() + " (" + uuid + ") — authenticated, unfrozen");
    }

    /** Called when a player disconnects — cleanup all state. */
    public void onDisconnect(UUID uuid) {
        frozenPlayers.remove(uuid);
        joinTimestamps.remove(uuid);
        BossBar bar = bossBars.remove(uuid);
        // BossBar is auto-cleaned when player disconnects, but explicit cleanup is safe
        lastWarning.remove(uuid);
    }

    public boolean isFrozen(UUID uuid) {
        return enabled && frozenPlayers.contains(uuid);
    }

    public int getFrozenCount() {
        return frozenPlayers.size();
    }

    // ── Timeout Checker ──

    private void startTimeoutChecker() {
        long checkInterval = Math.max(20L, timeoutSeconds * 20L / 4); // check 4 times during timeout
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long timeoutMs = timeoutSeconds * 1000L;

                for (var entry : new ArrayList<>(joinTimestamps.entrySet())) {
                    UUID uuid = entry.getKey();
                    long joinedAt = entry.getValue();

                    if (now - joinedAt > timeoutMs) {
                        Player player = plugin.getServer().getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            player.kick(Component.text(
                                    "§cAuthentication timed out.\n§7You have " + timeoutSeconds
                                            + " seconds to authenticate.\n§7Please reconnect and try again."));
                            logger.info("[timeout] " + player.getName() + " (" + uuid
                                    + ") kicked — auth timeout after " + timeoutSeconds + "s");
                        }
                        joinTimestamps.remove(uuid);
                        frozenPlayers.remove(uuid);
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, checkInterval, checkInterval);
    }

    // ── Event Handlers ──

    /**
     * Player join: immediately freeze unless already authenticated by Velocity.
     * The freeze decision is made in CorePaperPlugin's AUTH_UPDATE subscriber —
     * if the player is in authenticatedPlayers, they won't be frozen.
     * This handler fires after the subscriber processes the initial state.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // If already authenticated (AUTH_UPDATE arrived before join?), skip freeze
        // CorePaperPlugin will call freeze() explicitly based on AUTH_UPDATE state
        // This is just a safety net — freeze after a short delay to allow AUTH_UPDATE to arrive
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !frozenPlayers.contains(uuid)) {
                // Player is online and not in frozen set and not in authenticated set
                // Default to frozen (fail-closed, spec §63)
                // CorePaperPlugin maintains authenticatedPlayers — check via it
                var corePlugin = (CorePaperPlugin) plugin.getServer().getPluginManager().getPlugin("MDN-Core");
                if (corePlugin != null && corePlugin.isEnabled() && !corePlugin.isAuthenticated(uuid)) {
                    freeze(player, false); // default to "new player" message
                    logger.info("[auto-freeze] " + player.getName() + " — not yet authenticated, freezing");
                }
            }
        }, 5L); // 5 ticks (250ms) delay for AUTH_UPDATE to arrive
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isFrozen(event.getPlayer().getUniqueId())) return;

        // Only intervene if the player actually changed position (spec §13)
        if (!event.hasChangedPosition()) return;

        if (allowLook) {
            // Allow yaw/pitch changes but lock X/Y/Z to current location (spec §12-13)
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) {
                event.setCancelled(true);
                return;
            }
            // Preserve X/Y/Z from original position, allow yaw/pitch from destination
            to.setX(from.getX());
            to.setY(from.getY());
            to.setZ(from.getZ());
            // Don't cancel — let the modified destination through
            // Note: this approach works with Paper's PlayerMoveEvent
            // If the server uses an older implementation, fall back to cancel
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!isFrozen(event.getPlayer().getUniqueId())) return;
        // Only allow teleport to the auth spawn location (lenient check, spec §87)
        if (spawnLocation != null) {
            Location to = event.getTo();
            if (!spawnLocation.getWorld().equals(to.getWorld())
                    || to.distanceSquared(spawnLocation) > 0.01) {
                event.setCancelled(true);
            }
        }
    }

    // ── Damage Protection (spec §15) ──

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Block frozen players from dealing damage too
        if (event.getDamager() instanceof Player player && isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
        // Block damage to frozen players (handled above, but double-check)
        if (event.getEntity() instanceof Player player && isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ── Block Protection (spec §16) ──

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            sendWarning(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            sendWarning(event.getPlayer());
        }
    }

    // ── Interaction Protection (spec §17) ──

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            sendWarning(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ── Inventory Protection (spec §18) ──

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
            sendWarning(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ── Item Pickup/Drop/Consume (spec §19) ──

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ── Command Blocking (spec §21-22) ──

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!isFrozen(event.getPlayer().getUniqueId())) return;

        String raw = event.getMessage();
        if (raw.startsWith("/")) {
            // Extract the first word (command name)
            String cmd = raw.substring(1).split("\\s+")[0].toLowerCase();

            // Allow authentication commands
            if (allowedCommands.contains(cmd)) return;

            // Block everything else
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c⚠ You must authenticate first! Use "
                    + (allowedCommands.contains("register") ? "/register or /login" : "/login"));
        }
    }

    // ── Helpers ──

    private void sendWarning(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - lastWarning.getOrDefault(uuid, 0L) > WARNING_COOLDOWN_MS) {
            player.sendMessage("§c⚠ You must authenticate before interacting with the world.");
            lastWarning.put(uuid, now);
        }
    }

    // ── Public API ──

    public boolean isEnabled() { return enabled; }
    public Set<UUID> getFrozenPlayers() { return Collections.unmodifiableSet(frozenPlayers); }
    public Location getSpawnLocation() { return spawnLocation; }
}
