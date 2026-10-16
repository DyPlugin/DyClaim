package dev.dyclaim;
import dev.dyclaim.platform.PlatformTasks;
import dev.dyclaim.manager.ConfirmationManager;
import io.papermc.paper.threadedregions.scheduler.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ManagerTaskOwnershipTest extends ManagerFixture {
    @Test void cancellationDuringTaskBindingCannotLeaveAStaleOfferOrTimer(){
        var player=player("Recipient",chunk(0,0));var handle=mock(org.bukkit.scheduler.BukkitTask.class);
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),anyLong())).thenAnswer(i->{confirmations.clear();return handle;});
        confirmations.addPending(player.getUniqueId(),ConfirmationManager.ActionType.CLAIM,ignored->{},ignored->{});
        assertFalse(confirmations.hasPending(player.getUniqueId()));verify(handle).cancel();verify(player,never()).sendMessage("confirm-text");
    }
    @Test void taskRejectionDoesNotLeaveAnAcceptableUnannouncedOffer(){
        var player=player("Recipient",chunk(0,0));
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),anyLong())).thenThrow(new IllegalStateException("Scheduler closed"));
        assertThrows(IllegalStateException.class,()->confirmations.addPending(player.getUniqueId(),ConfirmationManager.ActionType.CLAIM,ignored->{},ignored->{}));
        assertFalse(confirmations.hasPending(player.getUniqueId()));
    }
    @Test void delayedUpdateNoticeFollowsTheJoiningAdminEntity() {
        var player=player("Admin",chunk(0,0));when(player.hasPermission("dyclaim.admin")).thenReturn(true);
        EntityScheduler entity=mock(EntityScheduler.class);when(player.getScheduler()).thenReturn(entity);
        ScheduledTask nativeTask=mock(ScheduledTask.class);AtomicReference<Consumer<ScheduledTask>> callback=new AtomicReference<>();
        when(entity.runDelayed(eq(plugin),any(),any(),eq(60L))).thenAnswer(i->{callback.set(i.getArgument(1));return nativeTask;});
        when(plugin.getPlatformTasks()).thenReturn(PlatformTasks.folia(plugin));
        new dev.dyclaim.manager.UpdateChecker(plugin).onAdminJoin(new org.bukkit.event.player.PlayerJoinEvent(player,""));
        assertNotNull(callback.get(),"Notification permissions and language must be read on the admin's entity region");
    }
    @Test void confirmationExpiryFollowsTheRecipientEntity() {
        var player=player("Recipient",chunk(0,0));
        when(plugin.getServer().isOwnedByCurrentRegion(player)).thenReturn(true);
        EntityScheduler entity=mock(EntityScheduler.class);when(player.getScheduler()).thenReturn(entity);
        ScheduledTask nativeTask=mock(ScheduledTask.class);AtomicReference<Consumer<ScheduledTask>> callback=new AtomicReference<>();
        when(entity.runDelayed(eq(plugin),any(),any(),anyLong())).thenAnswer(i->{callback.set(i.getArgument(1));return nativeTask;});
        when(plugin.getPlatformTasks()).thenReturn(PlatformTasks.folia(plugin));
        confirmations.addPending(player.getUniqueId(),ConfirmationManager.ActionType.CLAIM,ignored->{},ignored->{});
        assertNotNull(callback.get(),"Expiry messages must execute on the recipient's region");
        callback.get().accept(nativeTask);assertFalse(confirmations.hasPending(player.getUniqueId()));
        verify(player).sendMessage("confirm-expired");
    }
    @Test void deferredClaimSaveUsesGlobalSchedulerWithoutAccessingEntities() {
        GlobalRegionScheduler global=mock(GlobalRegionScheduler.class);when(plugin.getServer().getGlobalRegionScheduler()).thenReturn(global);
        ScheduledTask nativeTask=mock(ScheduledTask.class);AtomicReference<Consumer<ScheduledTask>> callback=new AtomicReference<>();
        when(global.runDelayed(eq(plugin),any(),eq(20L))).thenAnswer(i->{callback.set(i.getArgument(1));return nativeTask;});
        when(plugin.getPlatformTasks()).thenReturn(PlatformTasks.folia(plugin));
        claims.saveAll();assertNotNull(callback.get());callback.get().accept(nativeTask);
        assertTrue(java.nio.file.Files.exists(dir.resolve("claims.json")));
    }
}
