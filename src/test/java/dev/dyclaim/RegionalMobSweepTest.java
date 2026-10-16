package dev.dyclaim;

import dev.dyclaim.platform.*;
import dev.dyclaim.model.ClaimData;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegionalMobSweepTest extends ManagerFixture {
    PlatformTasks tasks;RegionalMobSweep sweep;ClaimData claim;Mob mob;Chunk target;
    Deque<Runnable> regions=new ArrayDeque<>(),entities=new ArrayDeque<>(),retirements=new ArrayDeque<>();
    AtomicBoolean allowed=new AtomicBoolean(true);
    @BeforeEach void prepare() {
        target=chunk(-2,3);assertTrue(claims.claimChunk(player("Owner",target),target));claim=claims.getClaimAt(target);
        mob=mock(Mob.class);when(mob.isValid()).thenReturn(true);
        when(mob.getLocation()).thenReturn(new Location(world,-24,70,56));
        when(world.isChunkLoaded(-2,3)).thenReturn(true);when(target.getEntities()).thenReturn(new Entity[]{mob});
        tasks=mock(PlatformTasks.class);
        when(tasks.regionLater(eq(world),eq(-2),eq(3),any(),eq(1L))).thenAnswer(i->{regions.add(i.getArgument(3));return mock(BukkitTask.class);});
        when(tasks.entityLater(any(),any(),any(),eq(1L))).thenAnswer(i->{entities.add(i.getArgument(1));retirements.add(i.getArgument(2));return mock(BukkitTask.class);});
        sweep=new RegionalMobSweep(tasks,()->claims.getAllClaims().values(),claims::getByKey,n->world,c->allowed.get(),e->true,()->1);
    }
    @Test void readsChunkOnlyOnRegionAndRemovesOnlyOnEntityOwner() {
        sweep.tick();verify(target,never()).getEntities();assertEquals(1,regions.size());
        regions.remove().run();verify(mob,never()).remove();assertEquals(1,entities.size());
        entities.remove().run();verify(mob).remove();
    }
    @Test void movingOutsideClaimOrChangingPolicyPreventsRemoval() {
        sweep.tick();regions.remove().run();when(mob.getLocation()).thenReturn(new Location(world,100,70,100));
        entities.remove().run();verify(mob,never()).remove();
        sweep.tick();regions.remove().run();when(mob.getLocation()).thenReturn(new Location(world,-24,70,56));allowed.set(false);
        entities.remove().run();verify(mob,never()).remove();
    }
    @Test void retirementReleasesBusySlotAndCloseInvalidatesQueuedRegion() {
        sweep.tick();regions.remove().run();sweep.tick();assertTrue(regions.isEmpty());
        retirements.remove().run();sweep.tick();assertEquals(1,regions.size());
        sweep.close();regions.remove().run();assertEquals(1,entities.size());verify(mob,never()).remove();
    }
    @Test void unloadedChunkIsNeverLoadedAndDoesNotBlockNextSweep() {
        when(world.isChunkLoaded(-2,3)).thenReturn(false);
        sweep.tick();regions.remove().run();verify(target,never()).getEntities();
        sweep.tick();assertEquals(1,regions.size());
    }
    @Test void batchBudgetRotatesThroughEntitiesInsteadOfStarvingLastMob() {
        Mob second=mock(Mob.class);when(second.isValid()).thenReturn(true);when(second.getLocation()).thenReturn(new Location(world,-24,70,56));
        when(target.getEntities()).thenReturn(new Entity[]{mob,second});
        sweep.tick();regions.remove().run();assertEquals(1,entities.size());entities.remove().run();
        sweep.tick();regions.remove().run();assertEquals(1,entities.size());entities.remove().run();
        verify(mob).remove();verify(second).remove();
    }
    @Test void replacedClaimDoesNotInheritQueuedRemoval() {
        sweep.tick();regions.remove().run();claims.remove(claim.getChunkKey());
        assertTrue(claims.claimChunk(player("Replacement",target),target));
        entities.remove().run();verify(mob,never()).remove();
    }
    @Test void claimCollectionIsCapturedOncePerRotationInsteadOfEveryBudgetStep() {
        var another=chunk(4,5);assertTrue(claims.claimChunk(player("Second",another),another));
        var snapshots=new java.util.concurrent.atomic.AtomicInteger();
        sweep=new RegionalMobSweep(tasks,()->{snapshots.incrementAndGet();return claims.getAllClaims().values();},claims::getByKey,n->world,c->false,e->true,()->1);
        sweep.tick();sweep.tick();assertEquals(1,snapshots.get());
        sweep.tick();assertEquals(2,snapshots.get());
    }
    @Test void closeAttemptsEveryCancellationEvenIfOneSchedulerHandleThrows() {
        Mob second=mock(Mob.class);when(target.getEntities()).thenReturn(new Entity[]{mob,second});
        var cancellations=new java.util.concurrent.atomic.AtomicInteger();
        when(tasks.entityLater(any(),any(),any(),eq(1L))).thenAnswer(i->{
            entities.add(i.getArgument(1));BukkitTask handle=mock(BukkitTask.class);
            doAnswer(call->{if(cancellations.incrementAndGet()==1)throw new IllegalStateException("Native cancellation failed");return null;}).when(handle).cancel();
            return handle;
        });
        sweep=new RegionalMobSweep(tasks,()->claims.getAllClaims().values(),claims::getByKey,n->world,c->true,e->true,()->2);
        sweep.tick();regions.remove().run();assertThrows(IllegalStateException.class,sweep::close);
        assertEquals(2,cancellations.get());entities.forEach(Runnable::run);verify(mob,never()).remove();
    }
}
