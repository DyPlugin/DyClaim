package dev.dyclaim;

import dev.dyclaim.platform.*;
import dev.dyclaim.model.ClaimData;
import io.papermc.paper.threadedregions.scheduler.*;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegionalEjectionTest extends ManagerFixture {
    String scope="player";
    Deque<Runnable> regions=new ArrayDeque<>(),players=new ArrayDeque<>();
    Player visitor;ClaimData claim;Location destination;RegionalEjection flow;
    boolean eligible=true;
    @BeforeEach void nativeSetup(){
        var home=chunk(0,0);var owner=player("Owner",home);visitor=player("Visitor",home);
        assertTrue(claims.claimChunk(owner,home));claim=claims.getClaimAt(home);
        when(plugin.isEnabled()).thenReturn(true);
        var region=mock(RegionScheduler.class);var entity=mock(EntityScheduler.class);
        when(plugin.getServer().getRegionScheduler()).thenReturn(region);when(visitor.getScheduler()).thenReturn(entity);
        when(region.runDelayed(eq(plugin),eq(world),anyInt(),anyInt(),any(),anyLong())).thenAnswer(i->{
            Consumer<ScheduledTask> cb=i.getArgument(4);var task=mock(ScheduledTask.class);
            regions.add(()->runAs("region",()->cb.accept(task)));return task;
        });
        when(entity.runDelayed(eq(plugin),any(),any(),anyLong())).thenAnswer(i->{
            Consumer<ScheduledTask> cb=i.getArgument(1);var task=mock(ScheduledTask.class);
            players.add(()->runAs("player",()->cb.accept(task)));return task;
        });
        destination=new Location(world,-7.5,65,-7.5);
        when(visitor.teleportAsync(destination)).thenAnswer(i->{assertEquals("player",scope);return CompletableFuture.completedFuture(true);});
        when(plugin.getPlatformTasks()).thenReturn(PlatformTasks.folia(plugin));
        flow=new RegionalEjection(plugin,plugin.getPlatformTasks(),(w,x,z)->{
            assertEquals("region",scope,"Terrain lookup crossed the owning region");return destination;
        },(p,c)->{assertEquals("player",scope,"Eligibility queried a foreign player");return eligible;});
    }
    void runAs(String next,Runnable action){String previous=scope;scope=next;try{action.run();}finally{scope=previous;}}
    @Test void featureEntryRejectionRoutesTerrainReadsBeforeAnyWorldAccess() throws Exception {
        var manager=new dev.dyclaim.manager.FeatureManager(plugin);
        values.set("warnings.enabled",true);
        var stateField=manager.getClass().getDeclaredField("state");stateField.setAccessible(true);
        var state=(dev.dyclaim.manager.FeatureManager.State)stateField.get(manager);
        var warning=new dev.dyclaim.manager.FeatureManager.WarningChain();warning.stage="applied";warning.mode="claim-entry-ban";warning.until=Long.MAX_VALUE;
        state.warnings.put(claim.getId()+":"+visitor.getUniqueId(),warning);
        var eject=manager.getClass().getDeclaredMethod("eject",Player.class,ClaimData.class);eject.setAccessible(true);
        clearInvocations(world);
        eject.invoke(manager,visitor,claim);
        assertEquals(1,regions.size());verify(world,never()).isChunkLoaded(anyInt(),anyInt());
    }
    @Test void destinationReadAndTransferUseSeparateOwners(){
        flow.start(visitor,claim);assertEquals(1,regions.size());regions.remove().run();
        verify(visitor,never()).teleportAsync(any(Location.class));players.remove().run();
        verify(visitor).teleportAsync(destination);verify(visitor,never()).teleport(any(Location.class));
    }
    @Test void movedVisitorIsNotEjectedAfterQueuedDestinationRead(){
        flow.start(visitor,claim);regions.remove().run();
        when(visitor.getLocation()).thenReturn(new Location(world,128,65,128));players.remove().run();
        verify(visitor,never()).teleportAsync(any(Location.class));
    }
    @Test void reloadInvalidatesQueuedRegionReads(){
        flow.start(visitor,claim);flow.reset();regions.remove().run();assertTrue(players.isEmpty());
        verify(visitor,never()).teleportAsync(any(Location.class));
    }
    @Test void expiredEntryBanPreventsQueuedTransfer(){
        flow.start(visitor,claim);regions.remove().run();eligible=false;players.remove().run();
        verify(visitor,never()).teleportAsync(any(Location.class));
    }
}
