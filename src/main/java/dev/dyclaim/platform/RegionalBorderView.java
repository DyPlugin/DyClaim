package dev.dyclaim.platform;
import dev.dyclaim.DyClaim;
import dev.dyclaim.visualizer.ClaimOutline;
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
        final Set<Location> sent=ConcurrentHashMap.newKeySet();
        /** Border lines shown to this player only; removed by the region that owns each one. */
        final Map<org.bukkit.entity.Entity,ClaimOutline.Cell> displays=new ConcurrentHashMap<>();
        List<ClaimOutline.Edge> edges;int particleOffset;
        View(Player player,Chunk chunk){this.player=player;actor=player.getUniqueId();world=chunk.getWorld();chunkX=chunk.getX();chunkZ=chunk.getZ();}
    }
    public RegionalBorderView(DyClaim plugin,PlatformTasks tasks,Predicate<Player> bedrock){this.plugin=plugin;this.tasks=tasks;this.bedrock=bedrock;}
    public boolean isViewing(UUID player){return views.containsKey(player);}
    private boolean active(View view){return !view.closed&&views.get(view.actor)==view;}
    private void track(View view,BukkitTask task){view.handles.add(task);if(view.closed)task.cancel();}
    public void show(Player player,Chunk chunk) {
        View view=new View(player,chunk);if(views.putIfAbsent(view.actor,view)!=null)return;
        FileConfiguration cfg=plugin.getConfigManager().values();int duration=cfg.getInt("visualization.duration-seconds");
        var origin=new ClaimOutline.Cell(view.chunkX,view.chunkZ);
        var claim=plugin.getClaimManager().getClaimAt(view.world.getName(),view.chunkX,view.chunkZ);
        if(claim==null)view.edges=ClaimOutline.single(origin);
        else {
            UUID owner=claim.getOwnerUUID();String worldName=view.world.getName();
            view.edges=ClaimOutline.connected(origin,Math.max(1,(cfg.getInt("visualization.max-distance")+15)/16),cell->{
                var neighbor=plugin.getClaimManager().getClaimAt(worldName,cell.x(),cell.z());return neighbor!=null&&owner.equals(neighbor.getOwnerUUID());
            });
        }
        try{
            if(bedrock.test(player))showBlocks(view,cfg,duration);
            else if("display".equalsIgnoreCase(cfg.getString("visualization.style","display"))&&DISPLAYS)showDisplays(view,cfg,duration);
            else showParticles(view,cfg,duration);
        }catch(RuntimeException|Error failure){finish(view);throw failure;}
    }
    private void showBlocks(View view,FileConfiguration cfg,int duration){
        BlockData data=Material.valueOf(cfg.getString("visualization.bedrock-block")).createBlockData();
        int y=Math.max(view.world.getMinHeight(),Math.min(view.player.getLocation().getBlockY(),view.world.getMaxHeight()-1));
        Map<ClaimOutline.Cell,Set<Location>> groups=new LinkedHashMap<>();
        int remaining=256;
        for(var edge:view.edges){
            int minX=edge.cell().x()*16,minZ=edge.cell().z()*16;
            var positions=groups.computeIfAbsent(edge.cell(),key->new LinkedHashSet<>());
            for(int i=0;i<16&&remaining>0;i++){
                int x=switch(edge.side()){case NORTH,SOUTH->minX+i;case WEST->minX;case EAST->minX+15;};
                int z=switch(edge.side()){case NORTH->minZ;case SOUTH->minZ+15;case WEST,EAST->minZ+i;};
                if(positions.add(new Location(view.world,x,y,z)))remaining--;
            }
            if(remaining==0)break;
        }
        for(var group:groups.entrySet())track(view,tasks.regionLater(view.world,group.getKey().x(),group.getKey().z(),()->{
            if(!active(view)||!view.world.isChunkLoaded(group.getKey().x(),group.getKey().z()))return;
            Set<Location> positions=new LinkedHashSet<>();
            for(Location l:group.getValue())addAir(view.world,l.getBlockX(),y,l.getBlockZ(),positions);
            if(!active(view))return;
            track(view,tasks.playerLater(view.player,()->{
                if(!active(view))return;
                if(!view.player.isOnline()||view.player.getWorld()!=view.world){finish(view);return;}
                Location current=view.player.getLocation();
                double radiusSquared=Math.pow(cfg.getInt("visualization.max-distance"),2);
                for(Location location:positions){
                    if(!active(view))return;
                    if(Math.pow(location.getX()-current.getX(),2)+Math.pow(location.getZ()-current.getZ(),2)>radiusSquared)continue;
                    view.sent.add(location);view.player.sendBlockChange(location,data);
                }
            },1));
        },1));
        track(view,tasks.playerLater(view.player,()->finish(view),duration*20L));
    }
    private static final boolean DISPLAYS=displaysAvailable();
    private static boolean displaysAvailable(){
        try{Class.forName("org.bukkit.entity.BlockDisplay");org.bukkit.entity.Entity.class.getMethod("setVisibleByDefault",boolean.class);return true;}
        catch(ClassNotFoundException|NoSuchMethodException|LinkageError missing){return false;}
    }
    /**
     * Glowing lines along the claim edges, one block display per chunk side. They are spawned invisible, shown only to
     * the viewer, never saved with the world and removed again by the region that owns them.
     */
    private void showDisplays(View view,FileConfiguration cfg,int duration){
        BlockData block=Material.valueOf(cfg.getString("visualization.display-block","LIME_STAINED_GLASS")).createBlockData();
        Color glow=Color.fromRGB(Integer.parseInt(cfg.getString("visualization.display-glow","#55FF55").substring(1),16));
        int y=Math.max(view.world.getMinHeight(),Math.min(view.player.getLocation().getBlockY(),view.world.getMaxHeight()-2));
        Map<ClaimOutline.Cell,List<ClaimOutline.Edge>> byCell=new LinkedHashMap<>();
        int remaining=96;
        for(var edge:view.edges){if(remaining--<=0)break;byCell.computeIfAbsent(edge.cell(),key->new ArrayList<>()).add(edge);}
        for(var group:byCell.entrySet())track(view,tasks.regionLater(view.world,group.getKey().x(),group.getKey().z(),()->{
            if(!active(view)||!view.world.isChunkLoaded(group.getKey().x(),group.getKey().z()))return;
            List<org.bukkit.entity.Entity> spawned=new ArrayList<>();
            for(var edge:group.getValue()){
                int minX=edge.cell().x()*16,minZ=edge.cell().z()*16;
                boolean alongX=edge.side()==ClaimOutline.Side.NORTH||edge.side()==ClaimOutline.Side.SOUTH;
                double x=switch(edge.side()){case EAST->minX+16-0.04;default->minX;};
                double z=switch(edge.side()){case SOUTH->minZ+16-0.04;default->minZ;};
                Location at=new Location(view.world,x,y+0.05,z);
                org.bukkit.entity.BlockDisplay display=view.world.spawn(at,org.bukkit.entity.BlockDisplay.class,entity->{
                    entity.setVisibleByDefault(false);entity.setPersistent(false);entity.setBlock(block);
                    entity.setGlowing(true);entity.setGlowColorOverride(glow);
                    entity.setBrightness(new org.bukkit.entity.Display.Brightness(15,15));
                    entity.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(),new org.joml.Quaternionf(),
                            alongX?new org.joml.Vector3f(16f,0.08f,0.04f):new org.joml.Vector3f(0.04f,0.08f,16f),new org.joml.Quaternionf()));
                });
                view.displays.put(display,edge.cell());spawned.add(display);
            }
            if(!active(view)){removeDisplays(view);return;}
            track(view,tasks.playerLater(view.player,()->{
                if(!active(view))return;
                if(!view.player.isOnline()||view.player.getWorld()!=view.world){finish(view);return;}
                for(var entity:spawned)if(entity.isValid())view.player.showEntity(plugin,entity);
            },1));
        },1));
        track(view,tasks.playerLater(view.player,()->finish(view),duration*20L));
    }
    /** Each display is removed on the thread of the region it stands in; on shutdown only where that thread is this one. */
    private void removeDisplays(View view){
        Map<ClaimOutline.Cell,List<org.bukkit.entity.Entity>> byCell=new HashMap<>();
        for(var entry:view.displays.entrySet())byCell.computeIfAbsent(entry.getValue(),key->new ArrayList<>()).add(entry.getKey());
        view.displays.clear();
        for(var group:byCell.entrySet()){
            if(!plugin.isEnabled()||tasks.ownsRegion(view.world,group.getKey().x(),group.getKey().z())){
                if(tasks.ownsRegion(view.world,group.getKey().x(),group.getKey().z()))group.getValue().forEach(org.bukkit.entity.Entity::remove);
                continue; // Not saved with the world, so a display that cannot be reached here disappears on unload.
            }
            try{tasks.regionLater(view.world,group.getKey().x(),group.getKey().z(),()->group.getValue().forEach(org.bukkit.entity.Entity::remove),1);}
            catch(RuntimeException unavailable){plugin.getLogger().warning("Border displays could not be removed: "+unavailable.getMessage());}
        }
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
            int total=view.edges.size()*34;
            int budget=Math.min(Math.min(160,cfg.getInt("visualization.max-particles-per-run")),total);
            for(int count=0;count<budget;count++){
                int point=Math.floorMod(view.particleOffset++,total);var edge=view.edges.get(point/34);
                int i=(point%34)/2,height=point%2;
                double x=switch(edge.side()){case NORTH,SOUTH->edge.cell().x()*16+i;case WEST->edge.cell().x()*16;case EAST->edge.cell().x()*16+16;};
                double z=switch(edge.side()){case NORTH->edge.cell().z()*16;case SOUTH->edge.cell().z()*16+16;case WEST,EAST->edge.cell().z()*16+i;};
                if(position.distanceSquared(new Location(view.world,x,position.getY(),z))>Math.pow(cfg.getInt("visualization.max-distance"),2))continue;
                if(particle.getDataType()==Particle.DustOptions.class)player.spawnParticle(particle,x,y+height+0.5,z,1,0,0,0,0,new Particle.DustOptions(Color.ORANGE,1));
                else player.spawnParticle(particle,x,y+height+0.5,z,1,0,0,0,0);
            }
        },1,5));
    }
    public void hide(Player player){View view=views.get(player.getUniqueId());if(view!=null)finish(view);}
    private void finish(View view){
        if(!views.remove(view.actor,view))return;view.closed=true;
        for(BukkitTask handle:List.copyOf(view.handles))try{handle.cancel();}catch(RuntimeException failure){plugin.getLogger().warning("Border task cancellation failed: "+failure.getMessage());}
        view.handles.clear();restore(view);removeDisplays(view);
    }
    private void restore(View view){
        if(view.sent.isEmpty())return;
        Set<ClaimOutline.Cell> cells=new HashSet<>();
        for(Location l:view.sent)cells.add(new ClaimOutline.Cell(Math.floorDiv(l.getBlockX(),16),Math.floorDiv(l.getBlockZ(),16)));
        for(var cell:cells){
            if(!plugin.isEnabled()){
                if(tasks.ownsRegion(view.world,cell.x(),cell.z())&&tasks.ownsEntity(view.player))sendRestoration(view,currentBlocks(view,cell));
                else plugin.getLogger().warning("Temporary borders could not be restored after disable outside their owning region");
                continue;
            }
            try{tasks.regionLater(view.world,cell.x(),cell.z(),()->{
                Map<Location,BlockData> actual=currentBlocks(view,cell);
                if(!actual.isEmpty())tasks.playerLater(view.player,()->sendRestoration(view,actual),1);
            },1);}catch(RuntimeException unavailable){plugin.getLogger().warning("Border restoration could not be scheduled: "+unavailable.getMessage());}
        }
    }
    private Map<Location,BlockData> currentBlocks(View view,ClaimOutline.Cell cell){
        if(!view.world.isChunkLoaded(cell.x(),cell.z()))return Map.of();
        Map<Location,BlockData> actual=new LinkedHashMap<>();
        for(Location location:view.sent)if(Math.floorDiv(location.getBlockX(),16)==cell.x()&&Math.floorDiv(location.getBlockZ(),16)==cell.z())
            actual.put(location,view.world.getBlockAt(location.getBlockX(),location.getBlockY(),location.getBlockZ()).getBlockData());
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
