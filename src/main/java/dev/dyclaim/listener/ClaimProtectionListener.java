package dev.dyclaim.listener;

import dev.dyclaim.DyClaim;
import dev.dyclaim.model.ClaimData;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

import java.util.Iterator;
import java.util.Set;

public class ClaimProtectionListener implements Listener {

    private final DyClaim plugin;

    private static final Set<String> PROTECTED_INTERACTION_KEYWORDS = Set.of(
            "CHEST", "FURNACE", "HOPPER", "DROPPER", "DISPENSER", "BARREL",
            "SHULKER", "ANVIL", "BREWING", "BEACON", "DOOR", "GATE",
            "TRAPDOOR", "BUTTON", "LEVER", "PRESSURE", "REPEATER", "COMPARATOR",
            "NOTE_BLOCK", "JUKEBOX", "ENCHANTING", "CRAFTING", "GRINDSTONE",
            "STONECUTTER", "LOOM", "CARTOGRAPHY", "SMITHING", "BELL",
            "CAMPFIRE", "COMPOSTER", "LECTERN", "RESPAWN_ANCHOR",
            "LODESTONE", "BED", "DECORATED_POT", "CHISELED_BOOKSHELF");

    public ClaimProtectionListener(DyClaim plugin) {
        this.plugin = plugin;
    }

