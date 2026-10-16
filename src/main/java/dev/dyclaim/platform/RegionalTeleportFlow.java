package dev.dyclaim.platform;
import dev.dyclaim.DyClaim;
import dev.dyclaim.model.ClaimData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Coordinates target-region safety reads and player-owned warmup/transfer work. */
public final class RegionalTeleportFlow implements AutoCloseable {
    private final DyClaim plugin;
    private final PlatformTasks tasks;
    private final Function<ClaimData,Location> resolver;
    private final Map<UUID,Request> pending=new ConcurrentHashMap<>();
    private final Map<UUID,Request> effectOwners=new ConcurrentHashMap<>();
    private volatile boolean closed;
    private static final class Request {
        final Player player;final UUID actor,id;final String key;
        final Set<BukkitTask> handles=ConcurrentHashMap.newKeySet();
        PotionEffect previous;volatile boolean effect;Location destination;boolean transferring;
        Request(Player player,ClaimData claim){this.player=player;actor=player.getUniqueId();id=claim.getId();key=claim.getChunkKey();}
    }
    public RegionalTeleportFlow(DyClaim plugin,PlatformTasks tasks,Function<ClaimData,Location> resolver){this.plugin=plugin;this.tasks=tasks;this.resolver=resolver;}
    public void start(Player player,ClaimData claim){
        if(closed)return;
        Request request=new Request(player,claim);
        if(pending.putIfAbsent(request.actor,request)!=null){send(player,"tp-already-pending");return;}
        if(!authorized(request)){finish(request,"operation-changed");return;}
        resolve(request,true);
    }
    private boolean active(Request request){return !closed&&pending.get(request.actor)==request;}
    private ClaimData current(Request request){
        ClaimData claim=plugin.getClaimManager().getByKey(request.key);
        return claim!=null&&claim.getId().equals(request.id)&&claim.getChunkKey().equals(request.key)?claim:null;
    }
    /** Called only on the player owner; target callbacks never query player permissions/state. */
    private boolean authorized(Request request){
        ClaimData claim=current(request);
        return plugin.isEnabled()&&request.player.isOnline()&&request.player.hasPermission("dyclaim.teleport")&&claim!=null&&plugin.getAccessManager().allows(request.player,claim,"teleport");
    }
    private void track(Request request,BukkitTask handle){request.handles.add(handle);if(!active(request))handle.cancel();}
    private void resolve(Request request,boolean initial){
        ClaimData claim=current(request);
        if(claim==null){finish(request,"operation-changed");return;}
        ClaimData target=claim.snapshot();World world=Bukkit.getWorld(target.getWorld());
        if(world==null){finish(request,"tp-unsafe");return;}
        track(request,tasks.regionLater(world,target.getChunkX(),target.getChunkZ(),()->{
            if(!active(request))return;
            ClaimData latest=current(request);Location destination=null;
            if(latest!=null)try{destination=resolver.apply(latest.snapshot());}catch(RuntimeException failure){plugin.getLogger().warning("Teleport destination lookup failed for "+request.key+": "+failure.getMessage());}
            Location found=destination;
            track(request,tasks.playerLater(request.player,()->{
                if(!active(request))return;
                if(!authorized(request)){finish(request,"operation-changed");return;}
                if(found==null){finish(request,"tp-unsafe");return;}
                if(initial)warmup(request);else transfer(request,found);
            },1));
        },1));
    }
    private void warmup(Request request){
        Request previous=effectOwners.get(request.actor);if(previous!=null)restore(previous);
        Player player=request.player;int seconds=plugin.getConfigManager().getTeleportWarmup();
        request.previous=player.getPotionEffect(PotionEffectType.BLINDNESS);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,seconds*20+20,0,false,false));
        request.effect=true;effectOwners.put(request.actor,request);
        if(!active(request)){pending.remove(request.actor,request);restore(request);return;}
        player.sendMessage(plugin.getMessageManager().getPrefixed(player,"tp-warmup",Map.of("{seconds}",String.valueOf(seconds))));
        track(request,tasks.playerLater(player,()->{
            if(!active(request))return;
            restore(request);
            if(!authorized(request)){finish(request,"operation-changed");return;}
            resolve(request,false);
        },Math.max(1,seconds*20L)));
    }
    private void transfer(Request request,Location destination){
        request.destination=destination;request.transferring=true;
        // After native acceptance, cancellation guards our callbacks, not the native transfer itself.
        try{tasks.teleport(request.player,destination).whenComplete((success,failure)->{
            if(!active(request))return;
            try{track(request,tasks.playerLater(request.player,()->{
                if(!active(request))return;
                if(!authorized(request)){finish(request,"operation-changed");return;}
                if(failure!=null)plugin.getLogger().warning("Teleport failed for "+request.key+": "+failure.getMessage());
                finish(request,failure==null&&Boolean.TRUE.equals(success)?"tp-success":"tp-cancelled");
            },1));}catch(IllegalStateException unavailable){if(!closed)plugin.getLogger().warning("Teleport completion could not be scheduled: "+unavailable.getMessage());}
        });}catch(RuntimeException failure){plugin.getLogger().warning("Teleport could not start: "+failure.getMessage());finish(request,"tp-cancelled");}
    }
    private void stop(Request request){
        for(BukkitTask handle:Set.copyOf(request.handles))try{handle.cancel();}catch(RuntimeException failure){plugin.getLogger().warning("Teleport task cancellation failed: "+failure.getMessage());}
        request.handles.clear();
    }
    private void restore(Request request){
        if(!request.effect||!effectOwners.remove(request.actor,request))return;
        request.effect=false;
        if(!request.player.isOnline())return;
        request.player.removePotionEffect(PotionEffectType.BLINDNESS);if(request.previous!=null)request.player.addPotionEffect(request.previous);
    }
    private void finish(Request request,String message){
        if(!pending.remove(request.actor,request))return;
        stop(request);restore(request);if(request.player.isOnline())send(request.player,message);
    }
    private void send(Player player,String key){player.sendMessage(plugin.getMessageManager().getPrefixed(player,key));}
    public void cancel(Player player,boolean notify){
        UUID actor=player.getUniqueId();Request request=pending.remove(actor);
        if(request!=null){stop(request);restore(request);if(notify&&player.isOnline())send(player,"tp-cancelled");}
        Request effect=effectOwners.get(actor);if(effect!=null)restore(effect);
    }
    public boolean hasPending(UUID player){return pending.containsKey(player);}
    public boolean isClosed(){return closed;}
    public boolean expectedTransfer(Player player,Location destination){Request request=pending.get(player.getUniqueId());return request!=null&&request.transferring&&java.util.Objects.equals(request.destination,destination);}
    public void reset(){cancelAll();}
    @Override public void close(){
        closed=true;
        cancelAll();
    }
    private void cancelAll(){
        Set<Request> cleanup=new java.util.HashSet<>(pending.values());cleanup.addAll(effectOwners.values());
        for(Request request:cleanup){
            pending.remove(request.actor,request);
            stop(request);
            if(request.effect)try{
                if(!plugin.isEnabled()){
                    if(tasks.ownsEntity(request.player))restore(request);
                    else plugin.getLogger().warning("Teleport effect could not be restored after disable outside its owning region");
                }else tasks.playerLater(request.player,()->restore(request),1);
            }catch(RuntimeException unavailable){plugin.getLogger().warning("Teleport effect cleanup could not be scheduled: "+unavailable.getMessage());}
        }
    }
}
