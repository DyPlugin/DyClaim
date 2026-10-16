package dev.dyclaim;
import dev.dyclaim.platform.PlatformTasks;
import dev.dyclaim.listener.ClaimProtectionListener;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.potion.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaRemoteActorTest extends ManagerFixture {
    String scope="source";Player attacker,victim;ClaimProtectionListener listener;
    java.util.concurrent.atomic.AtomicBoolean permissionAllowed=new java.util.concurrent.atomic.AtomicBoolean();
    @BeforeEach void nativeActors(){
        attacker=player("Attacker",chunk(0,0));victim=player("Victim",chunk(128,0));assertTrue(claims.claimChunk(attacker,chunk(0,0)));assertTrue(claims.claimChunk(victim,chunk(128,0)));
        var tasks=mock(PlatformTasks.class);when(tasks.isRegionThreaded()).thenReturn(true);when(plugin.getPlatformTasks()).thenReturn(tasks);
        when(tasks.ownsEntity(any())).thenAnswer(i->i.getArgument(0)==attacker?scope.equals("source"):scope.equals("victim"));
        when(attacker.getLocation()).thenAnswer(i->{assertEquals("source",scope,"Remote attacker position queried");return new Location(world,0,65,0);});
        when(attacker.hasPermission("dyclaim.admin.bypass")).thenAnswer(i->{assertEquals("source",scope,"Remote actor permission queried");return permissionAllowed.get();});
        when(victim.getLocation()).thenReturn(new Location(world,2048,65,0));
        when(world.getChunkAt(any(Location.class))).thenThrow(new IllegalStateException("Foreign chunk resolution"));
        listener=new ClaimProtectionListener(plugin);
    }
    @Test void lingeringCloudFiltersVictimWithoutReadingRemoteActorOrLoadingItsChunk(){
        access.recordActor(attacker);scope="victim";
        var effects=List.of(new PotionEffect(PotionEffectType.POISON,100,0));var cloud=mock(AreaEffectCloud.class);when(cloud.getSource()).thenReturn(attacker);when(cloud.getCustomEffects()).thenReturn(effects);
        var affected=new ArrayList<LivingEntity>(List.of(victim));var event=mock(AreaEffectCloudApplyEvent.class);when(event.getEntity()).thenReturn(cloud);when(event.getAffectedEntities()).thenReturn(affected);
        assertDoesNotThrow(()->listener.lingeringPotion(event));assertTrue(affected.isEmpty());
    }
    @Test void rangedAttackStillWorksWhenBothClaimPvpSettingsAllowIt(){
        claims.getByKey("world:0:0").setChoice("pvp",true);claims.getByKey("world:128:0").setChoice("pvp",true);
        access.recordActor(attacker);scope="victim";var arrow=mock(Arrow.class);when(arrow.getShooter()).thenReturn(attacker);
        var event=mock(EntityDamageByEntityEvent.class);when(event.getEntity()).thenReturn(victim);when(event.getDamager()).thenReturn(arrow);
        assertDoesNotThrow(()->listener.onEntityDamageByEntity(event));verify(event,never()).setCancelled(true);
    }
    @Test void unknownRemoteAttackerFailsClosedUntilItsOwnerSnapshotExists(){
        scope="victim";var arrow=mock(Arrow.class);when(arrow.getShooter()).thenReturn(attacker);
        var event=mock(EntityDamageByEntityEvent.class);when(event.getEntity()).thenReturn(victim);when(event.getDamager()).thenReturn(arrow);
        assertDoesNotThrow(()->listener.onEntityDamageByEntity(event));verify(event).setCancelled(true);
    }
    @Test void nativeJoinRefreshesBypassPermissionsOnPlayerOwnerAndQuitDropsTheSnapshot(){
        var refresh=new java.util.concurrent.atomic.AtomicReference<Runnable>();
        when(plugin.getPlatformTasks().playerTimer(eq(attacker),any(),eq(1L),eq(1L))).thenAnswer(i->{refresh.set(i.getArgument(1));return mock(org.bukkit.scheduler.BukkitTask.class);});
        new dev.dyclaim.listener.PlayerListener(plugin).onJoin(new org.bukkit.event.player.PlayerJoinEvent(attacker,""));
        assertNotNull(refresh.get());permissionAllowed.set(true);refresh.get().run();scope="victim";assertTrue(access.bypass(attacker));
        scope="source";permissionAllowed.set(false);refresh.get().run();scope="victim";assertFalse(access.bypass(attacker));
        access.quit(attacker.getUniqueId());assertFalse(access.bypass(attacker));
    }
    @Test void remoteDispenserOriginUsesImmutableBlockCoordinates(){
        scope="victim";var dispenser=mock(org.bukkit.block.Block.class);when(dispenser.getWorld()).thenReturn(world);
        when(dispenser.getChunk()).thenThrow(new IllegalStateException("Foreign dispenser chunk"));
        var source=mock(org.bukkit.projectiles.BlockProjectileSource.class);when(source.getBlock()).thenReturn(dispenser);
        var arrow=mock(Arrow.class);when(arrow.getShooter()).thenReturn(source);
        var event=mock(EntityDamageByEntityEvent.class);when(event.getEntity()).thenReturn(victim);when(event.getDamager()).thenReturn(arrow);
        assertDoesNotThrow(()->listener.onEntityDamageByEntity(event));verify(event).setCancelled(true);
    }
    @Test void fluidBoundaryProtectionDoesNotLoadAdjacentForeignChunk(){
        values.set("protection.boundaries.block-external-fluid",true);assertTrue(claims.claimChunk(victim,chunk(1,0)));
        var from=mock(org.bukkit.block.Block.class);var to=mock(org.bukkit.block.Block.class);
        when(from.getWorld()).thenReturn(world);when(to.getWorld()).thenReturn(world);when(from.getX()).thenReturn(15);when(to.getX()).thenReturn(16);
        when(from.getChunk()).thenThrow(new IllegalStateException("Chunk resolution"));when(to.getChunk()).thenThrow(new IllegalStateException("Foreign chunk resolution"));
        var event=mock(org.bukkit.event.block.BlockFromToEvent.class);when(event.getBlock()).thenReturn(from);when(event.getToBlock()).thenReturn(to);
        assertDoesNotThrow(()->listener.onBlockFromTo(event));verify(event).setCancelled(true);
    }
}