    private static boolean isProtectedInteraction(String typeName) {
        for (String keyword : PROTECTED_INTERACTION_KEYWORDS) {
            if (typeName.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlock().getChunk();
        ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
        if (claim == null)
            return;
        if (claim.isAllowed(player.getUniqueId()) || player.hasPermission("dyclaim.admin.bypass"))
            return;
        event.setCancelled(true);
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "protection-block-break"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlock().getChunk();
        ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
        if (claim == null)
            return;
        if (claim.isAllowed(player.getUniqueId()) || player.hasPermission("dyclaim.admin.bypass"))
            return;
        event.setCancelled(true);
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "protection-block-place"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (event.getClickedBlock() == null)
            return;

        Player player = event.getPlayer();
        Chunk chunk = event.getClickedBlock().getChunk();
        ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
        if (claim == null)
            return;
        if (claim.isAllowed(player.getUniqueId()) || player.hasPermission("dyclaim.admin.bypass"))
            return;

        String typeName = event.getClickedBlock().getType().name();
        if (isProtectedInteraction(typeName)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "protection-interact"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            ClaimData claim = plugin.getClaimManager().getClaimAt(block.getChunk());
            if (claim != null && claim.isExplosionDisabled()) {
                iterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            ClaimData claim = plugin.getClaimManager().getClaimAt(block.getChunk());
            if (claim != null && claim.isExplosionDisabled()) {
                iterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim))
            return;
        Player attacker = getPlayerAttacker(event.getDamager());
        if (attacker == null)
            return;

        Chunk victimChunk = victim.getLocation().getChunk();
        Chunk attackerChunk = attacker.getLocation().getChunk();
        ClaimData victimClaim = plugin.getClaimManager().getClaimAt(victimChunk);
        ClaimData attackerClaim = plugin.getClaimManager().getClaimAt(attackerChunk);

        boolean blocked = false;
        if (victimClaim != null && victimClaim.isPvpDisabled())
            blocked = true;
        if (attackerClaim != null && attackerClaim.isPvpDisabled())
            blocked = true;

        if (blocked && !attacker.hasPermission("dyclaim.admin.bypass")) {
            event.setCancelled(true);
            attacker.sendMessage(plugin.getMessageManager().getPrefixed(attacker, "protection-pvp"));
        }
    }

    private Player getPlayerAttacker(Entity damager) {
        if (damager instanceof Player player)
            return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player)
            return player;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.PATROL) {
            return;
        }
        if (!(event.getEntity() instanceof Monster))
            return;
        Chunk chunk = event.getLocation().getChunk();
        ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
        if (claim != null && claim.isMobSpawnDisabled()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlock().getChunk();
        ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
        if (claim == null)
            return;
        if (claim.isAllowed(player.getUniqueId()) || player.hasPermission("dyclaim.admin.bypass"))
            return;
        event.setCancelled(true);
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "protection-block-place"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlock().getChunk();
        ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
        if (claim == null)
            return;
        if (claim.isAllowed(player.getUniqueId()) || player.hasPermission("dyclaim.admin.bypass"))
            return;
        event.setCancelled(true);
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "protection-block-break"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Entity remover = event.getRemover();
        if (remover == null)
            return;
        Player player = null;
        if (remover instanceof Player p)
            player = p;
        else if (remover instanceof Projectile proj && proj.getShooter() instanceof Player p)
            player = p;
        if (player == null)
            return;
        Chunk chunk = event.getEntity().getLocation().getChunk();
        ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
        if (claim == null)
            return;
        if (claim.isAllowed(player.getUniqueId()) || player.hasPermission("dyclaim.admin.bypass"))
            return;
        event.setCancelled(true);
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "protection-entity"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null)
            return;
        Chunk chunk = event.getEntity().getLocation().getChunk();
        ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
        if (claim == null)
            return;
        if (claim.isAllowed(player.getUniqueId()) || player.hasPermission("dyclaim.admin.bypass"))
            return;
        event.setCancelled(true);
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "protection-entity"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Chunk pistonChunk = event.getBlock().getChunk();
        for (Block block : event.getBlocks()) {
            Block destination = block.getRelative(event.getDirection());
            Chunk destChunk = destination.getChunk();
            if (!destChunk.equals(pistonChunk)) {
                ClaimData destClaim = plugin.getClaimManager().getClaimAt(destChunk);
                ClaimData srcClaim = plugin.getClaimManager().getClaimAt(pistonChunk);
                if (destClaim != null
                        && (srcClaim == null || !destClaim.getOwnerUUID().equals(srcClaim.getOwnerUUID()))) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        Chunk pistonChunk = event.getBlock().getChunk();
        for (Block block : event.getBlocks()) {
            Chunk srcChunk = block.getChunk();
            if (!srcChunk.equals(pistonChunk)) {
                ClaimData srcClaim = plugin.getClaimManager().getClaimAt(srcChunk);
                ClaimData pistonClaim = plugin.getClaimManager().getClaimAt(pistonChunk);
                if (srcClaim != null
                        && (pistonClaim == null || !srcClaim.getOwnerUUID().equals(pistonClaim.getOwnerUUID()))) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.getSource().getType().name().contains("FIRE")) {
            Chunk chunk = event.getBlock().getChunk();
            ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
            if (claim != null) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getCause() == BlockIgniteEvent.IgniteCause.SPREAD
                || event.getCause() == BlockIgniteEvent.IgniteCause.LAVA
                || event.getCause() == BlockIgniteEvent.IgniteCause.LIGHTNING) {
            Chunk chunk = event.getBlock().getChunk();
            ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
            if (claim != null) {
                event.setCancelled(true);
            }
        }
        if (event.getCause() == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL && event.getPlayer() != null) {
            Chunk chunk = event.getBlock().getChunk();
            ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
            if (claim == null)
                return;
            if (claim.isAllowed(event.getPlayer().getUniqueId())
                    || event.getPlayer().hasPermission("dyclaim.admin.bypass"))
                return;
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageManager().getPrefixed(event.getPlayer(), "protection-interact"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Chunk fromChunk = event.getBlock().getChunk();
        Chunk toChunk = event.getToBlock().getChunk();
        if (fromChunk.equals(toChunk))
            return;

        ClaimData toClaim = plugin.getClaimManager().getClaimAt(toChunk);
        ClaimData fromClaim = plugin.getClaimManager().getClaimAt(fromChunk);
        if (toClaim != null && (fromClaim == null || !toClaim.getOwnerUUID().equals(fromClaim.getOwnerUUID()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Chunk chunk = event.getBlock().getChunk();
        ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
        if (claim == null)
            return;

        Entity entity = event.getEntity();
        if (entity instanceof Enderman || entity instanceof Wither || entity instanceof WitherSkull) {
            event.setCancelled(true);
        }
        if (entity instanceof Player player) {
            if (!plugin.getClaimManager().isAllowed(player, chunk)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        Chunk chunk = entity.getLocation().getChunk();
        ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
        if (claim == null)
            return;
        if (claim.isAllowed(player.getUniqueId()) || player.hasPermission("dyclaim.admin.bypass"))
            return;

        if (entity instanceof ItemFrame || entity instanceof ArmorStand) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "protection-entity"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getRightClicked().getLocation().getChunk();
        ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
        if (claim == null)
            return;
        if (claim.isAllowed(player.getUniqueId()) || player.hasPermission("dyclaim.admin.bypass"))
            return;
        event.setCancelled(true);
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "protection-entity"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!(event.getAttacker() instanceof Player player))
            return;
        Chunk chunk = event.getVehicle().getLocation().getChunk();
        ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
        if (claim == null)
            return;
        if (claim.isAllowed(player.getUniqueId()) || player.hasPermission("dyclaim.admin.bypass"))
            return;
        event.setCancelled(true);
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "protection-entity"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT)
            return;
        if (event.getTo() == null)
            return;
        Chunk toChunk = event.getTo().getChunk();
        ClaimData claim = plugin.getClaimManager().getClaimAt(toChunk);
        if (claim == null)
            return;
        if (claim.isAllowed(event.getPlayer().getUniqueId()) || event.getPlayer().hasPermission("dyclaim.admin.bypass"))
            return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(plugin.getMessageManager().getPrefixed(event.getPlayer(), "protection-entity"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player)
            return;

        if (entity instanceof ItemFrame || entity instanceof ArmorStand
                || entity instanceof Painting || entity instanceof Hanging) {

            Player attacker = getPlayerAttacker(event.getDamager());
            if (attacker == null)
                return;

            Chunk chunk = entity.getLocation().getChunk();
            ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
            if (claim == null)
                return;
            if (claim.isAllowed(attacker.getUniqueId()) || attacker.hasPermission("dyclaim.admin.bypass"))
                return;
            event.setCancelled(true);
            attacker.sendMessage(plugin.getMessageManager().getPrefixed(attacker, "protection-entity"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractPhysical(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL)
            return;
        if (event.getClickedBlock() == null)
            return;

        String typeName = event.getClickedBlock().getType().name();
        if (typeName.contains("FARMLAND") || typeName.contains("TURTLE_EGG")) {
            Player player = event.getPlayer();
            Chunk chunk = event.getClickedBlock().getChunk();
            ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
            if (claim == null)
                return;
            if (claim.isAllowed(player.getUniqueId()) || player.hasPermission("dyclaim.admin.bypass"))
                return;
            event.setCancelled(true);
        }
    }
}
