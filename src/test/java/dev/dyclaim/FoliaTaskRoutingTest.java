package dev.dyclaim;

import dev.dyclaim.platform.PlatformTasks;
import io.papermc.paper.threadedregions.scheduler.*;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FoliaTaskRoutingTest {
    @Test void playerTaskUsesEntityOwnerAndRetirementPreventsQueuedAction() {
        DyClaim plugin=mock(DyClaim.class);Server server=mock(Server.class);Player player=mock(Player.class);
        EntityScheduler entity=mock(EntityScheduler.class);ScheduledTask nativeTask=mock(ScheduledTask.class);
        when(plugin.getServer()).thenReturn(server);when(player.getScheduler()).thenReturn(entity);
        org.bukkit.scheduler.BukkitScheduler legacy=mock(org.bukkit.scheduler.BukkitScheduler.class);when(server.getScheduler()).thenReturn(legacy);
        when(legacy.runTaskLater(eq(plugin),any(Runnable.class),anyLong())).thenReturn(mock(BukkitTask.class));
        AtomicReference<Consumer<ScheduledTask>> action=new AtomicReference<>();AtomicReference<Runnable> retire=new AtomicReference<>();
        when(entity.runDelayed(eq(plugin),any(),any(),eq(6L))).thenAnswer(i->{action.set(i.getArgument(1));retire.set(i.getArgument(2));return nativeTask;});
        AtomicInteger calls=new AtomicInteger();BukkitTask handle=PlatformTasks.folia(plugin).playerLater(player,calls::incrementAndGet,6);
        assertNotNull(action.get(),"Player work must reach the entity scheduler, not the global or Bukkit scheduler");
        retire.get().run();action.get().accept(nativeTask);
        assertTrue(handle.isCancelled());assertEquals(0,calls.get());verify(server,never()).getScheduler();
    }
    @Test void globalTaskUsesGlobalRegionWithoutAccessingBukkitScheduler() {
        DyClaim plugin=mock(DyClaim.class);Server server=mock(Server.class);GlobalRegionScheduler global=mock(GlobalRegionScheduler.class);
        when(plugin.getServer()).thenReturn(server);when(server.getGlobalRegionScheduler()).thenReturn(global);
        org.bukkit.scheduler.BukkitScheduler legacy=mock(org.bukkit.scheduler.BukkitScheduler.class);when(server.getScheduler()).thenReturn(legacy);
        when(legacy.runTaskLater(eq(plugin),any(Runnable.class),anyLong())).thenReturn(mock(BukkitTask.class));
        ScheduledTask nativeTask=mock(ScheduledTask.class);AtomicReference<Consumer<ScheduledTask>> action=new AtomicReference<>();
        when(global.runDelayed(eq(plugin),any(),eq(3L))).thenAnswer(i->{action.set(i.getArgument(1));return nativeTask;});
        AtomicInteger calls=new AtomicInteger();BukkitTask handle=PlatformTasks.folia(plugin).globalLater(calls::incrementAndGet,3);
        assertNotNull(action.get());action.get().accept(nativeTask);assertEquals(1,calls.get());
        handle.cancel();verify(nativeTask).cancel();verify(server,never()).getScheduler();
    }
}
