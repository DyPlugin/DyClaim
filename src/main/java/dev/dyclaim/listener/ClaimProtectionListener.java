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
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.Location;
import org.bukkit.Material;

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
        if (plugin.getAccessManager().allows(player, claim, "build"))
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
        if (plugin.getAccessManager().allows(player, claim, "build"))
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
        String typeName = event.getClickedBlock().getType().name();
        String right=typeName.contains("TRAPDOOR")?"trapdoors":(typeName.contains("DOOR")||typeName.contains("GATE"))?"doors":
                (typeName.contains("BUTTON")||typeName.contains("LEVER")||typeName.contains("REPEATER")||typeName.contains("COMPARATOR")||typeName.contains("PRESSURE"))?"redstone":"containers";
        if(plugin.getAccessManager().allows(player,claim,right))return;
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
            boolean mob=event.getEntity() instanceof LivingEntity && !(event.getEntity() instanceof Player);
            if (claim != null && !plugin.getAccessManager().enabled(claim, mob?"mob-explosions":"explosions")) {
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
            if (claim != null && !plugin.getAccessManager().enabled(claim, "explosions")) {
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
        if (victimClaim != null && !plugin.getAccessManager().enabled(victimClaim, "pvp"))
            blocked = true;
        if (attackerClaim != null && !plugin.getAccessManager().enabled(attackerClaim, "pvp"))
            blocked = true;

        if (blocked && !plugin.getAccessManager().bypass(attacker)) {
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
        if (claim != null && !plugin.getAccessManager().enabled(claim, "mob-spawning")) {
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
        if (plugin.getAccessManager().allows(player, claim, "build"))
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
        if (plugin.getAccessManager().allows(player, claim, "build"))
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
        if (plugin.getAccessManager().allows(player, claim, "entities"))
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
        if (plugin.getAccessManager().allows(player, claim, "entities"))
            return;
        event.setCancelled(true);
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "protection-entity"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if(!plugin.getConfigManager().values().getBoolean("protection.boundaries.block-cross-owner-pistons"))return;
        ClaimData piston=plugin.getClaimManager().getClaimAt(event.getBlock().getChunk());
        if(crosses(piston,plugin.getClaimManager().getClaimAt(event.getBlock().getRelative(event.getDirection()).getChunk()))){event.setCancelled(true);return;}
        for (Block block : event.getBlocks()) {
            ClaimData source=plugin.getClaimManager().getClaimAt(block.getChunk()),destination=plugin.getClaimManager().getClaimAt(block.getRelative(event.getDirection()).getChunk());
            if(crosses(piston,source)||crosses(source,destination)||crosses(piston,destination)){event.setCancelled(true);return;}
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if(!plugin.getConfigManager().values().getBoolean("protection.boundaries.block-cross-owner-pistons"))return;
        ClaimData piston=plugin.getClaimManager().getClaimAt(event.getBlock().getChunk());
        for (Block block : event.getBlocks()) {
            ClaimData source=plugin.getClaimManager().getClaimAt(block.getChunk());
            if(crosses(piston,source)){event.setCancelled(true);return;}
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if(!plugin.getConfigManager().values().getBoolean("protection.boundaries.block-external-fire"))return;
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
            if (claim != null && plugin.getConfigManager().values().getBoolean("protection.boundaries.block-external-fire")) {
                event.setCancelled(true);
            }
        }
        if (event.getCause() == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL && event.getPlayer() != null) {
            Chunk chunk = event.getBlock().getChunk();
            ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
            if (claim == null)
                return;
            if (plugin.getAccessManager().allows(event.getPlayer(), claim, "build"))
                return;
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageManager().getPrefixed(event.getPlayer(), "protection-interact"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if(!plugin.getConfigManager().values().getBoolean("protection.boundaries.block-external-fluid"))return;
        Chunk fromChunk = event.getBlock().getChunk();
        Chunk toChunk = event.getToBlock().getChunk();
        if (fromChunk.equals(toChunk))
            return;

        ClaimData toClaim = plugin.getClaimManager().getClaimAt(toChunk);
        ClaimData fromClaim = plugin.getClaimManager().getClaimAt(fromChunk);
        if (crosses(fromClaim,toClaim)) {
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
        if(event.getBlock().getType()==Material.FARMLAND && !(entity instanceof Player)
                && plugin.getConfigManager().values().getBoolean("protection.farmland.protect-from-mobs")){event.setCancelled(true);return;}
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
        if (plugin.getAccessManager().allows(player, claim, "entities"))
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
        if (plugin.getAccessManager().allows(player, claim, "entities"))
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
        if (plugin.getAccessManager().allows(player, claim, "entities"))
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
        if (plugin.getAccessManager().allows(event.getPlayer(), claim, "build"))
            return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(plugin.getMessageManager().getPrefixed(event.getPlayer(), "protection-entity"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player)
            return;
        if(entity instanceof Villager) {
            Player attacker=getPlayerAttacker(event.getDamager());
            ClaimData claim=plugin.getClaimManager().getClaimAt(entity.getLocation().getChunk());
            if(attacker!=null&&claim!=null&&!plugin.getAccessManager().bypass(attacker)
                    &&(!plugin.getAccessManager().enabled(claim,"villager-player-damage")||!plugin.getAccessManager().allows(attacker,claim,"entities"))) {
                event.setCancelled(true);attacker.sendMessage(plugin.getMessageManager().getPrefixed(attacker,"protection-villager"));
            }
            return;
        }

        if (entity instanceof ItemFrame || entity instanceof ArmorStand
                || entity instanceof Painting || entity instanceof Hanging) {

            Player attacker = getPlayerAttacker(event.getDamager());
            if (attacker == null)
                return;

            Chunk chunk = entity.getLocation().getChunk();
            ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
            if (claim == null)
                return;
            if (plugin.getAccessManager().allows(attacker, claim, "entities"))
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
        if(typeName.contains("PRESSURE")) {
            ClaimData claim=plugin.getClaimManager().getClaimAt(event.getClickedBlock().getChunk());
            if(!plugin.getAccessManager().allows(event.getPlayer(),claim,"redstone"))event.setCancelled(true);
            return;
        }
        if (typeName.contains("FARMLAND") || typeName.contains("TURTLE_EGG")) {
            if(typeName.contains("FARMLAND")&&!plugin.getConfigManager().values().getBoolean("protection.farmland.protect-from-visitors"))return;
            Player player = event.getPlayer();
            Chunk chunk = event.getClickedBlock().getChunk();
            ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
            if (claim == null)
                return;
            if (plugin.getAccessManager().allows(player, claim, "build"))
                return;
            event.setCancelled(true);
        }
    }

    public static boolean crosses(ClaimData source,ClaimData destination) {
        if(source==null&&destination==null)return false;
        return source==null||destination==null||!source.getOwnerUUID().equals(destination.getOwnerUUID());
    }
    private ClaimData inventoryClaim(Inventory inventory){Location loc=inventory.getLocation();return loc==null?null:plugin.getClaimManager().getClaimAt(loc.getChunk());}
    private java.util.List<Inventory> inventoryParts(Inventory inventory) {
        if(inventory instanceof DoubleChestInventory chest)return java.util.List.of(chest.getLeftSide(),chest.getRightSide());
        return java.util.List.of(inventory);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void inventoryOpen(InventoryOpenEvent event) {
        if(!(event.getPlayer() instanceof Player player))return;
        for(Inventory part:inventoryParts(event.getInventory())) {
            if(!plugin.getAccessManager().allows(player,inventoryClaim(part),"containers")){event.setCancelled(true);return;}
        }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void hopper(InventoryMoveItemEvent event) {
        if(!plugin.getConfigManager().values().getBoolean("protection.boundaries.block-cross-owner-hoppers"))return;
        for(Inventory source:inventoryParts(event.getSource()))for(Inventory target:inventoryParts(event.getDestination()))
            if(crosses(inventoryClaim(source),inventoryClaim(target))){event.setCancelled(true);return;}
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void hopperPickup(InventoryPickupItemEvent event) {
        if(!plugin.getConfigManager().values().getBoolean("protection.boundaries.block-cross-owner-hoppers"))return;
        for(Inventory part:inventoryParts(event.getInventory()))
            if(crosses(inventoryClaim(part),plugin.getClaimManager().getClaimAt(event.getItem().getLocation().getChunk()))){event.setCancelled(true);return;}
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void burn(BlockBurnEvent event){
        if(plugin.getConfigManager().values().getBoolean("protection.boundaries.block-external-fire")&&plugin.getClaimManager().getClaimAt(event.getBlock().getChunk())!=null)event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGH) public void redstone(BlockRedstoneEvent event){
        if(event.getNewCurrent()<=event.getOldCurrent()||!plugin.getConfigManager().values().getBoolean("protection.boundaries.block-external-redstone-effects"))return;
        ClaimData target=plugin.getClaimManager().getClaimAt(event.getBlock().getChunk());if(target==null)return;
        for(org.bukkit.block.BlockFace face:java.util.List.of(org.bukkit.block.BlockFace.NORTH,org.bukkit.block.BlockFace.SOUTH,org.bukkit.block.BlockFace.EAST,org.bukkit.block.BlockFace.WEST,org.bukkit.block.BlockFace.UP,org.bukkit.block.BlockFace.DOWN)) {
            Block neighbor=event.getBlock().getRelative(face);
            if(neighbor.getBlockPower()>0&&crosses(plugin.getClaimManager().getClaimAt(neighbor.getChunk()),target)){event.setNewCurrent(event.getOldCurrent());return;}
        }
    }
}
