package dev.dyclaim;
import dev.dyclaim.platform.*;
import dev.dyclaim.model.ClaimData;
import io.papermc.paper.threadedregions.scheduler.*;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RegionalTeleportFlowTest extends ManagerFixture {
    Player owner;ClaimData claim;Location destination;RegionalTeleportFlow flow;
    String scope="player";Deque<Runnable> regions=new ArrayDeque<>(),players=new ArrayDeque<>();
    AtomicInteger resolutions=new AtomicInteger();CompletableFuture<Boolean> transfer=new CompletableFuture<>();
    @BeforeEach void setupRegions(){
        owner=player("Owner",chunk(0,0));assertTrue(claims.claimChunk(owner,chunk(256,0)));claim=claims.getByKey("world:256:0");
        when(plugin.isEnabled()).thenReturn(true);when(config.getTeleportWarmup()).thenReturn(3);
        when(owner.isOnline()).thenAnswer(i->{assertEquals("player",scope);return true;});
        RegionScheduler region=mock(RegionScheduler.class);EntityScheduler entity=mock(EntityScheduler.class);
        when(plugin.getServer().getRegionScheduler()).thenReturn(region);when(owner.getScheduler()).thenReturn(entity);
        when(region.runDelayed(eq(plugin),eq(world),eq(256),eq(0),any(),anyLong())).thenAnswer(i->{Consumer<ScheduledTask> callback=i.getArgument(4);ScheduledTask task=mock(ScheduledTask.class);regions.add(()->runAs("target",()->callback.accept(task)));return task;});
        when(entity.runDelayed(eq(plugin),any(),any(),anyLong())).thenAnswer(i->{Consumer<ScheduledTask> callback=i.getArgument(1);ScheduledTask task=mock(ScheduledTask.class);players.add(()->runAs("player",()->callback.accept(task)));return task;});
        var tasks=PlatformTasks.folia(plugin);destination=mock(Location.class);
        when(owner.teleportAsync(destination)).thenAnswer(i->{assertEquals("player",scope);return transfer;});
        flow=new RegionalTeleportFlow(plugin,tasks,c->{assertEquals("target",scope,"Foreign chunk safety must be resolved on its region");resolutions.incrementAndGet();return destination;});
    }
    void runAs(String next,Runnable callback){String previous=scope;scope=next;try{callback.run();}finally{scope=previous;}}
    void region(){assertFalse(regions.isEmpty());regions.removeFirst().run();}
    void player(){assertFalse(players.isEmpty());players.removeFirst().run();}
    void enterWarmup(){assertDoesNotThrow(()->flow.start(owner,claim));region();player();}
    void initiateTransfer(){enterWarmup();player();region();player();}
    @Test void safetyQueriesAndTransferCompletionUseTheirCorrectOwners(){
        initiateTransfer();assertEquals(2,resolutions.get());verify(owner,never()).teleport(any(Location.class));
        verify(owner,never()).sendMessage("tp-success");transfer.complete(true);verify(owner,never()).sendMessage("tp-success");
        player();verify(owner).sendMessage("tp-success");assertFalse(flow.hasPending(owner.getUniqueId()));
    }
    @Test void cancelledQueuedScanCannotResolveOrInitiateTransfer(){
        assertDoesNotThrow(()->flow.start(owner,claim));assertFalse(regions.isEmpty());flow.cancel(owner,true);region();
        assertEquals(0,resolutions.get());verify(owner,never()).teleportAsync(any(Location.class));
    }
    @Test void revokedPermissionDuringWarmupPreventsTheTransfer(){
        enterWarmup();when(owner.hasPermission("dyclaim.teleport")).thenReturn(false);player();
        verify(owner,never()).teleportAsync(any(Location.class));verify(owner).sendMessage("operation-changed");
    }
    @Test void ownershipChangeAfterTargetSafetyCheckIsRevalidatedOnPlayerRegion(){
        enterWarmup();player();region();claim.transferOwnership(UUID.randomUUID(),"Recipient");player();
        verify(owner,never()).teleportAsync(any(Location.class));verify(owner).sendMessage("operation-changed");
    }
    @Test void closingAfterTargetScanPreventsAQueuedTransfer(){
        enterWarmup();player();region();flow.close();while(!players.isEmpty())player();
        verify(owner,never()).teleportAsync(any(Location.class));assertTrue(flow.isClosed());
    }
    @Test void oldNativeCompletionCannotConsumeAReplacementRequestOrSendSuccess(){
        initiateTransfer();flow.cancel(owner,false);flow.start(owner,claim);transfer.complete(true);
        assertTrue(flow.hasPending(owner.getUniqueId()));verify(owner,never()).sendMessage("tp-success");
        region();player();assertTrue(flow.hasPending(owner.getUniqueId()));
    }
    @Test void reloadCleanupCannotRemoveTheBlindnessOfANewerWarmup(){
        enterWarmup();flow.reset();assertFalse(flow.isClosed());player();flow.start(owner,claim);region();
        assertEquals(2,players.size());players.removeLast().run();
        verify(owner,times(1)).removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        player();verify(owner,times(1)).removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        assertTrue(flow.hasPending(owner.getUniqueId()));
    }
    @Test void shutdownEntityOwnerRestoresWarmupEffectBeforeSchedulersAreClosed(){
        enterWarmup();when(plugin.isEnabled()).thenReturn(false);
        when(plugin.getServer().isOwnedByCurrentRegion(owner)).thenReturn(true);
        int scheduled=players.size();flow.close();
        verify(owner).removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        assertEquals(scheduled,players.size(),"Disabled plugin cannot queue new cleanup work");
    }
    @Test void shutdownIncludesEffectsAwaitingAQueuedReloadRestoration(){
        enterWarmup();flow.reset();when(plugin.isEnabled()).thenReturn(false);
        when(plugin.getServer().isOwnedByCurrentRegion(owner)).thenReturn(true);flow.close();
        verify(owner).removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
    }
    @Test void closingDuringEffectApplicationStillRestoresTheAppliedEffect(){
        var blinded=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(i->{blinded.set(false);return null;}).when(owner).removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        doAnswer(i->{when(plugin.isEnabled()).thenReturn(false);when(plugin.getServer().isOwnedByCurrentRegion(owner)).thenReturn(true);flow.close();blinded.set(true);return true;}).when(owner).addPotionEffect(any(org.bukkit.potion.PotionEffect.class));
        enterWarmup();assertFalse(blinded.get());verify(owner,never()).sendMessage("tp-warmup");
    }
}
