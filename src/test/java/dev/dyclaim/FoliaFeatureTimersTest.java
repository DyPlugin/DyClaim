package dev.dyclaim;
import dev.dyclaim.platform.PlatformTasks;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaFeatureTimersTest extends ManagerFixture {
    @Test void confirmedBulkSaleRunsOnRequestingPlayersScheduler() {
        var owner=player("Owner",chunk(0,0));assertTrue(claims.claimChunk(owner,owner.getLocation().getChunk()));
        when(owner.hasPermission("dyclaim.admin")).thenReturn(true);
        PlatformTasks tasks=mock(PlatformTasks.class);when(tasks.isRegionThreaded()).thenReturn(true);
        when(tasks.ownsEntity(owner)).thenReturn(true);
        when(plugin.getPlatformTasks()).thenReturn(tasks);
        List<Runnable> batches=new ArrayList<>();
        when(tasks.playerTimer(eq(owner),any(),eq(1L),eq(1L))).thenAnswer(i->{batches.add(i.getArgument(1));return mock(BukkitTask.class);});
        features.handle(owner,new String[]{"admin","bulksell"});assertTrue(confirmations.confirm(owner.getUniqueId()));
        assertEquals(1,batches.size());batches.get(0).run();assertTrue(claims.getAllClaims().isEmpty());
        verify(scheduler,never()).runTaskTimer(eq(plugin),any(Runnable.class),anyLong(),anyLong());
    }
    @Test void reloadInvalidatesAnAlreadyCreatedPlayerBatch() {
        var owner=player("Owner",chunk(0,0));assertTrue(claims.claimChunk(owner,owner.getLocation().getChunk()));
        when(owner.hasPermission("dyclaim.admin")).thenReturn(true);
        PlatformTasks tasks=mock(PlatformTasks.class);when(tasks.isRegionThreaded()).thenReturn(true);when(plugin.getPlatformTasks()).thenReturn(tasks);
        when(tasks.ownsEntity(owner)).thenReturn(true);
        List<Runnable> batches=new ArrayList<>();BukkitTask handle=mock(BukkitTask.class);
        when(tasks.playerTimer(eq(owner),any(),eq(1L),eq(1L))).thenAnswer(i->{batches.add(i.getArgument(1));return handle;});
        features.handle(owner,new String[]{"admin","bulksell"});assertTrue(confirmations.confirm(owner.getUniqueId()));
        features.reloadTasks();batches.get(0).run();assertEquals(1,claims.getAllClaims().size());verify(handle).cancel();
        verify(economy,never()).deposit(any(UUID.class),anyDouble());
    }
    @Test void cleanupAndScheduledPurgeUsePlatformTimersAndReloadCancelsOldTimers() {
        values.set("protection.mobs.remove-on-entry.enabled",true);
        values.set("admin.purge.scheduled-enabled",true);
        PlatformTasks tasks=mock(PlatformTasks.class);when(tasks.isRegionThreaded()).thenReturn(true);
        when(plugin.getPlatformTasks()).thenReturn(tasks);
        BukkitTask mob=mock(BukkitTask.class),purge=mock(BukkitTask.class);
        when(tasks.globalTimer(any(),eq(20L),anyLong())).thenReturn(mob);
        when(tasks.globalTimer(any(),eq(values.getLong("admin.purge.check-interval-minutes")*1200),anyLong())).thenReturn(purge);
        features.reloadTasks();features.reloadTasks();
        verify(mob).cancel();verify(purge).cancel();
        verify(scheduler,never()).runTaskTimer(eq(plugin),any(Runnable.class),anyLong(),anyLong());
    }
}
