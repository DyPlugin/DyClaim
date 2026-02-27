package dev.dyclaim.command;

import dev.dyclaim.DyClaim;
import dev.dyclaim.manager.ConfirmationManager;
import dev.dyclaim.model.ClaimData;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class UnclaimCommand implements CommandExecutor {

    private final DyClaim plugin;

    public UnclaimCommand(DyClaim plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageManager().getPrefixed(sender, "player-only"));
            return true;
        }

        if (!player.hasPermission("dyclaim.sell")) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "no-permission"));
            return true;
        }

        Chunk chunk = player.getLocation().getChunk();
        ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);

        if (claim == null) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "sell-not-claimed"));
            return true;
        }

        if (!claim.getOwnerUUID().equals(player.getUniqueId()) && !player.hasPermission("dyclaim.admin")) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "sell-not-owner"));
            return true;
        }

        double refund = 0;
        if (plugin.getEconomyManager().isEnabled()) {
            double price = plugin.getConfigManager().getClaimPrice();
            int percent = plugin.getConfigManager().getSellRefundPercent();
            refund = price * percent / 100.0;
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "sell-confirmation",
                    Map.of("{refund}", plugin.getEconomyManager().formatMoney(refund))));
        } else {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "sell-confirmation",
                    Map.of("{refund}", "0")));
        }

        sendConfirmationButtons(player);

        final double finalRefund = refund;
        plugin.getConfirmationManager().addPending(
                player.getUniqueId(),
                ConfirmationManager.ActionType.SELL,
                uuid -> SellHelper.executeSell(plugin, player, finalRefund),
                uuid -> player.sendMessage(plugin.getMessageManager().getPrefixed(player, "confirm-deny")));

        return true;
    }

    @SuppressWarnings("deprecation")
    private void sendConfirmationButtons(Player player) {
        TextComponent accept = new TextComponent("  ");
        TextComponent acceptBtn = new TextComponent(plugin.getMessageManager().getMessage(player, "confirm-accept"));
        acceptBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim confirm"));
        acceptBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("§a" + plugin.getMessageManager().getRawMessage("confirm-accept"))));
        TextComponent space = new TextComponent("  ");
        TextComponent denyBtn = new TextComponent(plugin.getMessageManager().getMessage(player, "confirm-deny"));
        denyBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim deny"));
        denyBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("§c" + plugin.getMessageManager().getRawMessage("confirm-deny"))));
        accept.addExtra(acceptBtn);
        accept.addExtra(space);
        accept.addExtra(denyBtn);
        player.spigot().sendMessage(accept);
    }
}
