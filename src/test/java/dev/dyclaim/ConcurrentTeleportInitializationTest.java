package dev.dyclaim;
import dev.dyclaim.platform.PlatformTasks;
import dev.dyclaim.manager.TeleportManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConcurrentTeleportInitializationTest extends ManagerFixture {
    @Test void simultaneousFirstRequestsRemainOwnedByTheSameLifecycleManager() throws Exception {
        var first=player("First",chunk(0,0));var second=player("Second",chunk(128,0));
        assertTrue(claims.claimChunk(first,chunk(0,0)));assertTrue(claims.claimChunk(second,chunk(128,0)));
        var firstClaim=claims.getByKey("world:0:0");var secondClaim=claims.getByKey("world:128:0");
        var tasks=mock(PlatformTasks.class);when(tasks.isRegionThreaded()).thenReturn(true);when(plugin.isEnabled()).thenReturn(true);
        var firstHandle=mock(BukkitTask.class);var secondHandle=mock(BukkitTask.class);
        var firstConstructing=new CountDownLatch(1);var secondConstructing=new CountDownLatch(1);var firstScheduled=new CountDownLatch(1);
        ThreadLocal<Integer> lookups=ThreadLocal.withInitial(()->0);
        when(plugin.getPlatformTasks()).thenAnswer(i->{
            int count=lookups.get()+1;lookups.set(count);
            if(count==2){
                if(Thread.currentThread().getName().equals("first-request")){firstConstructing.countDown();secondConstructing.await(400,TimeUnit.MILLISECONDS);}
                else{secondConstructing.countDown();assertTrue(firstScheduled.await(5,TimeUnit.SECONDS));}
            }
            return tasks;
        });
        when(tasks.regionLater(eq(world),anyInt(),anyInt(),any(),anyLong())).thenAnswer(i->{
            if(Thread.currentThread().getName().equals("first-request")){firstScheduled.countDown();return firstHandle;}return secondHandle;
        });
        TeleportManager manager=new TeleportManager(plugin);
        ExecutorService a=Executors.newSingleThreadExecutor(r->new Thread(r,"first-request")),b=Executors.newSingleThreadExecutor(r->new Thread(r,"second-request"));
        try{
            Future<?> one=a.submit(()->{try(var local=mockStatic(Bukkit.class)){local.when(()->Bukkit.getWorld("world")).thenReturn(world);manager.startTeleport(first,firstClaim);}});
            assertTrue(firstConstructing.await(5,TimeUnit.SECONDS));
            Future<?> two=b.submit(()->{try(var local=mockStatic(Bukkit.class)){local.when(()->Bukkit.getWorld("world")).thenReturn(world);manager.startTeleport(second,secondClaim);}});
            one.get(8,TimeUnit.SECONDS);two.get(8,TimeUnit.SECONDS);manager.close();
            verify(firstHandle).cancel();verify(secondHandle).cancel();
        }finally{a.shutdownNow();b.shutdownNow();}
    }
}
