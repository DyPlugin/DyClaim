package dev.dyclaim.listener;

import dev.dyclaim.DyClaim;
import dev.dyclaim.manager.MessageManager;
import dev.dyclaim.model.ClaimData;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
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
        if(plugin.getPlatformTasks().isRegionThreaded())for(Player player:org.bukkit.Bukkit.getOnlinePlayers())plugin.getPlatformTasks().playerLater(player,()->plugin.getAccessManager().watchActor(player),1);
    }
    @EventHandler public void onJoin(org.bukkit.event.player.PlayerJoinEvent event){plugin.getAccessManager().watchActor(event.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onActorTeleport(org.bukkit.event.player.PlayerTeleportEvent event){if(event.getTo()!=null)plugin.getAccessManager().recordActor(event.getPlayer(),event.getTo());}

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null)
            return;

        int fromCX = event.getFrom().getBlockX() >> 4;
        int fromCZ = event.getFrom().getBlockZ() >> 4;
        int toCX = event.getTo().getBlockX() >> 4;
        int toCZ = event.getTo().getBlockZ() >> 4;

        if (fromCX == toCX && fromCZ == toCZ) {
            // The published chunk is unchanged; the per-tick actor timer still refreshes permissions.
            return;
        }
        if(plugin.getPlatformTasks().isRegionThreaded())plugin.getAccessManager().recordActor(event.getPlayer(),event.getTo());

        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();

        if (plugin.getConfigManager().isWorldBlacklisted(worldName))
            return;

        ClaimData fromClaim = plugin.getClaimManager().getClaimAt(worldName, fromCX, fromCZ);
        ClaimData toClaim = plugin.getClaimManager().getClaimAt(worldName, toCX, toCZ);

        boolean notices = !plugin.getFeatureManager().noticesHidden(player.getUniqueId());
        if (toClaim != null) {
            boolean shouldShow = false;

            if (fromClaim == null) {
                shouldShow = true;
            } else if (!fromClaim.getOwnerUUID().equals(toClaim.getOwnerUUID())) {
                shouldShow = true;
            } else if (plugin.getAccessManager().enabled(fromClaim,"pvp") != plugin.getAccessManager().enabled(toClaim,"pvp")) {
                shouldShow = true;
            } else if (showNames() && !java.util.Objects.equals(fromClaim.getName(), toClaim.getName())) {
                // Two named areas of the same owner: crossing between them is worth a notice too.
                shouldShow = true;
            }

            if (shouldShow && notices && plugin.getConfigManager().isShowEnter()) {
                sendClaimEnterNotification(player, toClaim);
            }
            if ((fromClaim == null || !fromClaim.getOwnerUUID().equals(toClaim.getOwnerUUID())) && !toClaim.canManage(player.getUniqueId())) {
                sendGreeting(player, toClaim);
            }
        }

        if (fromClaim != null && toClaim == null && notices && plugin.getConfigManager().isShowLeave()) {
            String leaveMsg = plugin.getMessageManager().getMessage(player, "actionbar-leave");
            if (useTitle()) {
                player.sendTitle("", leaveMsg, 5, 30, 10);
            } else if (plugin.getConfigManager().isUseActionbar()) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(leaveMsg));
            } else {
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "protection-leave"));
            }
        }
    }

    /** Owner-written text; colour codes are removed unless the server allows them. */
    private void sendGreeting(Player player, ClaimData claim) {
        var settings = plugin.getAccessManager().settings();
        String greeting = claim.getGreeting();
        if (greeting == null || !settings.claimMessages) return;
        // With colours allowed the owner's codes and tags are applied first; otherwise they are shown as plain text.
        greeting = settings.messageColors ? MessageManager.colorize(greeting) : greeting.replace("&", "");
        player.sendMessage(plugin.getMessageManager().getMessage(player, "claim-greeting",
                Map.of("{owner}", claim.getOwnerName(), "{message}", greeting)));
    }

    private void sendClaimEnterNotification(Player player, ClaimData claim) {
        String lang = plugin.getMessageManager().getPreferredLanguage(player);
        boolean isTurkish = "tr".equals(lang);

        String pvpStatus = !plugin.getAccessManager().enabled(claim,"pvp")
                ? (isTurkish ? "\u00a7aKapal\u0131" : "\u00a7aOFF")
                : (isTurkish ? "\u00a7cA\u00e7\u0131k" : "\u00a7cON");

        String name = showNames() ? claim.getName() : null;
        Map<String, String> values = name == null ? Map.of("{owner}", claim.getOwnerName(), "{pvp}", pvpStatus)
                : Map.of("{owner}", claim.getOwnerName(), "{pvp}", pvpStatus, "{name}", name);
        if (useTitle()) {
            player.sendTitle(plugin.getMessageManager().getMessage(player, name == null ? "title-enter" : "title-enter-named", values),
                    plugin.getMessageManager().getMessage(player, "title-enter-subtitle", values), 5, 30, 10);
        } else if (plugin.getConfigManager().isUseActionbar()) {
            String msg = plugin.getMessageManager().getMessage(player, name == null ? "actionbar-enter" : "actionbar-enter-named", values);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
        } else {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, name == null ? "protection-enter" : "protection-enter-named", values));
        }
    }

    private boolean showNames() { return plugin.getConfigManager().values().getBoolean("notification.show-claim-name", true); }
    private boolean useTitle() { return plugin.getConfigManager().values().getBoolean("notification.use-title", false); }
}

