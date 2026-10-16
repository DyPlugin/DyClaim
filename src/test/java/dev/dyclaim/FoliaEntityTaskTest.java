package dev.dyclaim;

import dev.dyclaim.platform.PlatformTasks;
import io.papermc.paper.threadedregions.scheduler.*;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaEntityTaskTest {
    @Test void retiredMobReleasesReservationOnceAndNeverRunsQueuedAction() throws Exception {
        DyClaim plugin=mock(DyClaim.class);Entity mob=mock(Entity.class);
        EntityScheduler scheduler=mock(EntityScheduler.class);when(mob.getScheduler()).thenReturn(scheduler);
        AtomicReference<Consumer<ScheduledTask>> callback=new AtomicReference<>();
        AtomicReference<Runnable> retired=new AtomicReference<>();
        ScheduledTask nativeTask=mock(ScheduledTask.class);
        when(scheduler.runDelayed(eq(plugin),any(),any(),eq(1L))).thenAnswer(i->{callback.set(i.getArgument(1));retired.set(i.getArgument(2));return nativeTask;});
        AtomicInteger actions=new AtomicInteger(),releases=new AtomicInteger();
        var method=PlatformTasks.class.getMethod("entityLater",Entity.class,Runnable.class,Runnable.class,long.class);
        BukkitTask task=(BukkitTask)method.invoke(PlatformTasks.folia(plugin),mob,(Runnable)actions::incrementAndGet,(Runnable)releases::incrementAndGet,1L);
        retired.get().run();retired.get().run();callback.get().accept(nativeTask);
        assertTrue(task.isCancelled());assertEquals(0,actions.get());assertEquals(1,releases.get());
    }
    @Test void alreadyRetiredEntityReleasesReservationWithoutCallback() throws Exception {
        DyClaim plugin=mock(DyClaim.class);Entity mob=mock(Entity.class);
        when(mob.getScheduler()).thenReturn(mock(EntityScheduler.class));
        AtomicInteger released=new AtomicInteger();
        var method=PlatformTasks.class.getMethod("entityLater",Entity.class,Runnable.class,Runnable.class,long.class);
        BukkitTask task=(BukkitTask)method.invoke(PlatformTasks.folia(plugin),mob,(Runnable)()->fail("Retired entity ran"),(Runnable)released::incrementAndGet,1L);
        assertTrue(task.isCancelled());assertEquals(1,released.get());
    }
}
