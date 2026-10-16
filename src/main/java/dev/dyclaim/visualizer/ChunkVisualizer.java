package dev.dyclaim.visualizer;

import dev.dyclaim.DyClaim;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

public class ChunkVisualizer {
    private final DyClaim plugin;
    private final Map<UUID,BukkitTask> tasks=new HashMap<>();
    private final Map<UUID,List<Location>> fake=new HashMap<>();
    public ChunkVisualizer(DyClaim plugin){this.plugin=plugin;}
    public boolean isViewing(UUID uuid){return tasks.containsKey(uuid);}
    public void showChunkBorders(Player p){showChunkBorders(p,p.getLocation().getChunk());}
    public void showChunkBorders(Player p,Chunk chunk){
        if(isViewing(p.getUniqueId()))return;
        var cfg=plugin.getConfigManager().values();int duration=cfg.getInt("visualization.duration-seconds");
        int minX=chunk.getX()*16,minZ=chunk.getZ()*16;
        if(bedrock(p)) {
            Material material=Material.valueOf(cfg.getString("visualization.bedrock-block"));BlockData data=material.createBlockData();List<Location> locations=new ArrayList<>();
            int y=Math.max(chunk.getWorld().getMinHeight(),Math.min(p.getLocation().getBlockY(),chunk.getWorld().getMaxHeight()-1));
            for(int i=0;i<16;i++){fake(p,new Location(chunk.getWorld(),minX+i,y,minZ),data,locations);fake(p,new Location(chunk.getWorld(),minX+i,y,minZ+15),data,locations);fake(p,new Location(chunk.getWorld(),minX,y,minZ+i),data,locations);fake(p,new Location(chunk.getWorld(),minX+15,y,minZ+i),data,locations);}
            fake.put(p.getUniqueId(),locations);tasks.put(p.getUniqueId(),Bukkit.getScheduler().runTaskLater(plugin,()->cleanupPlayer(p),duration*20L));
        }else{
            Particle chosen=Particle.valueOf(cfg.getString("visualization.particle"));
            Particle particle=chosen.getDataType()==Void.class||chosen.getDataType()==Particle.DustOptions.class?chosen:Particle.FLAME;
            long expires=System.currentTimeMillis()+duration*1000L;
            tasks.put(p.getUniqueId(),Bukkit.getScheduler().runTaskTimer(plugin,()->{
                if(!p.isOnline()||p.getWorld()!=chunk.getWorld()||System.currentTimeMillis()>=expires){cleanupPlayer(p);return;}
                Location centre=new Location(chunk.getWorld(),minX+8,p.getLocation().getY(),minZ+8);
                if(p.getLocation().distanceSquared(centre)>Math.pow(cfg.getInt("visualization.max-distance"),2)){cleanupPlayer(p);return;}
                int y=Math.max(chunk.getWorld().getMinHeight(),Math.min(p.getLocation().getBlockY(),chunk.getWorld().getMaxHeight()-2));
                int budget=Math.min(160,cfg.getInt("visualization.max-particles-per-run")),count=0;
                for(int height=0;height<2;height++)for(int i=0;i<=16;i++)for(int edge=0;edge<4;edge++) {
                    if(count++>=budget)return;
                    double x=switch(edge){case 0,1->minX+i;case 2->minX;default->minX+16;};
                    double z=switch(edge){case 0->minZ;case 1->minZ+16;default->minZ+i;};
                    if(particle.getDataType()==Particle.DustOptions.class)p.spawnParticle(particle,x,y+height+0.5,z,1,0,0,0,0,new Particle.DustOptions(Color.ORANGE,1));
                    else p.spawnParticle(particle,x,y+height+0.5,z,1,0,0,0,0);
                }
            },1,5));
        }
    }
    private void fake(Player p,Location loc,BlockData data,List<Location> list){if(loc.getBlock().getType().isAir()&&!list.contains(loc)){p.sendBlockChange(loc,data);list.add(loc);}}
    public void cleanupPlayer(Player p){BukkitTask task=tasks.remove(p.getUniqueId());if(task!=null)task.cancel();List<Location> locations=fake.remove(p.getUniqueId());if(locations!=null&&p.isOnline())for(Location loc:locations)if(loc.getWorld()==p.getWorld())p.sendBlockChange(loc,loc.getBlock().getBlockData());}
    public void cleanupAll(){for(UUID uuid:new HashSet<>(tasks.keySet())){Player p=Bukkit.getPlayer(uuid);if(p!=null)cleanupPlayer(p);else{tasks.remove(uuid).cancel();fake.remove(uuid);}}}
    private boolean bedrock(Player p){try{Class<?> api=Class.forName("org.geysermc.floodgate.api.FloodgateApi");Object instance=api.getMethod("getInstance").invoke(null);return (boolean)api.getMethod("isFloodgatePlayer",UUID.class).invoke(instance,p.getUniqueId());}catch(ReflectiveOperationException|LinkageError ex){return false;}}
}
