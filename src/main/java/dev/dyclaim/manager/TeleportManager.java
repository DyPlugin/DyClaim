package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import dev.dyclaim.model.ClaimData;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

public class TeleportManager implements Listener {
    private final DyClaim plugin;
    private final Map<UUID,BukkitTask> pending=new HashMap<>();
    private final Map<UUID,PotionEffect> effects=new HashMap<>();
    public TeleportManager(DyClaim plugin){this.plugin=plugin;}
    public static boolean safe(Location loc) {
        World w=loc.getWorld();if(w==null||loc.getY()<w.getMinHeight()+1||loc.getY()+1>=w.getMaxHeight()||!w.getWorldBorder().isInside(loc))return false;
        var feet=loc.getBlock();var head=feet.getRelative(0,1,0);var floor=feet.getRelative(0,-1,0);
        Set<Material> hazards=Set.of(Material.LAVA,Material.FIRE,Material.SOUL_FIRE,Material.CACTUS,Material.MAGMA_BLOCK,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.POWDER_SNOW,Material.SWEET_BERRY_BUSH);
        return feet.isPassable()&&head.isPassable()&&!feet.isLiquid()&&!head.isLiquid()&&floor.getType().isSolid()
                &&!hazards.contains(feet.getType())&&!hazards.contains(head.getType())&&!hazards.contains(floor.getType());
    }
    public Location destination(ClaimData c) {
        World w=Bukkit.getWorld(c.getWorld());if(w==null)return null;
        if(c.hasSpawn()){Location loc=new Location(w,c.getSpawnX(),c.getSpawnY(),c.getSpawnZ(),c.getSpawnYaw(),c.getSpawnPitch());if(safe(loc))return loc;}
        int minX=c.getChunkX()*16,minZ=c.getChunkZ()*16;
        for(int radius=0;radius<=8;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            int x=minX+8+dx,z=minZ+8+dz;if(x<minX||x>=minX+16||z<minZ||z>=minZ+16)continue;
            Location loc=new Location(w,x+0.5,w.getHighestBlockYAt(x,z)+1,z+0.5);if(safe(loc))return loc;
        }
        return null;
    }
    public void startTeleport(Player player,ClaimData claim) {
        if(pending.containsKey(player.getUniqueId())){send(player,"tp-already-pending");return;}
        if(destination(claim)==null){send(player,"tp-unsafe");return;}
        UUID uuid=player.getUniqueId(),id=claim.getId();String key=claim.getChunkKey();
        int warmup=plugin.getConfigManager().getTeleportWarmup();
        PotionEffect previous=player.getPotionEffect(PotionEffectType.BLINDNESS);if(previous!=null)effects.put(uuid,previous);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,warmup*20+20,0,false,false));
        player.sendMessage(plugin.getMessageManager().getPrefixed(player,"tp-warmup",Map.of("{seconds}",String.valueOf(warmup))));
        pending.put(uuid,Bukkit.getScheduler().runTaskLater(plugin,()->{
            pending.remove(uuid);restore(player);
            ClaimData current=plugin.getClaimManager().getByKey(key);
            if(!player.isOnline()||!player.hasPermission("dyclaim.teleport")||current==null||!current.getId().equals(id)||!plugin.getAccessManager().allows(player,current,"teleport")){send(player,"operation-changed");return;}
            Location target=destination(current);if(target==null){send(player,"tp-unsafe");return;}
            if(player.teleport(target))send(player,"tp-success");else send(player,"tp-cancelled");
        },Math.max(1,warmup*20L)));
    }
    public void startTeleport(Player player,Location ignored){send(player,"tp-unsafe");}
    private void send(Player p,String key){p.sendMessage(plugin.getMessageManager().getPrefixed(p,key));}
    private void restore(Player p){p.removePotionEffect(PotionEffectType.BLINDNESS);PotionEffect previous=effects.remove(p.getUniqueId());if(previous!=null)p.addPotionEffect(previous);}
    private void cancel(Player p,boolean notify){BukkitTask task=pending.remove(p.getUniqueId());if(task!=null){task.cancel();restore(p);if(notify)send(p,"tp-cancelled");}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void move(PlayerMoveEvent e){if(e.getTo()!=null&&(e.getFrom().getWorld()!=e.getTo().getWorld()||e.getFrom().getBlockX()!=e.getTo().getBlockX()||e.getFrom().getBlockY()!=e.getTo().getBlockY()||e.getFrom().getBlockZ()!=e.getTo().getBlockZ()))cancel(e.getPlayer(),true);}
    @EventHandler public void quit(PlayerQuitEvent e){cancel(e.getPlayer(),false);}
    public void close(){for(UUID uuid:new HashSet<>(pending.keySet())){Player p=Bukkit.getPlayer(uuid);if(p!=null)cancel(p,false);else pending.remove(uuid).cancel();}}
}
