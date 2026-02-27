package dev.dyclaim.listener;

import dev.dyclaim.DyClaim;
import dev.dyclaim.manager.MessageManager;
import dev.dyclaim.model.ClaimData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;

public class PlayerListener implements Listener {

    private final DyClaim plugin;

    public PlayerListener(DyClaim plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null)
            return;

        int fromCX = event.getFrom().getBlockX() >> 4;
        int fromCZ = event.getFrom().getBlockZ() >> 4;
        int toCX = event.getTo().getBlockX() >> 4;
        int toCZ = event.getTo().getBlockZ() >> 4;

        if (fromCX == toCX && fromCZ == toCZ) {
            return;
        }

        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();

        if (plugin.getConfigManager().isWorldBlacklisted(worldName))
            return;

        ClaimData fromClaim = plugin.getClaimManager().getClaimAt(worldName, fromCX, fromCZ);
        ClaimData toClaim = plugin.getClaimManager().getClaimAt(worldName, toCX, toCZ);

        if (toClaim != null) {
            boolean shouldShow = false;

            if (fromClaim == null) {
                shouldShow = true;
            } else if (!fromClaim.getOwnerUUID().equals(toClaim.getOwnerUUID())) {
                shouldShow = true;
            } else if (fromClaim.isPvpDisabled() != toClaim.isPvpDisabled()) {
                shouldShow = true;
            }

            if (shouldShow && plugin.getConfigManager().isShowEnter()) {
                sendClaimEnterNotification(player, toClaim);
            }
        }

        if (fromClaim != null && toClaim == null && plugin.getConfigManager().isShowLeave()) {
            String leaveMsg = plugin.getMessageManager().getMessage(player, "actionbar-leave");
            if (plugin.getConfigManager().isUseActionbar()) {
                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                        MessageManager.colorize(leaveMsg)));
            } else {
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "protection-leave"));
            }
        }
    }

    private void sendClaimEnterNotification(Player player, ClaimData claim) {
        String lang = plugin.getMessageManager().getPreferredLanguage(player);
        boolean isTurkish = "tr".equals(lang);

        String pvpStatus = claim.isPvpDisabled()
                ? (isTurkish ? "\u00a74Kapal\u0131" : "\u00a74OFF")
                : (isTurkish ? "\u00a7aA\u00e7\u0131k" : "\u00a7aON");

        if (plugin.getConfigManager().isUseActionbar()) {
            String msg = plugin.getMessageManager().getMessage(player, "actionbar-enter",
                    Map.of("{owner}", claim.getOwnerName(), "{pvp}", pvpStatus));
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    MessageManager.colorize(msg)));
        } else {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "protection-enter",
                    Map.of("{owner}", claim.getOwnerName())));
        }
    }
}
