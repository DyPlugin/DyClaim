package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportManager implements Listener {

    private final DyClaim plugin;
    private final Map<UUID, BukkitTask> pendingTeleports = new ConcurrentHashMap<>();

    public TeleportManager(DyClaim plugin) {
        this.plugin = plugin;
    }

    public void startTeleport(Player player, Location destination) {
        UUID uuid = player.getUniqueId();

        if (pendingTeleports.containsKey(uuid)) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "tp-already-pending"));
            return;
        }

        int warmup = plugin.getConfigManager().getTeleportWarmup();
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, warmup * 20 + 20, 0, false, false));
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "tp-warmup",
                Map.of("{seconds}", String.valueOf(warmup))));

        final Location startLoc = player.getLocation().clone();

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingTeleports.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.removePotionEffect(PotionEffectType.BLINDNESS);
                p.teleport(destination);
                p.sendMessage(plugin.getMessageManager().getPrefixed(p, "tp-success"));
            }
        }, warmup * 20L);

        pendingTeleports.put(uuid, task);
    }

    private void cancelTeleport(UUID uuid, boolean notify) {
        BukkitTask task = pendingTeleports.remove(uuid);
        if (task != null) {
            task.cancel();
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                if (notify) {
                    player.sendMessage(plugin.getMessageManager().getPrefixed(player, "tp-cancelled"));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null)
            return;
        UUID uuid = event.getPlayer().getUniqueId();
        if (!pendingTeleports.containsKey(uuid))
            return;

        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            cancelTeleport(uuid, true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelTeleport(event.getPlayer().getUniqueId(), false);
    }
}
