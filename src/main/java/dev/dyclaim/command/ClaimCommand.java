package dev.dyclaim.command;

import dev.dyclaim.DyClaim;
import dev.dyclaim.hook.ClaimPluginHooks;
import dev.dyclaim.hook.GriefPreventionHook;
import dev.dyclaim.hook.WorldGuardHook;
import dev.dyclaim.manager.ConfirmationManager;
import dev.dyclaim.manager.MessageManager;
import dev.dyclaim.model.ClaimData;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class ClaimCommand implements CommandExecutor, TabCompleter {

    private final DyClaim plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    public ClaimCommand(DyClaim plugin) {
        this.plugin = plugin;
    }

    /**
     * Retrieves the claim at the player's current chunk and validates ownership.
     * Returns null and sends an error message if the chunk is not claimed or the
     * player is not the owner.
     */
    private ClaimData requireOwnedClaim(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
        if (claim == null) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "sell-not-claimed"));
            return null;
        }
        if (!claim.getOwnerUUID().equals(player.getUniqueId()) && !player.hasPermission("dyclaim.admin")) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "sell-not-owner"));
            return null;
        }
        return claim;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageManager().getPrefixed(sender, "player-only"));
            return true;
        }

        if (args.length == 0) {
            handleClaim(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "onayla", "confirm" -> handleConfirm(player);
            case "reddet", "deny", "cancel" -> handleDeny(player);
            case "sat", "sell" -> handleSell(player);
            case "gor", "gör", "see" -> handleSee(player);
            case "bilgi", "info" -> handleInfo(player);
            case "liste", "list" -> handleList(player);
            case "pvp" -> handleToggle(player, "pvp");
            case "patlama", "explosion" -> handleToggle(player, "explosion");
            case "mob" -> handleToggle(player, "mob");
            case "güven", "guven", "trust" -> handleTrust(player, args);
            case "güvensil", "guvensil", "untrust" -> handleUntrust(player, args);
            case "güvenliste", "guvenliste", "trustlist" -> handleTrustList(player);
            case "admin" -> handleAdmin(player, args);
            case "yardim", "yardım", "help" -> handleHelp(player);
            default -> {
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "unknown-command"));
            }
        }

        return true;
    }

    private void handleClaim(Player player) {
        if (!player.hasPermission("dyclaim.claim")) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "no-permission"));
            return;
        }

        if (!plugin.getConfigManager().isAllowClaiming()) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "claiming-disabled"));
            return;
        }

        if (plugin.getConfigManager().isWorldBlacklisted(player.getWorld().getName())) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "world-blacklisted"));
            return;
        }

        Chunk chunk = player.getLocation().getChunk();

        if (WorldGuardHook.isRegionProtected(chunk) || GriefPreventionHook.isRegionProtected(chunk)
                || ClaimPluginHooks.isRegionProtected(chunk)) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "claim-region-protected"));
            return;
        }

        ClaimData existing = plugin.getClaimManager().getClaimAt(chunk);
        if (existing != null) {
            if (existing.getOwnerUUID().equals(player.getUniqueId())) {
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "claim-already-owned"));
            } else {
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "claim-already-claimed",
                        Map.of("{owner}", existing.getOwnerName())));
            }
            return;
        }

        int count = plugin.getClaimManager().getPlayerClaimCount(player.getUniqueId());
        int max = plugin.getConfigManager().getMaxClaimsPerPlayer();
        if (count >= max) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "claim-max-reached",
                    Map.of("{max}", String.valueOf(max))));
            return;
        }

        if (plugin.getCooldownManager().hasCooldown(player.getUniqueId())) {
            int remaining = plugin.getCooldownManager().getRemainingSeconds(player.getUniqueId());
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "claim-cooldown",
                    Map.of("{remaining}", String.valueOf(remaining))));
            return;
        }

        if (plugin.getEconomyManager().isEnabled()) {
            double price = plugin.getConfigManager().getClaimPrice();
            if (!plugin.getEconomyManager().hasEnough(player, price)) {
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "claim-no-money",
                        Map.of("{price}", plugin.getEconomyManager().formatMoney(price))));
                return;
            }
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "claim-confirmation",
                    Map.of("{price}", plugin.getEconomyManager().formatMoney(price))));
        } else {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "claim-free"));
        }

        sendConfirmationButtons(player);

        plugin.getConfirmationManager().addPending(
                player.getUniqueId(),
                ConfirmationManager.ActionType.CLAIM,
                uuid -> executeClaim(player),
                uuid -> player.sendMessage(plugin.getMessageManager().getPrefixed(player, "confirm-deny")));
    }

    private void executeClaim(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        if (plugin.getClaimManager().isChunkClaimed(chunk)) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "claim-already-claimed",
                    Map.of("{owner}", plugin.getClaimManager().getClaimAt(chunk).getOwnerName())));
            return;
        }

        if (plugin.getEconomyManager().isEnabled()) {
            double price = plugin.getConfigManager().getClaimPrice();
            if (!plugin.getEconomyManager().withdraw(player, price)) {
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "claim-no-money",
                        Map.of("{price}", plugin.getEconomyManager().formatMoney(price))));
                return;
            }
        }

        plugin.getClaimManager().claimChunk(player, chunk);
        plugin.getCooldownManager().setCooldown(player.getUniqueId());
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "claim-success",
                Map.of("{chunk}", chunk.getX() + ", " + chunk.getZ())));
    }

    private void handleSell(Player player) {
        if (!player.hasPermission("dyclaim.sell")) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "no-permission"));
            return;
        }

        ClaimData claim = requireOwnedClaim(player);
        if (claim == null)
            return;

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
    }

    private void handleToggle(Player player, String type) {
        ClaimData claim = requireOwnedClaim(player);
        if (claim == null)
            return;

        switch (type) {
            case "pvp" -> {
                boolean newVal = !claim.isPvpDisabled();
                claim.setPvpDisabled(newVal);
                plugin.getClaimManager().saveAll();
                String msgKey = newVal ? "toggle-pvp-disabled" : "toggle-pvp-enabled";
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, msgKey,
                        Map.of("{chunk}", claim.getChunkDisplay())));
            }
            case "explosion" -> {
                boolean newVal = !claim.isExplosionDisabled();
                claim.setExplosionDisabled(newVal);
                plugin.getClaimManager().saveAll();
                String msgKey = newVal ? "toggle-explosion-disabled" : "toggle-explosion-enabled";
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, msgKey,
                        Map.of("{chunk}", claim.getChunkDisplay())));
            }
            case "mob" -> {
                boolean newVal = !claim.isMobSpawnDisabled();
                claim.setMobSpawnDisabled(newVal);
                plugin.getClaimManager().saveAll();
                String msgKey = newVal ? "toggle-mob-disabled" : "toggle-mob-enabled";
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, msgKey,
                        Map.of("{chunk}", claim.getChunkDisplay())));
            }
        }
    }

    private void handleTrust(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "trust-usage"));
            return;
        }

        ClaimData claim = requireOwnedClaim(player);
        if (claim == null)
            return;

        String targetName = args[1];
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID targetUUID = target.getUniqueId();

        if (targetUUID.equals(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "trust-self"));
            return;
        }

        if (claim.isTrusted(targetUUID)) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "trust-already",
                    Map.of("{player}", targetName)));
            return;
        }

        claim.addTrusted(targetUUID);
        plugin.getClaimManager().saveAll();
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "trust-added",
                Map.of("{player}", targetName)));
    }

    private void handleUntrust(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "untrust-usage"));
            return;
        }

        ClaimData claim = requireOwnedClaim(player);
        if (claim == null)
            return;

        String targetName = args[1];
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!claim.removeTrusted(target.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "trust-not-found",
                    Map.of("{player}", targetName)));
            return;
        }

        plugin.getClaimManager().saveAll();
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "trust-removed",
                Map.of("{player}", targetName)));
    }

    private void handleTrustList(Player player) {
        ClaimData claim = requireOwnedClaim(player);
        if (claim == null)
            return;

        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "trust-list-header"));
        List<UUID> trusted = claim.getTrustedPlayers();
        if (trusted.isEmpty()) {
            player.sendMessage(plugin.getMessageManager().getMessage(player, "trust-list-empty"));
        } else {
            for (UUID uuid : trusted) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                String name = op.getName() != null ? op.getName() : uuid.toString();
                player.sendMessage(plugin.getMessageManager().getMessage(player, "trust-list-entry",
                        Map.of("{player}", name)));
            }
        }
    }

    private void handleConfirm(Player player) {
        if (!plugin.getConfirmationManager().hasPending(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "confirm-none"));
            return;
        }
        plugin.getConfirmationManager().confirm(player.getUniqueId());
    }

    private void handleDeny(Player player) {
        if (!plugin.getConfirmationManager().hasPending(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "confirm-none"));
            return;
        }
        plugin.getConfirmationManager().deny(player.getUniqueId());
    }

    private void handleSee(Player player) {
        if (!player.hasPermission("dyclaim.see")) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "no-permission"));
            return;
        }
        if (plugin.getChunkVisualizer().isViewing(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "see-already"));
            return;
        }
        plugin.getChunkVisualizer().showChunkBorders(player);
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "see-showing",
                Map.of("{duration}", String.valueOf(plugin.getConfigManager().getVisualizationDuration()))));
    }

    private void handleInfo(Player player) {
        if (!player.hasPermission("dyclaim.info")) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "no-permission"));
            return;
        }

        Chunk chunk = player.getLocation().getChunk();
        ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);

        player.sendMessage(plugin.getMessageManager().getMessage(player, "info-header",
                Map.of("{prefix}", plugin.getMessageManager().getPrefix())));

        if (claim == null) {
            player.sendMessage(plugin.getMessageManager().getMessage(player, "info-not-claimed"));
        } else {
            player.sendMessage(plugin.getMessageManager().getMessage(player, "info-owner",
                    Map.of("{owner}", claim.getOwnerName())));
            player.sendMessage(plugin.getMessageManager().getMessage(player, "info-chunk",
                    Map.of("{chunk}", claim.getChunkDisplay())));
            player.sendMessage(plugin.getMessageManager().getMessage(player, "info-world",
                    Map.of("{world}", claim.getWorld())));
            player.sendMessage(plugin.getMessageManager().getMessage(player, "info-date",
                    Map.of("{date}", dateFormat.format(new Date(claim.getClaimedAt())))));

            String pvpStatus = claim.isPvpDisabled() ? "§cOFF" : "§aON";
            String expStatus = claim.isExplosionDisabled() ? "§cOFF" : "§aON";
            String mobStatus = claim.isMobSpawnDisabled() ? "§cOFF" : "§aON";

            player.sendMessage(plugin.getMessageManager().getMessage(player, "info-pvp",
                    Map.of("{status}", pvpStatus)));
            player.sendMessage(plugin.getMessageManager().getMessage(player, "info-explosion",
                    Map.of("{status}", expStatus)));
            player.sendMessage(plugin.getMessageManager().getMessage(player, "info-mob",
                    Map.of("{status}", mobStatus)));

            List<UUID> trusted = claim.getTrustedPlayers();
            if (trusted.isEmpty()) {
                player.sendMessage(plugin.getMessageManager().getMessage(player, "info-trusted",
                        Map.of("{trusted}", "-")));
            } else {
                String names = trusted.stream()
                        .map(uuid -> {
                            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                            return op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
                        })
                        .collect(Collectors.joining(", "));
                player.sendMessage(plugin.getMessageManager().getMessage(player, "info-trusted",
                        Map.of("{trusted}", names)));
            }
        }

        player.sendMessage(plugin.getMessageManager().getMessage(player, "info-footer"));
    }

    private void handleList(Player player) {
        if (!player.hasPermission("dyclaim.list")) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "no-permission"));
            return;
        }

        List<ClaimData> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        player.sendMessage(plugin.getMessageManager().getMessage(player, "list-header",
                Map.of("{prefix}", plugin.getMessageManager().getPrefix())));

        if (claims.isEmpty()) {
            player.sendMessage(plugin.getMessageManager().getMessage(player, "list-empty"));
        } else {
            for (ClaimData claim : claims) {
                player.sendMessage(plugin.getMessageManager().getMessage(player, "list-entry",
                        Map.of("{world}", claim.getWorld(),
                                "{chunk}", claim.getChunkDisplay(),
                                "{date}", dateFormat.format(new Date(claim.getClaimedAt())))));
            }
        }

        player.sendMessage(plugin.getMessageManager().getMessage(player, "list-footer",
                Map.of("{count}", String.valueOf(claims.size()),
                        "{max}", String.valueOf(plugin.getConfigManager().getMaxClaimsPerPlayer()))));
    }

    private void handleHelp(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String prefix = msg.getPrefix();
        player.sendMessage(msg.getMessage(player, "help-header", Map.of("{prefix}", prefix)));
        player.sendMessage(msg.getMessage(player, "help-claim"));
        player.sendMessage(msg.getMessage(player, "help-sell"));
        player.sendMessage(msg.getMessage(player, "help-see"));
        player.sendMessage(msg.getMessage(player, "help-info"));
        player.sendMessage(msg.getMessage(player, "help-list"));
        player.sendMessage(msg.getMessage(player, "help-pvp"));
        player.sendMessage(msg.getMessage(player, "help-explosion"));
        player.sendMessage(msg.getMessage(player, "help-mob"));
        player.sendMessage(msg.getMessage(player, "help-trust"));
        player.sendMessage(msg.getMessage(player, "help-untrust"));
        player.sendMessage(msg.getMessage(player, "help-trustlist"));
        if (player.hasPermission("dyclaim.admin")) {
            player.sendMessage(msg.getMessage(player, "help-admin"));
        }
        player.sendMessage(msg.getMessage(player, "help-footer"));
    }

    @SuppressWarnings("deprecation")
    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("dyclaim.admin")) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "no-permission"));
            return;
        }
        if (args.length < 2) {
            showAdminHelp(player);
            return;
        }

        String adminSub = args[1].toLowerCase(Locale.ROOT);
        switch (adminSub) {
            case "ac", "aç", "enable" -> {
                plugin.getConfigManager().setAllowClaiming(true);
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-claiming-enabled"));
            }
            case "kapat", "disable" -> {
                plugin.getConfigManager().setAllowClaiming(false);
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-claiming-disabled"));
            }
            case "sil", "delete" -> handleAdminDelete(player, args);
            case "ver", "give" -> handleAdminGive(player, args);
            case "fiyat", "price" -> handleAdminPrice(player, args);
            case "cooldown" -> handleAdminCooldown(player, args);
            case "prefix" -> handleAdminPrefix(player, args);
            case "ekonomi", "economy" -> handleAdminEconomy(player, args);
            case "pvp" -> handleAdminToggleFeature(player, args, "pvp");
            case "patlama", "explosion" -> handleAdminToggleFeature(player, args, "explosion");
            case "mob" -> handleAdminToggleFeature(player, args, "mob");
            case "toplusat", "bulksell" -> handleAdminBulkSell(player, args);
            case "farkver", "pricediff" -> handleAdminPriceDiff(player, args);
            case "dil", "lang" -> handleAdminLang(player, args);
            case "karaliste", "blacklist" -> handleAdminBlacklist(player, args);
            case "reload", "yenile" -> {
                plugin.reload();
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "reload-success"));
            }
            default -> showAdminHelp(player);
        }
    }

    private void handleAdminBulkSell(Player player, String[] args) {
        double refund = 0;
        if (plugin.getEconomyManager().isEnabled()) {
            double price = plugin.getConfigManager().getClaimPrice();
            int percent = plugin.getConfigManager().getSellRefundPercent();
            refund = price * percent / 100.0;
        }

        if (args.length >= 3) {
            String targetName = args[2];
            UUID targetUUID = resolvePlayerUUID(player, targetName);
            if (targetUUID == null)
                return;

            final double finalRefund = refund;
            sendConfirmationButtons(player);
            plugin.getConfirmationManager().addPending(
                    player.getUniqueId(),
                    ConfirmationManager.ActionType.SELL,
                    uuid -> {
                        int removed = plugin.getClaimManager().removeAllPlayerClaimsWithRefund(targetUUID, finalRefund);
                        if (removed == 0) {
                            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-no-claims"));
                        } else {
                            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-bulk-sell-player",
                                    Map.of("{owner}", targetName,
                                            "{count}", String.valueOf(removed),
                                            "{refund}",
                                            plugin.getEconomyManager().formatMoney(finalRefund * removed))));
                        }
                    },
                    uuid -> player.sendMessage(plugin.getMessageManager().getPrefixed(player, "confirm-deny")));
        } else {
            final double finalRefund = refund;
            sendConfirmationButtons(player);
            plugin.getConfirmationManager().addPending(
                    player.getUniqueId(),
                    ConfirmationManager.ActionType.SELL,
                    uuid -> {
                        int removed = plugin.getClaimManager().removeAllClaimsWithRefund(finalRefund);
                        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-bulk-sell-all",
                                Map.of("{count}", String.valueOf(removed))));
                    },
                    uuid -> player.sendMessage(plugin.getMessageManager().getPrefixed(player, "confirm-deny")));
        }
    }

    private void handleAdminToggleFeature(Player player, String[] args, String feature) {
        if (args.length < 3) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-usage-toggle",
                    Map.of("{feature}", feature)));
            return;
        }

        String toggle = args[2].toLowerCase(Locale.ROOT);
        boolean disable;
        switch (toggle) {
            case "ac", "aç", "enable" -> disable = false;
            case "kapat", "disable" -> disable = true;
            default -> {
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-usage-toggle",
                        Map.of("{feature}", feature)));
                return;
            }
        }

        String status = disable ? "§cOFF" : "§aON";

        if (args.length >= 4 && (args[3].equalsIgnoreCase("tumu") || args[3].equalsIgnoreCase("tümü")
                || args[3].equalsIgnoreCase("all"))) {
            switch (feature) {
                case "pvp" -> plugin.getClaimManager().setAllClaimsPvp(disable);
                case "explosion" -> plugin.getClaimManager().setAllClaimsExplosion(disable);
                case "mob" -> plugin.getClaimManager().setAllClaimsMobSpawn(disable);
            }
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-toggle-all",
                    Map.of("{feature}", feature.toUpperCase(), "{status}", status)));
        } else {
            Chunk chunk = player.getLocation().getChunk();
            ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
            if (claim == null) {
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "sell-not-claimed"));
                return;
            }
            switch (feature) {
                case "pvp" -> claim.setPvpDisabled(disable);
                case "explosion" -> claim.setExplosionDisabled(disable);
                case "mob" -> claim.setMobSpawnDisabled(disable);
            }
            plugin.getClaimManager().saveAll();
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-toggle-single",
                    Map.of("{feature}", feature.toUpperCase(), "{status}", status)));
        }
    }

    private void handleAdminDelete(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getMessageManager().getPrefix() + "§c/claim admin delete <player> [all]");
            return;
        }
        String targetName = args[2];
        if (args.length >= 4 && args[3].equalsIgnoreCase("all")) {
            UUID targetUUID = resolvePlayerUUID(player, targetName);
            if (targetUUID == null)
                return;
            int removed = plugin.getClaimManager().removeAllPlayerClaims(targetUUID);
            if (removed == 0) {
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-no-claims"));
            } else {
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-claim-deleted-all",
                        Map.of("{owner}", targetName, "{count}", String.valueOf(removed))));
            }
        } else {
            Chunk chunk = player.getLocation().getChunk();
            ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
            if (claim == null) {
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "sell-not-claimed"));
                return;
            }
            plugin.getClaimManager().unclaimChunk(chunk);
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-claim-deleted",
                    Map.of("{owner}", claim.getOwnerName(), "{chunk}", claim.getChunkDisplay())));
        }
    }

    private void handleAdminGive(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getMessageManager().getPrefix() + "§c/claim admin give <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-player-not-found"));
            return;
        }
        Chunk chunk = player.getLocation().getChunk();
        plugin.getClaimManager().giveChunk(target, chunk);
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-claim-given",
                Map.of("{player}", target.getName(), "{chunk}", chunk.getX() + ", " + chunk.getZ())));
    }

    private void handleAdminPrice(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getMessageManager().getPrefix() + "§c/claim admin price <amount>");
            return;
        }
        try {
            double oldPrice = plugin.getConfigManager().getClaimPrice();
            double newPrice = Double.parseDouble(args[2]);
            plugin.getConfigManager().setClaimPrice(newPrice);
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-price-set",
                    Map.of("{price}", plugin.getEconomyManager().formatMoney(newPrice))));

            if (plugin.getConfigManager().isAutoRefundPriceDifference() && oldPrice > newPrice) {
                double diff = oldPrice - newPrice;
                int refunded = plugin.getClaimManager().refundPriceDifference(diff);
                if (refunded > 0) {
                    player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-price-diff-success",
                            Map.of("{refund}", plugin.getEconomyManager().formatMoney(diff),
                                    "{count}", String.valueOf(refunded))));
                }
            }
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getMessageManager().getPrefix() + "§cInvalid number.");
        }
    }

    private void handleAdminCooldown(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getMessageManager().getPrefix() + "§c/claim admin cooldown <seconds>");
            return;
        }
        try {
            int seconds = Integer.parseInt(args[2]);
            plugin.getConfigManager().setCooldownSeconds(seconds);
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-cooldown-set",
                    Map.of("{seconds}", String.valueOf(seconds))));
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getMessageManager().getPrefix() + "§cInvalid number.");
        }
    }

    private void handleAdminPrefix(Player player, String[] args) {
        String defaultPrefix = "&7[&dDyClaim&7]";
        String newPrefix;
        if (args.length < 3) {
            newPrefix = defaultPrefix;
        } else {
            StringBuilder prefixBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i > 2)
                    prefixBuilder.append(" ");
                prefixBuilder.append(args[i]);
            }
            newPrefix = prefixBuilder.toString().trim();
            if (newPrefix.isEmpty()) {
                newPrefix = defaultPrefix;
            }
        }
        plugin.getConfigManager().setPrefix(newPrefix);
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-prefix-set"));
    }

    private void handleAdminEconomy(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getMessageManager().getPrefix() + "§c/claim admin economy enable/disable");
            return;
        }
        String toggle = args[2].toLowerCase(Locale.ROOT);
        switch (toggle) {
            case "ac", "aç", "enable" -> {
                plugin.getConfigManager().setEconomyEnabled(true);
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-economy-enabled"));
            }
            case "kapat", "disable" -> {
                plugin.getConfigManager().setEconomyEnabled(false);
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-economy-disabled"));
            }
            default -> player.sendMessage(plugin.getMessageManager().getPrefix() +
                    "§c/claim admin economy enable/disable");
        }
    }

    private void showAdminHelp(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String prefix = msg.getPrefix();
        player.sendMessage(msg.getMessage(player, "admin-help-header", Map.of("{prefix}", prefix)));
        player.sendMessage(msg.getMessage(player, "admin-help-toggle"));
        player.sendMessage(msg.getMessage(player, "admin-help-delete"));
        player.sendMessage(msg.getMessage(player, "admin-help-give"));
        player.sendMessage(msg.getMessage(player, "admin-help-price"));
        player.sendMessage(msg.getMessage(player, "admin-help-cooldown"));
        player.sendMessage(msg.getMessage(player, "admin-help-prefix"));
        player.sendMessage(msg.getMessage(player, "admin-help-economy"));
        player.sendMessage(msg.getMessage(player, "admin-help-pvp"));
        player.sendMessage(msg.getMessage(player, "admin-help-explosion"));
        player.sendMessage(msg.getMessage(player, "admin-help-mob"));
        player.sendMessage(msg.getMessage(player, "admin-help-bulksell"));
        player.sendMessage(msg.getMessage(player, "admin-help-pricediff"));
        player.sendMessage(msg.getMessage(player, "admin-help-lang"));
        player.sendMessage(msg.getMessage(player, "admin-help-blacklist"));
        player.sendMessage(msg.getMessage(player, "admin-help-reload"));
        player.sendMessage(msg.getMessage(player, "admin-help-footer"));
    }

    private void handleAdminLang(Player player, String[] args) {
        if (args.length < 3) {
            String current = plugin.getConfigManager().getLang();
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    " §eCurrent: §f" + current + " §7| §e/claim admin lang <auto/en/tr>");
            return;
        }
        String lang = args[2].toLowerCase(Locale.ROOT);
        if (!lang.equals("auto") && !lang.equals("en") && !lang.equals("tr")) {
            player.sendMessage(plugin.getMessageManager().getPrefix() + " §cAvailable: auto, en, tr");
            return;
        }
        plugin.getConfigManager().setLang(lang);
        plugin.getMessageManager().reload();
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "reload-success"));
    }

    private void handleAdminBlacklist(Player player, String[] args) {
        if (args.length < 3) {
            var list = plugin.getConfigManager().getBlacklistedWorlds();
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    " §eBlacklist: §f" + (list.isEmpty() ? "-" : String.join(", ", list)));
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    " §7/claim admin blacklist add/remove <world>");
            return;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        if (args.length < 4) {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    " §c/claim admin blacklist " + action + " <world>");
            return;
        }
        String worldName = args[3];

        if (Bukkit.getWorld(worldName) == null) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-world-not-found",
                    Map.of("{world}", worldName)));
            return;
        }

        switch (action) {
            case "ekle", "add" -> {
                if (plugin.getConfigManager().addBlacklistedWorld(worldName)) {
                    player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-blacklist-added",
                            Map.of("{world}", worldName)));
                } else {
                    player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-blacklist-already",
                            Map.of("{world}", worldName)));
                }
            }
            case "cikar", "çıkar", "remove" -> {
                if (plugin.getConfigManager().removeBlacklistedWorld(worldName)) {
                    player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-blacklist-removed",
                            Map.of("{world}", worldName)));
                } else {
                    player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-blacklist-not-found",
                            Map.of("{world}", worldName)));
                }
            }
            default -> player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-usage-blacklist"));
        }
    }

    private void handleAdminPriceDiff(Player player, String[] args) {
        try {
            double oldPrice = args.length >= 3 ? Double.parseDouble(args[2])
                    : plugin.getConfigManager().getPreviousClaimPrice();
            double currentPrice = plugin.getConfigManager().getClaimPrice();
            double diff = oldPrice - currentPrice;

            if (diff <= 0) {
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-pricediff-error",
                        Map.of("{price}", plugin.getEconomyManager().formatMoney(currentPrice))));
                return;
            }

            int refunded = plugin.getClaimManager().refundPriceDifference(diff);
            if (refunded == 0) {
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-no-claims"));
            } else {
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-price-diff-success",
                        Map.of("{refund}", plugin.getEconomyManager().formatMoney(diff),
                                "{count}", String.valueOf(refunded))));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "admin-invalid-number"));
        }
    }

    @SuppressWarnings("deprecation")
    private UUID resolvePlayerUUID(Player sender, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target != null)
            return target.getUniqueId();
        var offlinePlayer = Bukkit.getOfflinePlayer(targetName);
        if (offlinePlayer.hasPlayedBefore())
            return offlinePlayer.getUniqueId();
        sender.sendMessage(plugin.getMessageManager().getPrefixed(sender, "admin-player-not-found"));
        return null;
    }

    @SuppressWarnings("deprecation")
    private void sendConfirmationButtons(Player player) {
        TextComponent accept = new TextComponent("  ");
        TextComponent acceptBtn = new TextComponent(plugin.getMessageManager().getMessage(player, "confirm-accept"));
        acceptBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim confirm"));
        acceptBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("§aClick to confirm")));
        TextComponent space = new TextComponent("  ");
        TextComponent denyBtn = new TextComponent(plugin.getMessageManager().getMessage(player, "confirm-deny"));
        denyBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim deny"));
        denyBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("§cClick to cancel")));
        accept.addExtra(acceptBtn);
        accept.addExtra(space);
        accept.addExtra(denyBtn);
        player.spigot().sendMessage(accept);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        String lang = plugin.getMessageManager().getPreferredLanguage(sender);
        boolean turkish = "tr".equals(lang);

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(turkish
                    ? List.of("sat", "gör", "bilgi", "liste", "pvp", "patlama", "mob", "güven", "güvensil",
                            "güvenliste", "yardım")
                    : List.of("sell", "see", "info", "list", "pvp", "explosion", "mob", "trust", "untrust", "trustlist",
                            "help"));
            if (sender.hasPermission("dyclaim.admin")) {
                subs.add("admin");
            }
            String input = args[0].toLowerCase(Locale.ROOT);
            for (String sub : subs) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("admin") && sender.hasPermission("dyclaim.admin")) {
                List<String> adminSubs = new ArrayList<>(turkish
                        ? List.of("aç", "kapat", "sil", "ver", "fiyat", "cooldown", "prefix", "ekonomi", "pvp",
                                "patlama", "mob", "toplusat", "farkver", "dil", "karaliste", "yenile")
                        : List.of("enable", "disable", "delete", "give", "price", "cooldown", "prefix", "economy",
                                "pvp", "explosion", "mob", "bulksell", "pricediff", "lang", "blacklist", "reload"));
                String input = args[1].toLowerCase(Locale.ROOT);
                for (String s : adminSubs) {
                    if (s.startsWith(input)) {
                        completions.add(s);
                    }
                }
            } else if (sub.equals("trust") || sub.equals("untrust") || sub.equals("guven") || sub.equals("güven")
                    || sub.equals("guvensil") || sub.equals("güvensil")) {
                String input = args[1].toLowerCase(Locale.ROOT);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(input)) {
                        completions.add(p.getName());
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("dyclaim.admin")) {
            String adminSub = args[1].toLowerCase(Locale.ROOT);
            if (adminSub.equals("delete") || adminSub.equals("give") || adminSub.equals("bulksell")
                    || adminSub.equals("sil") || adminSub.equals("ver") || adminSub.equals("toplusat")) {
                String input = args[2].toLowerCase(Locale.ROOT);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(input)) {
                        completions.add(p.getName());
                    }
                }
            } else if (adminSub.equals("economy") || adminSub.equals("ekonomi")
                    || adminSub.equals("pvp") || adminSub.equals("explosion") || adminSub.equals("patlama")
                    || adminSub.equals("mob")) {
                List<String> toggles = turkish ? List.of("aç", "kapat") : List.of("enable", "disable");
                String input = args[2].toLowerCase(Locale.ROOT);
                for (String t : toggles) {
                    if (t.startsWith(input)) {
                        completions.add(t);
                    }
                }
            } else if (adminSub.equals("lang") || adminSub.equals("dil")) {
                List<String> langs = List.of("auto", "en", "tr");
                String input = args[2].toLowerCase(Locale.ROOT);
                for (String l : langs) {
                    if (l.startsWith(input)) {
                        completions.add(l);
                    }
                }
            } else if (adminSub.equals("blacklist") || adminSub.equals("karaliste")) {
                List<String> actions = turkish ? List.of("ekle", "çıkar") : List.of("add", "remove");
                String input = args[2].toLowerCase(Locale.ROOT);
                for (String a : actions) {
                    if (a.startsWith(input)) {
                        completions.add(a);
                    }
                }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin")) {
            String adminSub = args[1].toLowerCase(Locale.ROOT);
            if (adminSub.equals("delete") || adminSub.equals("sil") || adminSub.equals("pvp")
                    || adminSub.equals("explosion")
                    || adminSub.equals("patlama") || adminSub.equals("mob")) {
                if ("all".startsWith(args[3].toLowerCase(Locale.ROOT))) {
                    completions.add("all");
                }
            }
        }

        return completions;
    }
}