package dev.dyclaim;

import dev.dyclaim.platform.PlatformTasks;
import io.papermc.paper.threadedregions.scheduler.*;
import org.bukkit.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RegionTaskRoutingTest {
    @Test void negativeChunkCoordinatesArePassedToRegionSchedulerUnchanged() {
        DyClaim plugin=mock(DyClaim.class);Server server=mock(Server.class);World world=mock(World.class);
        RegionScheduler region=mock(RegionScheduler.class);ScheduledTask nativeTask=mock(ScheduledTask.class);
        when(plugin.getServer()).thenReturn(server);when(server.getRegionScheduler()).thenReturn(region);
        GlobalRegionScheduler wrong=mock(GlobalRegionScheduler.class);when(server.getGlobalRegionScheduler()).thenReturn(wrong);
        when(wrong.runDelayed(eq(plugin),any(),anyLong())).thenReturn(nativeTask);
        AtomicReference<Consumer<ScheduledTask>> action=new AtomicReference<>();
        when(region.runDelayed(eq(plugin),eq(world),eq(-2),eq(-7),any(),eq(1L))).thenAnswer(i->{action.set(i.getArgument(4));return nativeTask;});
        AtomicInteger calls=new AtomicInteger();PlatformTasks.folia(plugin).regionLater(world,-2,-7,calls::incrementAndGet,1);
        assertNotNull(action.get());action.get().accept(nativeTask);assertEquals(1,calls.get());verify(server,never()).getScheduler();
    }
    @Test void closingSchedulerInvalidatesQueuedRegionCallback() {
        DyClaim plugin=mock(DyClaim.class);Server server=mock(Server.class);World world=mock(World.class);
        RegionScheduler region=mock(RegionScheduler.class);ScheduledTask nativeTask=mock(ScheduledTask.class);
        when(plugin.getServer()).thenReturn(server);when(server.getRegionScheduler()).thenReturn(region);
        GlobalRegionScheduler wrong=mock(GlobalRegionScheduler.class);when(server.getGlobalRegionScheduler()).thenReturn(wrong);
        when(wrong.runDelayed(eq(plugin),any(),anyLong())).thenReturn(nativeTask);
        AtomicReference<Consumer<ScheduledTask>> action=new AtomicReference<>();
        when(region.runDelayed(eq(plugin),eq(world),eq(0),eq(0),any(),eq(1L))).thenAnswer(i->{action.set(i.getArgument(4));return nativeTask;});
        AtomicInteger calls=new AtomicInteger();PlatformTasks tasks=PlatformTasks.folia(plugin);
        tasks.regionLater(world,0,0,calls::incrementAndGet,1);assertNotNull(action.get());tasks.close();action.get().accept(nativeTask);
        assertEquals(0,calls.get());verify(nativeTask).cancel();
    }
}
