package dev.dyclaim;

import dev.dyclaim.platform.PlatformTasks;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PlatformTasksTest {
    @Test void cancellingPlayerTaskPreventsAlreadyQueuedCallbackFromRunning() {
        DyClaim plugin=mock(DyClaim.class); Server server=mock(Server.class);
        BukkitScheduler scheduler=mock(BukkitScheduler.class); BukkitTask delegate=mock(BukkitTask.class);
        when(plugin.getServer()).thenReturn(server); when(server.getScheduler()).thenReturn(scheduler);
        AtomicReference<Runnable> queued=new AtomicReference<>();
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),eq(20L))).thenAnswer(i->{queued.set(i.getArgument(1));return delegate;});
        PlatformTasks tasks=PlatformTasks.bukkit(plugin); AtomicInteger calls=new AtomicInteger();
        BukkitTask task=tasks.playerLater(mock(Player.class),calls::incrementAndGet,20);
        task.cancel(); queued.get().run();
        assertEquals(0,calls.get());verify(delegate).cancel();
    }
    @Test void closeInvalidatesAllQueuedTasksAndRejectsNewTasks() {
        DyClaim plugin=mock(DyClaim.class);Server server=mock(Server.class);BukkitScheduler scheduler=mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);when(server.getScheduler()).thenReturn(scheduler);
        AtomicReference<Runnable> queued=new AtomicReference<>();
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),eq(1L))).thenAnswer(i->{queued.set(i.getArgument(1));return mock(BukkitTask.class);});
        PlatformTasks tasks=PlatformTasks.bukkit(plugin);AtomicInteger calls=new AtomicInteger();
        tasks.globalLater(calls::incrementAndGet,1);tasks.close();queued.get().run();
        assertEquals(0,calls.get());assertThrows(IllegalStateException.class,()->tasks.globalLater(calls::incrementAndGet,1));
    }
    @Test void bukkitFallbackPreservesTicksAndRepeatingInterval() {
        DyClaim plugin=mock(DyClaim.class);Server server=mock(Server.class);BukkitScheduler scheduler=mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),eq(4L),eq(9L))).thenReturn(mock(BukkitTask.class));
        PlatformTasks tasks=PlatformTasks.bukkit(plugin);
        assertNotNull(tasks.playerTimer(mock(Player.class),()->{},4,9));
        verify(scheduler).runTaskTimer(eq(plugin),any(Runnable.class),eq(4L),eq(9L));
    }
    @Test void closeAttemptsEveryCancellationEvenWhenSchedulersThrow() {
        DyClaim plugin=mock(DyClaim.class);Server server=mock(Server.class);BukkitScheduler scheduler=mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);when(server.getScheduler()).thenReturn(scheduler);
        BukkitTask first=mock(BukkitTask.class),second=mock(BukkitTask.class);
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),anyLong())).thenReturn(first,second);
        doThrow(new IllegalStateException("first cancellation failed")).when(first).cancel();
        doThrow(new IllegalStateException("second cancellation failed")).when(second).cancel();
        PlatformTasks tasks=PlatformTasks.bukkit(plugin);tasks.globalLater(()->{},1);tasks.globalLater(()->{},1);
        assertThrows(IllegalStateException.class,tasks::close);
        verify(first).cancel();verify(second).cancel();
    }
}
