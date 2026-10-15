package dev.dyclaim.platform;

import dev.dyclaim.DyClaim;
import dev.dyclaim.model.ClaimData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.function.BiPredicate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.scheduler.BukkitTask;

/** Native ejection coordinator; terrain and player access have different owners. */
public final class RegionalEjection {
    @FunctionalInterface public interface Destination {Location find(World world,int x,int z);}
    private final DyClaim plugin;
    private final PlatformTasks tasks;
    private final Destination destination;
    private final BiPredicate<Player,ClaimData> eligible;
    private final Map<UUID,Request> pending=new ConcurrentHashMap<>();
    private static final List<int[]> OFFSETS=offsets();
    private static final class Request {
        final Player player;final UUID actor;final ClaimData claim;final World world;
        final Set<BukkitTask> handles=ConcurrentHashMap.newKeySet();
        Request(Player player,ClaimData claim){this.player=player;actor=player.getUniqueId();this.claim=claim;world=player.getWorld();}
    }
    public RegionalEjection(DyClaim plugin,PlatformTasks tasks,Destination destination,BiPredicate<Player,ClaimData> eligible){
        this.plugin=plugin;this.tasks=tasks;this.destination=destination;this.eligible=eligible;
    }
    private static List<int[]> offsets(){
        List<int[]> values=new ArrayList<>();
        for(int r=1;r<=8;r++)for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)if(Math.abs(x)==r||Math.abs(z)==r)values.add(new int[]{x,z});
        return List.copyOf(values);
    }
    private boolean active(Request request){return plugin.isEnabled()&&pending.get(request.actor)==request;}
    // This check is always executed by the player's entity scheduler.
    private boolean valid(Request request){
        if(!active(request)||!request.player.isOnline()||request.player.getWorld()!=request.world)return false;
        Location at=request.player.getLocation();
        return plugin.getClaimManager().getByKey(request.claim.getChunkKey())==request.claim
            &&(at.getBlockX()>>4)==request.claim.getChunkX()&&(at.getBlockZ()>>4)==request.claim.getChunkZ()
            &&eligible.test(request.player,request.claim);
    }
    public void start(Player player,ClaimData claim){
        if(!plugin.isEnabled())return;
        Request request=new Request(player,claim);if(pending.putIfAbsent(request.actor,request)!=null)return;
        if(!valid(request)){finish(request);return;}scan(request,0);
    }
    private void track(Request request,BukkitTask task){request.handles.add(task);if(!active(request))task.cancel();}
    private void scan(Request request,int index){
        if(!active(request))return;
        if(index>=OFFSETS.size()){
            request.player.sendMessage(plugin.getMessageManager().getPrefixed(request.player,"entry-exit-unavailable"));finish(request);return;
        }
        int[] offset=OFFSETS.get(index);int x=request.claim.getChunkX()+offset[0],z=request.claim.getChunkZ()+offset[1];
        try{track(request,tasks.regionLater(request.world,x,z,()->{
            if(!active(request))return;
            Location found=null;
            try{found=destination.find(request.world,x,z);}catch(RuntimeException failure){plugin.getLogger().warning("Ejection destination lookup failed: "+failure.getMessage());}
            Location target=found;
            try{track(request,tasks.entityLater(request.player,()->{
                if(!valid(request)){finish(request);return;}
                if(target==null){scan(request,index+1);return;}
                if(plugin.getClaimManager().isChunkClaimed(request.world.getName(),x,z)){scan(request,index+1);return;}
                try{tasks.teleport(request.player,target).whenComplete((success,failure)->{
                    if(failure!=null)plugin.getLogger().warning("Ejection transfer failed: "+failure.getMessage());
                    finish(request);
                });}catch(RuntimeException failure){finish(request);plugin.getLogger().warning("Ejection transfer could not start: "+failure.getMessage());}
            },()->finish(request),1));}catch(RuntimeException failure){finish(request);plugin.getLogger().warning("Ejection player task unavailable: "+failure.getMessage());}
        },1));}catch(RuntimeException failure){finish(request);plugin.getLogger().warning("Ejection region task unavailable: "+failure.getMessage());}
    }
    private void finish(Request request){
        pending.remove(request.actor,request);
        for(BukkitTask task:request.handles)try{task.cancel();}catch(RuntimeException failure){plugin.getLogger().warning("Ejection task cancellation failed: "+failure.getMessage());}
        request.handles.clear();
    }
    public void reset(){for(Request request:List.copyOf(pending.values()))finish(request);}
}
