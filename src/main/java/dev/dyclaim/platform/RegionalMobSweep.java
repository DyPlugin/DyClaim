package dev.dyclaim.platform;

import dev.dyclaim.model.ClaimData;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** Bounded mob cleanup across region and entity owners. */
public final class RegionalMobSweep implements AutoCloseable {
    private final PlatformTasks tasks;
    private final Supplier<Collection<ClaimData>> claims;
    private final Function<String,ClaimData> current;
    private final Function<String,World> worlds;
    private final Predicate<ClaimData> allowed;
    private final Predicate<Entity> removable;
    private final IntSupplier budget;
    private final Set<Slot> pending=ConcurrentHashMap.newKeySet();
    private final Map<String,Integer> offsets=new HashMap<>();
    private final AtomicBoolean busy=new AtomicBoolean();
    private volatile boolean closed;
    private Iterator<ClaimData> rotation=Collections.emptyIterator();
    public RegionalMobSweep(PlatformTasks tasks,Supplier<Collection<ClaimData>> claims,Function<String,ClaimData> current,
                            Function<String,World> worlds,Predicate<ClaimData> allowed,Predicate<Entity> removable,IntSupplier budget) {
        this.tasks=tasks;this.claims=claims;this.current=current;this.worlds=worlds;
        this.allowed=allowed;this.removable=removable;this.budget=budget;
    }
    public synchronized void tick() {
        if(closed||!busy.compareAndSet(false,true))return;
        try {
            if(!rotation.hasNext()){
                List<ClaimData> available=new ArrayList<>(claims.get());
                Set<String> liveKeys=new HashSet<>();for(ClaimData claim:available)liveKeys.add(claim.getChunkKey());
                offsets.keySet().retainAll(liveKeys);rotation=available.iterator();
            }
            if(!rotation.hasNext()){busy.set(false);return;}
            ClaimData claim=rotation.next();World world=worlds.apply(claim.getWorld());
            if(world==null||!allowed.test(claim)){busy.set(false);return;}
            AtomicInteger remaining=new AtomicInteger();
            Slot slot=new Slot(remaining);
            try{slot.bind(tasks.regionLater(world,claim.getChunkX(),claim.getChunkZ(),()->{
                try{if(!closed&&current.apply(claim.getChunkKey())==claim&&allowed.test(claim))scan(claim,world,remaining);}
                finally{slot.finish();}
            },1));}catch(RuntimeException|Error e){slot.finish();throw e;}
        } catch(RuntimeException|Error e){if(pending.isEmpty())busy.set(false);throw e;}
    }
    private void scan(ClaimData claim,World world,AtomicInteger remaining) {
        if(!world.isChunkLoaded(claim.getChunkX(),claim.getChunkZ()))return;
        Entity[] entities=world.getChunkAt(claim.getChunkX(),claim.getChunkZ()).getEntities();
        if(entities.length==0)return;
        int start=offsets.getOrDefault(claim.getChunkKey(),0)%entities.length;
        int count=Math.min(entities.length,Math.max(1,budget.getAsInt()));
        offsets.put(claim.getChunkKey(),(start+count)%entities.length);
        for(int i=0;i<count&&!closed;i++) {
            Entity entity=entities[(start+i)%entities.length];Slot slot=new Slot(remaining);
            try{slot.bind(tasks.entityLater(entity,()->{
                try{
                    if(closed||current.apply(claim.getChunkKey())!=claim||!allowed.test(claim))return;
                    if(!entity.isValid()||!(entity instanceof Mob))return;
                    var location=entity.getLocation();
                    if(location.getWorld()!=world||(location.getBlockX()>>4)!=claim.getChunkX()||(location.getBlockZ()>>4)!=claim.getChunkZ())return;
                    if(removable.test(entity))entity.remove();
                }finally{slot.finish();}
            },slot::finish,1));}catch(RuntimeException|Error e){slot.finish();throw e;}
        }
    }
    @Override public synchronized void close() {
        closed=true;IllegalStateException failure=null;
        for(Slot slot:List.copyOf(pending))try{slot.cancel();}
        catch(RuntimeException e){if(failure==null)failure=new IllegalStateException("Could not cancel every mob cleanup task");failure.addSuppressed(e);}
        finally{slot.finish();}
        if(failure!=null)throw failure;
    }
    private final class Slot {
        private final AtomicInteger remaining;
        private final AtomicBoolean done=new AtomicBoolean();
        private volatile BukkitTask handle;
        Slot(AtomicInteger remaining){this.remaining=remaining;remaining.incrementAndGet();pending.add(this);}
        void bind(BukkitTask handle){this.handle=handle;if(closed||done.get())cancel();}
        void cancel(){BukkitTask task=handle;if(task!=null)task.cancel();}
        void finish(){if(done.compareAndSet(false,true)){pending.remove(this);if(remaining.decrementAndGet()==0)busy.set(false);}}
    }
}
