package dev.dyclaim;
import dev.dyclaim.platform.PlatformTasks;
import dev.dyclaim.manager.FeatureManager;
import dev.dyclaim.listener.ClaimProtectionListener;
import org.bukkit.Location;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaBoundaryLookupTest extends ManagerFixture {
    Location destination;org.bukkit.entity.Player visitor;
    @BeforeEach void nativeBoundary(){
        var claimChunk=chunk(128,0);var owner=player("Owner",claimChunk);visitor=player("Visitor",chunk(0,0));assertTrue(claims.claimChunk(owner,claimChunk));
        var tasks=mock(PlatformTasks.class);when(tasks.isRegionThreaded()).thenReturn(true);when(tasks.ownsEntity(any())).thenReturn(true);when(plugin.getPlatformTasks()).thenReturn(tasks);
        destination=mock(Location.class);when(destination.getWorld()).thenReturn(world);when(destination.getBlockX()).thenReturn(2048);
        when(destination.getChunk()).thenThrow(new IllegalStateException("Foreign chunk lookup is forbidden"));
    }
    @Test void entryBanUsesDestinationCoordinatesWithoutLoadingItsChunk() throws Exception {
        values.set("warnings.enabled",true);var claim=claims.getByKey("world:128:0");
        var field=FeatureManager.class.getDeclaredField("state");field.setAccessible(true);var state=(FeatureManager.State)field.get(features);
        var warning=new FeatureManager.WarningChain();warning.mode="claim-entry-ban";warning.stage="applied";warning.until=Long.MAX_VALUE;
        state.warnings.put(claim.getId()+":"+visitor.getUniqueId(),warning);
        var from=visitor.getLocation();var event=mock(PlayerMoveEvent.class);when(event.getPlayer()).thenReturn(visitor);when(event.getFrom()).thenReturn(from);when(event.getTo()).thenReturn(destination);
        assertDoesNotThrow(()->features.entry(event));verify(event).setCancelled(true);verify(destination,never()).getChunk();
    }
    @Test void chorusDestinationProtectionDoesNotLoadForeignChunk(){
        var event=mock(PlayerTeleportEvent.class);when(event.getPlayer()).thenReturn(visitor);when(event.getTo()).thenReturn(destination);when(event.getCause()).thenReturn(PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT);
        assertDoesNotThrow(()->new ClaimProtectionListener(plugin).onPlayerTeleport(event));verify(event).setCancelled(true);verify(destination,never()).getChunk();
    }
    @Test void ordinaryBoundaryTrackingDoesNotLoadForeignDestination(){
        var from=visitor.getLocation();var event=mock(PlayerMoveEvent.class);when(event.getPlayer()).thenReturn(visitor);when(event.getFrom()).thenReturn(from);when(event.getTo()).thenReturn(destination);
        assertDoesNotThrow(()->features.move(event));verify(destination,never()).getChunk();
    }
    @Test void automaticClaimWaitsForThePlayerToOwnAndReachDestination(){
        values.set("auto-claim.enabled",true);values.set("auto-claim.require-economy",false);values.set("auto-claim.allow-zero-price",true);
        when(economy.isEnabled()).thenReturn(false);features.handle(visitor,new String[]{"auto","on"});
        when(destination.getBlockX()).thenReturn(4096);var targetChunk=chunk(256,0);
        var queue=new java.util.ArrayList<Runnable>();
        when(plugin.getPlatformTasks().playerLater(eq(visitor),any(),anyLong())).thenAnswer(i->{queue.add(i.getArgument(1));return mock(org.bukkit.scheduler.BukkitTask.class);});
        var from=visitor.getLocation();var event=mock(PlayerMoveEvent.class);when(event.getPlayer()).thenReturn(visitor);when(event.getFrom()).thenReturn(from);when(event.getTo()).thenReturn(destination);
        assertDoesNotThrow(()->features.move(event));assertEquals(1,queue.size());assertNull(claims.getClaimAt(targetChunk));
        var current=new Location(world,4096,65,0);when(world.getChunkAt(current)).thenReturn(targetChunk);
        when(visitor.getLocation()).thenReturn(current);queue.remove(0).run();
        assertEquals(visitor.getUniqueId(),claims.getClaimAt(targetChunk).getOwnerUUID());verify(destination,never()).getChunk();
    }
}
