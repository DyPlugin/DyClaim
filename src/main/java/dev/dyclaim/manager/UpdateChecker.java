package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import dev.dyclaim.util.UpdateFeed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class UpdateChecker implements Listener {
    private final DyClaim plugin;
    private volatile UpdateFeed.Release latest;
    private final AtomicBoolean checking = new AtomicBoolean();
    private final Map<UUID, String> notified = new HashMap<>();
    private BukkitTask nextCheck;
    private long generation;
    private boolean closed;
    public UpdateChecker(DyClaim plugin) { this.plugin = plugin; }

    /** Called on the server thread; HTTP and parsing run asynchronously. */
    public void check() {
        if (closed || !plugin.getConfigManager().values().getBoolean("update-checker.enabled", true)
                || !checking.compareAndSet(false, true)) return;
        if (nextCheck != null) { nextCheck.cancel(); nextCheck = null; }
        long requestedGeneration = generation;
        String installed = plugin.getDescription().getVersion();
        String bukkitVersion = plugin.getServer().getBukkitVersion();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UpdateFeed.Release result = null;
            String failure = null;
            try {
                String game = UpdateFeed.gameVersion(bukkitVersion);
                result = UpdateFeed.select(UpdateFeed.fetch(UpdateFeed.requestUrl(game), installed), game, installed);
            } catch (Exception ex) { failure = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(); }
            UpdateFeed.Release found = result;
            String error = failure;
            if (!plugin.isEnabled()) { checking.set(false); return; }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                checking.set(false);
                if (closed) return;
                if (requestedGeneration != generation) { check(); return; }
                if (error == null) {
                    boolean changed = !Objects.equals(latest, found);
                    latest = found;
                    if (changed) notified.clear();
                    if (found != null) {
                        if (changed) {
                            plugin.getLogger().info("New version available: " + displayVersion(found.version()));
                            plugin.getLogger().info("Download: " + found.url());
                        }
                        plugin.getServer().getOnlinePlayers().forEach(this::notifyAdmin);
                    }
                } else plugin.getLogger().warning("Update check failed; will retry: " + error);
                long minutes = error == null
                        ? plugin.getConfigManager().values().getLong("update-checker.check-interval-hours", 6) * 60
                        : plugin.getConfigManager().values().getLong("update-checker.retry-minutes", 10);
                nextCheck = plugin.getServer().getScheduler().runTaskLater(plugin, this::check, Math.max(1, minutes) * 1200);
            });
        });
    }
    public void reload() {
        generation++;
        latest = null;
        notified.clear();
        if (nextCheck != null) { nextCheck.cancel(); nextCheck = null; }
        check();
    }
    public void close() {
        closed = true;
        generation++;
        latest = null;
        notified.clear();
        if (nextCheck != null) nextCheck.cancel();
    }
    @EventHandler public void onAdminJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("dyclaim.admin")) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> notifyAdmin(player), 60L);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { notified.remove(event.getPlayer().getUniqueId()); }
    private void notifyAdmin(Player player) {
        UpdateFeed.Release release = latest;
        if (closed || release == null || !player.isOnline() || !player.hasPermission("dyclaim.admin")
                || release.version().equals(notified.get(player.getUniqueId()))) return;
        notified.put(player.getUniqueId(), release.version());
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "update-available",
                Map.of("{version}", displayVersion(release.version()))));
        player.sendMessage(plugin.getMessageManager().getMessage(player, "update-download", Map.of("{link}", release.url())));
    }
    public static String displayVersion(String version) { return "DyClaim-v" + version.replaceFirst("(?i)^v", ""); }
    public boolean isUpdateAvailable() { return latest != null; }
    public String getLatestVersion() { return latest == null ? null : latest.version(); }
    public String getDownloadUrl() { return latest == null ? null : latest.url(); }
}
