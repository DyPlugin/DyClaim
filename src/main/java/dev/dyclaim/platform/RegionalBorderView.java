package dev.dyclaim.platform;
import dev.dyclaim.DyClaim;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Native region/entity ownership boundary for temporary border displays. */
public final class RegionalBorderView {
    private final DyClaim plugin;
    private final PlatformTasks tasks;
    private final Predicate<Player> bedrock;
    private final Map<UUID,View> views=new ConcurrentHashMap<>();
    private static final class View {
        final Player player;final UUID actor;final World world;final int chunkX,chunkZ;
        final Set<BukkitTask> handles=ConcurrentHashMap.newKeySet();
        volatile boolean closed;
        volatile Set<Location> sent=Set.of();
        View(Player player,Chunk chunk){this.player=player;actor=player.getUniqueId();world=chunk.getWorld();chunkX=chunk.getX();chunkZ=chunk.getZ();}
    }
    public RegionalBorderView(DyClaim plugin,PlatformTasks tasks,Predicate<Player> bedrock){this.plugin=plugin;this.tasks=tasks;this.bedrock=bedrock;}
    public boolean isViewing(UUID player){return views.containsKey(player);}
    private boolean active(View view){return !view.closed&&views.get(view.actor)==view;}
    private void track(View view,BukkitTask task){view.handles.add(task);if(view.closed)task.cancel();}
    public void show(Player player,Chunk chunk) {
        View view=new View(player,chunk);if(views.putIfAbsent(view.actor,view)!=null)return;
        FileConfiguration cfg=plugin.getConfigManager().values();int duration=cfg.getInt("visualization.duration-seconds");
        try{
            if(bedrock.test(player))showBlocks(view,cfg,duration);
            else showParticles(view,cfg,duration);
        }catch(RuntimeException|Error failure){finish(view);throw failure;}
    }
    private void showBlocks(View view,FileConfiguration cfg,int duration){
        BlockData data=Material.valueOf(cfg.getString("visualization.bedrock-block")).createBlockData();
        int y=Math.max(view.world.getMinHeight(),Math.min(view.player.getLocation().getBlockY(),view.world.getMaxHeight()-1));
        track(view,tasks.regionLater(view.world,view.chunkX,view.chunkZ,()->{
            if(!active(view))return;
            if(!view.world.isChunkLoaded(view.chunkX,view.chunkZ)){finish(view);return;}
            Set<Location> positions=new LinkedHashSet<>();int minX=view.chunkX*16,minZ=view.chunkZ*16;
            for(int i=0;i<16;i++){
                addAir(view.world,minX+i,y,minZ,positions);addAir(view.world,minX+i,y,minZ+15,positions);
                addAir(view.world,minX,y,minZ+i,positions);addAir(view.world,minX+15,y,minZ+i,positions);
            }
            if(!active(view))return;
            track(view,tasks.playerLater(view.player,()->{
                if(!active(view))return;
                if(!view.player.isOnline()||view.player.getWorld()!=view.world){finish(view);return;}
                view.sent=Set.copyOf(positions);
                for(Location location:positions){if(!active(view))return;view.player.sendBlockChange(location,data);}
                track(view,tasks.playerLater(view.player,()->finish(view),duration*20L));
            },1));
        },1));
    }
    private static void addAir(World world,int x,int y,int z,Set<Location> positions){if(world.getBlockAt(x,y,z).getType().isAir())positions.add(new Location(world,x,y,z));}
    private void showParticles(View view,FileConfiguration cfg,int duration){
        Particle chosen=Particle.valueOf(cfg.getString("visualization.particle"));
        Particle particle=chosen.getDataType()==Void.class||chosen.getDataType()==Particle.DustOptions.class?chosen:Particle.FLAME;
        long expires=System.currentTimeMillis()+duration*1000L;
        track(view,tasks.playerTimer(view.player,()->{
            if(!active(view))return;
            Player player=view.player;
            if(!player.isOnline()||player.getWorld()!=view.world||System.currentTimeMillis()>=expires){finish(view);return;}
            Location position=player.getLocation();int minX=view.chunkX*16,minZ=view.chunkZ*16;
            Location centre=new Location(view.world,minX+8,position.getY(),minZ+8);
            if(position.distanceSquared(centre)>Math.pow(cfg.getInt("visualization.max-distance"),2)){finish(view);return;}
            int y=Math.max(view.world.getMinHeight(),Math.min(position.getBlockY(),view.world.getMaxHeight()-2));
            int budget=Math.min(160,cfg.getInt("visualization.max-particles-per-run")),count=0;
            for(int height=0;height<2;height++)for(int i=0;i<=16;i++)for(int edge=0;edge<4;edge++){
                if(count++>=budget)return;
                double x=switch(edge){case 0,1->minX+i;case 2->minX;default->minX+16;};
                double z=switch(edge){case 0->minZ;case 1->minZ+16;default->minZ+i;};
                if(particle.getDataType()==Particle.DustOptions.class)player.spawnParticle(particle,x,y+height+0.5,z,1,0,0,0,0,new Particle.DustOptions(Color.ORANGE,1));
                else player.spawnParticle(particle,x,y+height+0.5,z,1,0,0,0,0);
            }
        },1,5));
    }
    public void hide(Player player){View view=views.get(player.getUniqueId());if(view!=null)finish(view);}
    private void finish(View view){
        if(!views.remove(view.actor,view))return;view.closed=true;
        for(BukkitTask handle:List.copyOf(view.handles))try{handle.cancel();}catch(RuntimeException failure){plugin.getLogger().warning("Border task cancellation failed: "+failure.getMessage());}
        view.handles.clear();restore(view);
    }
    private void restore(View view){
        if(view.sent.isEmpty())return;
        if(!plugin.isEnabled()){
            // Folia's shutdown thread owns the halted regions. Never infer ownership merely from disable.
            if(tasks.ownsRegion(view.world,view.chunkX,view.chunkZ)&&tasks.ownsEntity(view.player))sendRestoration(view,currentBlocks(view));
            else plugin.getLogger().warning("Temporary borders could not be restored after disable outside their owning region");
            return;
        }
        try{tasks.regionLater(view.world,view.chunkX,view.chunkZ,()->{
            Map<Location,BlockData> actual=currentBlocks(view);
            if(!actual.isEmpty())tasks.playerLater(view.player,()->sendRestoration(view,actual),1);
        },1);}catch(RuntimeException unavailable){plugin.getLogger().warning("Border restoration could not be scheduled: "+unavailable.getMessage());}
    }
    private Map<Location,BlockData> currentBlocks(View view){
        if(!view.world.isChunkLoaded(view.chunkX,view.chunkZ))return Map.of();
        Map<Location,BlockData> actual=new LinkedHashMap<>();
        for(Location location:view.sent)actual.put(location,view.world.getBlockAt(location.getBlockX(),location.getBlockY(),location.getBlockZ()).getBlockData());
        return actual;
    }
    private void sendRestoration(View view,Map<Location,BlockData> actual){
        if(!view.player.isOnline()||view.player.getWorld()!=view.world)return;
        View replacement=views.get(view.actor);
        for(var entry:actual.entrySet()){
            if(replacement!=null&&replacement.sent.contains(entry.getKey()))continue;
            view.player.sendBlockChange(entry.getKey(),entry.getValue());
        }
    }
    public void reset(){for(View view:List.copyOf(views.values()))finish(view);}
}
