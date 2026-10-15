package dev.dyclaim;
import dev.dyclaim.platform.PlatformTasks;
import dev.dyclaim.platform.proxy.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BackendStatusBridgeTest extends ManagerFixture {
    @Test void lateSchedulingFailureDoesNotDiscardANewerRequest() throws Exception {
        when(plugin.isEnabled()).thenReturn(true);Player actor=player("Admin",chunk(0,0));when(actor.hasPermission("dyclaim.admin")).thenReturn(true);
        PlatformTasks tasks=mock(PlatformTasks.class);BackendStatusBridge bridge=new BackendStatusBridge(plugin,tasks,"1.0.2");
        var clock=new java.util.concurrent.atomic.AtomicLong();
        var field=BackendStatusBridge.class.getDeclaredField("admission");field.setAccessible(true);field.set(bridge,new StatusRequests(clock::get));
        var calls=new java.util.concurrent.atomic.AtomicInteger();UUID newer=UUID.randomUUID();
        when(tasks.playerLater(eq(actor),any(),eq(1L))).thenAnswer(i->{
            if(calls.incrementAndGet()==1){clock.set(StatusRequests.TIMEOUT_NANOS);bridge.onPluginMessageReceived(StatusProtocol.CHANNEL,actor,StatusProtocol.request(newer));throw new IllegalStateException("Old scheduler failed");}
            timers.add(i.getArgument(1));return mock(org.bukkit.scheduler.BukkitTask.class);
        });
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,()->bridge.onPluginMessageReceived(StatusProtocol.CHANNEL,actor,StatusProtocol.request(UUID.randomUUID())));
        timers.remove(0).run();var bytes=org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(actor).sendPluginMessage(eq(plugin),eq(StatusProtocol.CHANNEL),bytes.capture());
        org.junit.jupiter.api.Assertions.assertEquals(newer,StatusProtocol.decodeStatus(bytes.getValue()).requestId());
    }
    @Test void permissionsAreRecheckedOnThePlayerTaskBeforeAReply() {
        when(plugin.isEnabled()).thenReturn(true);Player actor=player("Admin",chunk(0,0));
        when(actor.hasPermission("dyclaim.admin")).thenReturn(true);
        BackendStatusBridge bridge=new BackendStatusBridge(plugin,PlatformTasks.bukkit(plugin),"1.0.2");
        bridge.onPluginMessageReceived(StatusProtocol.CHANNEL,actor,StatusProtocol.request(UUID.randomUUID()));
        org.junit.jupiter.api.Assertions.assertEquals(1,timers.size());
        when(actor.hasPermission("dyclaim.admin")).thenReturn(false);timers.remove(0).run();
        verify(actor,never()).sendPluginMessage(eq(plugin),anyString(),any(byte[].class));
    }
    @Test void shutdownInvalidatesQueuedRepliesAndMalformedRequestsNeverQueue() {
        when(plugin.isEnabled()).thenReturn(true);Player actor=player("Admin",chunk(0,0));when(actor.hasPermission("dyclaim.admin")).thenReturn(true);
        BackendStatusBridge bridge=new BackendStatusBridge(plugin,PlatformTasks.bukkit(plugin),"1.0.2");
        bridge.onPluginMessageReceived(StatusProtocol.CHANNEL,actor,new byte[4]);org.junit.jupiter.api.Assertions.assertTrue(timers.isEmpty());
        bridge.onPluginMessageReceived(StatusProtocol.CHANNEL,actor,StatusProtocol.request(UUID.randomUUID()));org.junit.jupiter.api.Assertions.assertEquals(1,timers.size());bridge.close();timers.remove(0).run();
        verify(actor,never()).sendPluginMessage(eq(plugin),anyString(),any(byte[].class));
    }
    @Test void aReplyRetainsTheExternalRequestIdAndRequiresNoClaimMutation() throws Exception {
        when(plugin.isEnabled()).thenReturn(true);Player actor=player("Admin",chunk(0,0));when(actor.hasPermission("dyclaim.admin")).thenReturn(true);
        BackendStatusBridge bridge=new BackendStatusBridge(plugin,PlatformTasks.bukkit(plugin),"1.0.2");UUID id=UUID.randomUUID();
        bridge.onPluginMessageReceived(StatusProtocol.CHANNEL,actor,StatusProtocol.request(id));org.junit.jupiter.api.Assertions.assertEquals(1,timers.size());timers.remove(0).run();
        org.mockito.ArgumentCaptor<byte[]> bytes=org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(actor).sendPluginMessage(eq(plugin),eq(StatusProtocol.CHANNEL),bytes.capture());
        org.junit.jupiter.api.Assertions.assertEquals(id,StatusProtocol.decodeStatus(bytes.getValue()).requestId());
        org.junit.jupiter.api.Assertions.assertEquals(0,claims.getAllClaims().size());
    }
}
