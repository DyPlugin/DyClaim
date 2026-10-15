package dev.dyclaim;

import dev.dyclaim.platform.PlatformTasks;
import io.papermc.paper.threadedregions.scheduler.*;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaBackgroundTasksTest {
    @Test void recurringGlobalWorkUsesNativeGlobalScheduler() {
        DyClaim plugin=mock(DyClaim.class);Server server=mock(Server.class);when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
        GlobalRegionScheduler global=mock(GlobalRegionScheduler.class);when(server.getGlobalRegionScheduler()).thenReturn(global);
        ScheduledTask nativeTask=mock(ScheduledTask.class);AtomicReference<Consumer<ScheduledTask>> callback=new AtomicReference<>();
        when(global.runAtFixedRate(eq(plugin),any(),eq(2L),eq(20L))).thenAnswer(i->{callback.set(i.getArgument(1));return nativeTask;});
        var task=PlatformTasks.folia(plugin).globalTimer(()->{},2,20);
        assertNotNull(callback.get());task.cancel();verify(nativeTask).cancel();verify(server,never()).getScheduler();
    }
    @Test void asyncWorkUsesNativeAsyncSchedulerAndIsReportedAsync() {
        DyClaim plugin=mock(DyClaim.class);Server server=mock(Server.class);when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
        AsyncScheduler async=mock(AsyncScheduler.class);when(server.getAsyncScheduler()).thenReturn(async);
        ScheduledTask nativeTask=mock(ScheduledTask.class);AtomicReference<Consumer<ScheduledTask>> callback=new AtomicReference<>();
        when(async.runNow(eq(plugin),any())).thenAnswer(i->{callback.set(i.getArgument(1));return nativeTask;});
        var task=PlatformTasks.folia(plugin).async(()->{});
        assertNotNull(callback.get());assertFalse(task.isSync());task.cancel();verify(nativeTask).cancel();verify(server,never()).getScheduler();
    }
}
