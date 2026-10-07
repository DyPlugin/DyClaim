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
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.block.BlockState;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.block.data.Directional;
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
            "LODESTONE", "BED", "DECORATED_POT", "CHISELED_BOOKSHELF", "SIGN");

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
        if (event instanceof BlockMultiPlaceEvent multiple) {
            for (BlockState state : multiple.getReplacedBlockStates()) {
                ClaimData part = plugin.getClaimManager().getClaimAt(state.getBlock().getChunk());
                if (!plugin.getAccessManager().allows(player, part, "build")) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getMessageManager().getPrefixed(player, "protection-block-place"));
                    return;
                }
            }
        }
        Chunk chunk = event.getBlock().getChunk();
        ClaimData claim = plugin.getClaimManager().getClaimAt(chunk);
        if (claim == null)
            return;
        if (plugin.getAccessManager().allows(player, claim, "build"))
            return;
        event.setCancelled(true);
        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "protection-block-place"));
    }

    @EventHandler(priority = EventPriority.HIGH)
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
        if(event.getItem()!=null&&!plugin.getAccessManager().allows(player,claim,"build")) {
            String item=event.getItem().getType().name();
            if(modifiesBlock(item,typeName)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getMessageManager().getPrefixed(player,"protection-interact"));
                return;
            }
        }
        String right=typeName.contains("TRAPDOOR")?"trapdoors":(typeName.contains("DOOR")||typeName.contains("GATE"))?"doors":
                (typeName.contains("BUTTON")||typeName.contains("LEVER")||typeName.contains("REPEATER")||typeName.contains("COMPARATOR")||typeName.contains("PRESSURE"))?"redstone":"containers";
        if(plugin.getAccessManager().allows(player,claim,right))return;
        if (isProtectedInteraction(typeName)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "protection-interact"));
        }
    }

    private boolean modifiesBlock(String item,String block) {
        boolean sign=block.contains("SIGN"),copper=block.contains("COPPER");
        if(item.endsWith("_AXE"))return copper||block.endsWith("_LOG")||block.endsWith("_WOOD")
                ||block.endsWith("_STEM")||block.endsWith("_HYPHAE")||block.endsWith("BAMBOO_BLOCK");
        if(item.endsWith("_HOE")||item.endsWith("_SHOVEL"))return Set.of("GRASS_BLOCK","DIRT","PODZOL","COARSE_DIRT","MYCELIUM","ROOTED_DIRT","DIRT_PATH").contains(block);
        if(item.equals("SHEARS"))return block.contains("PUMPKIN")||block.equals("BEEHIVE")||block.equals("BEE_NEST");
        if(item.equals("HONEYCOMB"))return copper||sign;
        if(item.endsWith("INK_SAC")||item.endsWith("_DYE"))return sign;
        return item.equals("BONE_MEAL")&&!isProtectedInteraction(block);
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void signChange(SignChangeEvent event) {
        ClaimData claim=plugin.getClaimManager().getClaimAt(event.getBlock().getChunk());
        if(!plugin.getAccessManager().allows(event.getPlayer(),claim,"build"))event.setCancelled(true);
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
        if (attacker == null) {
            if(event.getDamager() instanceof Projectile projectile
                    &&projectile.getShooter() instanceof org.bukkit.projectiles.BlockProjectileSource source) {
                ClaimData target=plugin.getClaimManager().getClaimAt(victim.getLocation().getChunk());
                ClaimData origin=plugin.getClaimManager().getClaimAt(source.getBlock().getChunk());
                if((target!=null&&!plugin.getAccessManager().enabled(target,"pvp"))
                        ||(origin!=null&&!plugin.getAccessManager().enabled(origin,"pvp")))event.setCancelled(true);
            }
            return;
        }

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
        if (damager instanceof Tameable tame && tame.getOwner() != null)
            return org.bukkit.Bukkit.getPlayer(tame.getOwner().getUniqueId());
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player)
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

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void hangingExplosion(HangingBreakEvent event) {
        if(event.getCause()!=HangingBreakEvent.RemoveCause.EXPLOSION)return;
        ClaimData claim=plugin.getClaimManager().getClaimAt(event.getEntity().getLocation().getChunk());
        if(claim==null)return;
        String setting=event instanceof HangingBreakByEntityEvent byEntity
                &&byEntity.getRemover() instanceof LivingEntity&&!(byEntity.getRemover() instanceof Player)
                ?"mob-explosions":"explosions";
        if(!plugin.getAccessManager().enabled(claim,setting))event.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void explosionEntityDamage(EntityDamageEvent event) {
        if(event.getCause()!=EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                &&event.getCause()!=EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)return;
        Entity entity=event.getEntity();
        if(!(entity instanceof Hanging||entity instanceof ArmorStand||entity instanceof Animals
                ||entity instanceof Villager||entity instanceof Vehicle))return;
        ClaimData claim=plugin.getClaimManager().getClaimAt(entity.getLocation().getChunk());
        if(claim==null)return;
        String setting=event instanceof EntityDamageByEntityEvent byEntity
                &&byEntity.getDamager() instanceof LivingEntity&&!(byEntity.getDamager() instanceof Player)
                ?"mob-explosions":"explosions";
        if(!plugin.getAccessManager().enabled(claim,setting))event.setCancelled(true);
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
        if (event.getPlayer() != null) {
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

        if (entity instanceof ItemFrame || entity instanceof ArmorStand || entity instanceof Animals
                || entity instanceof Villager || entity instanceof Vehicle) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().getPrefixed(player, "protection-entity"));
        }
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void interactAtEntity(PlayerInteractAtEntityEvent event) { onPlayerInteractEntity(event); }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void leash(PlayerLeashEntityEvent event) {
        ClaimData claim=plugin.getClaimManager().getClaimAt(event.getEntity().getLocation().getChunk());
        if(!plugin.getAccessManager().allows(event.getPlayer(),claim,"entities"))event.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void unleash(PlayerUnleashEntityEvent event) {
        ClaimData claim=plugin.getClaimManager().getClaimAt(event.getEntity().getLocation().getChunk());
        if(!plugin.getAccessManager().allows(event.getPlayer(),claim,"entities"))event.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void vehicleEnter(VehicleEnterEvent event) {
        if(!(event.getEntered() instanceof Player player))return;
        ClaimData claim=plugin.getClaimManager().getClaimAt(event.getVehicle().getLocation().getChunk());
        if(!plugin.getAccessManager().allows(player,claim,"entities"))event.setCancelled(true);
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
        Player player = getPlayerAttacker(event.getAttacker());
        if (player == null) return;
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
        if((entity instanceof Hanging||entity instanceof ArmorStand||entity instanceof Animals
                ||entity instanceof Villager||entity instanceof Vehicle)&&event.getDamager() instanceof Projectile projectile
                &&projectile.getShooter() instanceof org.bukkit.projectiles.BlockProjectileSource source) {
            ClaimData target=plugin.getClaimManager().getClaimAt(entity.getLocation().getChunk());
            ClaimData origin=plugin.getClaimManager().getClaimAt(source.getBlock().getChunk());
            if(target!=null&&crosses(origin,target)){event.setCancelled(true);return;}
        }
        if(entity instanceof Villager) {
            Player attacker=getPlayerAttacker(event.getDamager());
            ClaimData claim=plugin.getClaimManager().getClaimAt(entity.getLocation().getChunk());
            if(attacker!=null&&claim!=null&&!plugin.getAccessManager().bypass(attacker)
                    &&(!plugin.getAccessManager().enabled(claim,"villager-player-damage")||!plugin.getAccessManager().allows(attacker,claim,"entities"))) {
                event.setCancelled(true);attacker.sendMessage(plugin.getMessageManager().getPrefixed(attacker,"protection-villager"));
            }
            return;
        }

        if (entity instanceof ItemFrame || entity instanceof ArmorStand || entity instanceof Animals
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
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void dispense(BlockDispenseEvent event) {
        if(!plugin.getConfigManager().values().getBoolean("protection.boundaries.block-cross-owner-dispensers",true))return;
        if(!(event.getBlock().getBlockData() instanceof Directional facing))return;
        ClaimData source=plugin.getClaimManager().getClaimAt(event.getBlock().getChunk());
        ClaimData destination=plugin.getClaimManager().getClaimAt(event.getBlock().getRelative(facing.getFacing()).getChunk());
        if(crosses(source,destination))event.setCancelled(true);
    }
    private void filterGrowth(Chunk source,Player player,java.util.List<BlockState> blocks) {
        if(!plugin.getConfigManager().values().getBoolean("protection.boundaries.block-external-growth",true))return;
        ClaimData from=plugin.getClaimManager().getClaimAt(source);
        blocks.removeIf(state->{
            ClaimData to=plugin.getClaimManager().getClaimAt(state.getBlock().getChunk());
            return to!=null&&(player!=null?!plugin.getAccessManager().allows(player,to,"build"):crosses(from,to));
        });
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void structureGrow(StructureGrowEvent event) { filterGrowth(event.getLocation().getChunk(),event.getPlayer(),event.getBlocks()); }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void fertilize(BlockFertilizeEvent event) { filterGrowth(event.getBlock().getChunk(),event.getPlayer(),event.getBlocks()); }
    private boolean blocksPvp(Player attacker, Player victim) {
        if(plugin.getAccessManager().bypass(attacker))return false;
        ClaimData from=plugin.getClaimManager().getClaimAt(attacker.getLocation().getChunk());
        ClaimData to=plugin.getClaimManager().getClaimAt(victim.getLocation().getChunk());
        return (from!=null&&!plugin.getAccessManager().enabled(from,"pvp"))
                ||(to!=null&&!plugin.getAccessManager().enabled(to,"pvp"));
    }
    private boolean harmful(java.util.Collection<org.bukkit.potion.PotionEffect> effects) {
        Set<String> harmful=Set.of("HARM","INSTANT_DAMAGE","POISON","WITHER","SLOW","SLOWNESS",
                "WEAKNESS","BLINDNESS","CONFUSION","NAUSEA","HUNGER","SLOW_DIGGING","MINING_FATIGUE",
                "LEVITATION","UNLUCK","BAD_OMEN","DARKNESS","INFESTED","OOZING","WEAVING","WIND_CHARGED");
        return effects.stream().anyMatch(effect->harmful.contains(effect.getType().getName()));
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void potionSplash(PotionSplashEvent event) {
        if(!(event.getPotion().getShooter() instanceof Player attacker)||!harmful(event.getPotion().getEffects()))return;
        for(LivingEntity entity:event.getAffectedEntities()) {
            if(entity instanceof Player victim&&attacker!=victim&&blocksPvp(attacker,victim))event.setIntensity(victim,0);
        }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void lingeringPotion(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud=event.getEntity();
        if(!(cloud.getSource() instanceof Player attacker))return;
        java.util.List<org.bukkit.potion.PotionEffect> effects=new java.util.ArrayList<>(cloud.getCustomEffects());
        if(cloud.getBasePotionType()!=null)effects.addAll(cloud.getBasePotionType().getPotionEffects());
        if(!harmful(effects))return;
        event.getAffectedEntities().removeIf(entity->entity instanceof Player victim&&attacker!=victim&&blocksPvp(attacker,victim));
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
