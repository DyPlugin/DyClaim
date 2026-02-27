package dev.dyclaim.command;

import dev.dyclaim.DyClaim;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Shared sell/unclaim execution logic used by both ClaimCommand and
 * UnclaimCommand.
 * Eliminates duplication of the refund + unclaim + message pattern.
 */
public final class SellHelper {

    private SellHelper() {
    }

    public static void executeSell(DyClaim plugin, Player player, double refund) {
        Chunk chunk = player.getLocation().getChunk();
        if (!plugin.getClaimManager().isChunkClaimed(chunk)) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "sell-not-claimed"));
            return;
        }
        plugin.getClaimManager().unclaimChunk(chunk);
        if (plugin.getEconomyManager().isEnabled() && refund > 0) {
            plugin.getEconomyManager().deposit(player, refund);
        }
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "sell-success",
                Map.of("{refund}", plugin.getEconomyManager().formatMoney(refund))));
    }
}
