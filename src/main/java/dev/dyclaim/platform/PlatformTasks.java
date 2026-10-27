package dev.dyclaim.platform;

import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Scheduling boundary; the Bukkit implementation preserves existing tick semantics. */
public final class PlatformTasks implements AutoCloseable {
    private final Plugin plugin;
    private final boolean regionThreaded;
    private final Set<GuardedTask> active=ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    private PlatformTasks(Plugin plugin,boolean regionThreaded){this.plugin=plugin;this.regionThreaded=regionThreaded;}
    public static PlatformTasks bukkit(Plugin plugin){return new PlatformTasks(plugin,false);}
    public static PlatformTasks folia(Plugin plugin){return new PlatformTasks(plugin,true);}
    /** One JAR for every server: Folia's region scheduling is chosen when the server is Folia, Bukkit scheduling otherwise. */
    public static PlatformTasks distribution(Plugin plugin){return foliaServer()?folia(plugin):bukkit(plugin);}
    static boolean foliaServer(){
        try{Class.forName("io.papermc.paper.threadedregions.RegionizedServer",false,PlatformTasks.class.getClassLoader());return true;}
        catch(ClassNotFoundException|LinkageError missing){return false;}
    }
    public boolean isRegionThreaded(){return regionThreaded;}
    public boolean ownsEntity(Entity entity){return !regionThreaded||ownership(new Class<?>[]{Entity.class},entity);}
    public boolean ownsRegion(World world,int chunkX,int chunkZ){return !regionThreaded||ownership(new Class<?>[]{World.class,int.class,int.class},world,chunkX,chunkZ);}
    private boolean ownership(Class<?>[] types,Object... args){
        try{return Boolean.TRUE.equals(org.bukkit.Server.class.getMethod("isOwnedByCurrentRegion",types).invoke(plugin.getServer(),args));}
        catch(ReflectiveOperationException e){throw failure(e);}
    }
    @SuppressWarnings("unchecked") public java.util.concurrent.CompletableFuture<Boolean> teleport(Player player,org.bukkit.Location destination){
        if(closed)return java.util.concurrent.CompletableFuture.completedFuture(false);
        if(regionThreaded)try{return (java.util.concurrent.CompletableFuture<Boolean>)Entity.class.getMethod("teleportAsync",org.bukkit.Location.class).invoke(player,destination);}
        catch(ReflectiveOperationException e){throw failure(e);}
        return java.util.concurrent.CompletableFuture.completedFuture(player.teleport(destination));
    }
    public synchronized BukkitTask globalTimer(Runnable action,long delay,long period){
        if(period<1)throw new IllegalArgumentException("Repeating period must be positive");
        GuardedTask task=create(action,true);try{
            if(regionThreaded)task.bindNative(invoke(global(),"GlobalRegionScheduler","runAtFixedRate",new Class<?>[]{Plugin.class,java.util.function.Consumer.class,long.class,long.class},plugin,callback(task),Math.max(1,delay),period));
            else task.bind(plugin.getServer().getScheduler().runTaskTimer(plugin,task,delay,period));return task;
        }catch(RuntimeException e){task.cancel();throw e;}
    }
    public synchronized BukkitTask async(Runnable action){
        GuardedTask task=create(action,false);task.asynchronous=true;try{
            if(regionThreaded)task.bindNative(invoke(getter(plugin.getServer(),org.bukkit.Server.class,"getAsyncScheduler"),"AsyncScheduler","runNow",new Class<?>[]{Plugin.class,java.util.function.Consumer.class},plugin,callback(task)));
            else task.bind(plugin.getServer().getScheduler().runTaskAsynchronously(plugin,task));return task;
        }catch(RuntimeException e){task.cancel();throw e;}
    }
    public synchronized BukkitTask globalNow(Runnable action){
        if(regionThreaded)return globalLater(action,1);
        GuardedTask task=create(action,false);try{task.bind(plugin.getServer().getScheduler().runTask(plugin,task));return task;}catch(RuntimeException e){task.cancel();throw e;}
    }
    public synchronized BukkitTask globalLater(Runnable action,long delay){
        GuardedTask task=create(action,false);try{
            if(regionThreaded)task.bindNative(invoke(global(),"GlobalRegionScheduler","runDelayed",new Class<?>[]{Plugin.class,java.util.function.Consumer.class,long.class},plugin,callback(task),Math.max(1,delay)));
            else task.bind(plugin.getServer().getScheduler().runTaskLater(plugin,task,delay));return task;
        }catch(RuntimeException e){task.cancel();throw e;}
    }
    public synchronized BukkitTask playerLater(Player player,Runnable action,long delay){
        return entityLater(player,action,()->{},delay);
    }
    /** Retirement runs without entity access and may only release application bookkeeping. */
    public synchronized BukkitTask entityLater(Entity entity,Runnable action,Runnable retired,long delay){
        if(!regionThreaded)return globalLater(action,delay);
        GuardedTask task=create(action,false);AtomicBoolean released=new AtomicBoolean();
        Runnable retirement=()->{try{task.cancel();}finally{if(released.compareAndSet(false,true))retired.run();}};
        try{
            Object nativeTask=invoke(entity(entity),"EntityScheduler","runDelayed",new Class<?>[]{Plugin.class,java.util.function.Consumer.class,Runnable.class,long.class},plugin,callback(task),retirement,Math.max(1,delay));
            task.bindNative(nativeTask);if(nativeTask==null)retirement.run();return task;
        }catch(RuntimeException e){retirement.run();throw e;}
    }
    public synchronized BukkitTask playerTimer(Player player,Runnable action,long delay,long period){
        if(period<1)throw new IllegalArgumentException("Repeating period must be positive");
        GuardedTask task=create(action,true);try{
            if(regionThreaded)task.bindNative(invoke(entity(player),"EntityScheduler","runAtFixedRate",new Class<?>[]{Plugin.class,java.util.function.Consumer.class,Runnable.class,long.class,long.class},plugin,callback(task),(Runnable)task::cancel,Math.max(1,delay),period));
            else task.bind(plugin.getServer().getScheduler().runTaskTimer(plugin,task,delay,period));return task;
        }catch(RuntimeException e){task.cancel();throw e;}
    }
    private Object global(){return getter(plugin.getServer(),org.bukkit.Server.class,"getGlobalRegionScheduler");}
    public synchronized BukkitTask regionLater(World world,int chunkX,int chunkZ,Runnable action,long delay){
        if(!regionThreaded)return globalLater(action,delay);
        GuardedTask task=create(action,false);try{
            Object scheduler=getter(plugin.getServer(),org.bukkit.Server.class,"getRegionScheduler");
            task.bindNative(invoke(scheduler,"RegionScheduler","runDelayed",new Class<?>[]{Plugin.class,World.class,int.class,int.class,java.util.function.Consumer.class,long.class},plugin,world,chunkX,chunkZ,callback(task),Math.max(1,delay)));return task;
        }catch(RuntimeException e){task.cancel();throw e;}
    }
    private Object entity(Entity entity){return getter(entity,Entity.class,"getScheduler");}
    private static Object getter(Object receiver,Class<?> api,String name){try{return api.getMethod(name).invoke(receiver);}catch(ReflectiveOperationException e){throw failure(e);}}
    private Object invoke(Object receiver,String api,String method,Class<?>[] types,Object... args){try{return Class.forName("io.papermc.paper.threadedregions.scheduler."+api).getMethod(method,types).invoke(receiver,args);}catch(ReflectiveOperationException e){throw failure(e);}}
    private static IllegalStateException failure(ReflectiveOperationException e){Throwable cause=e instanceof java.lang.reflect.InvocationTargetException invocation?invocation.getCause():e;return new IllegalStateException("Required platform scheduler failed",cause);}
    private java.util.function.Consumer<Object> callback(GuardedTask task){return ignored->task.run();}
    private GuardedTask create(Runnable action,boolean repeating){if(closed)throw new IllegalStateException("DyClaim scheduling is closed");GuardedTask task=new GuardedTask(action,repeating);active.add(task);return task;}
    @Override public synchronized void close(){
        closed=true;IllegalStateException failure=null;
        for(GuardedTask task:Set.copyOf(active))try{task.cancel();}catch(RuntimeException e){if(failure==null)failure=new IllegalStateException("Could not cancel every platform task");failure.addSuppressed(e);}
        if(failure!=null)throw failure;
    }
    private final class GuardedTask implements BukkitTask,Runnable {
        private final Runnable action;
        private final boolean repeating;
        private final AtomicBoolean cancelled=new AtomicBoolean();
        private volatile BukkitTask delegate;
        private volatile Object nativeDelegate;
        private boolean asynchronous;
        GuardedTask(Runnable action,boolean repeating){this.action=java.util.Objects.requireNonNull(action);this.repeating=repeating;}
        void bind(BukkitTask task){delegate=task;if(cancelled.get()&&task!=null)task.cancel();}
        void bindNative(Object task){nativeDelegate=task;if(task==null){cancel();return;}if(cancelled.get())cancelNative(task);}
        private void cancelNative(Object task){invoke(task,"ScheduledTask","cancel",new Class<?>[0]);}
        @Override public void run(){if(closed||cancelled.get())return;try{action.run();}catch(RuntimeException|Error e){cancel();throw e;}finally{if(!repeating)active.remove(this);}}
        @Override public void cancel(){if(!cancelled.compareAndSet(false,true))return;active.remove(this);BukkitTask task=delegate;if(task!=null)task.cancel();Object nativeTask=nativeDelegate;if(nativeTask!=null)cancelNative(nativeTask);}
        @Override public boolean isCancelled(){return cancelled.get();}
        @Override public int getTaskId(){BukkitTask task=delegate;return task==null?-1:task.getTaskId();}
        @Override public Plugin getOwner(){return plugin;}
        @Override public boolean isSync(){return !asynchronous;}
    }
}
