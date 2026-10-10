package dev.dyclaim;

import dev.dyclaim.listener.ClaimProtectionListener;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MobProtectionRegressionTest extends ManagerFixture {
    static Stream<Class<? extends Mob>> hostileTypes(){return Stream.of(Slime.class,Ghast.class,Phantom.class);}
    @ParameterizedTest @MethodSource("hostileTypes")
    void disabledNaturalHostileSpawningIncludesNonMonsterEnemies(Class<? extends Mob> type) {
        var inside=chunk(0,0);var owner=player("Owner",inside);claims.claimChunk(owner,inside);
        var entity=mock(type);var event=mock(CreatureSpawnEvent.class);var location=owner.getLocation();
        when(event.getEntity()).thenReturn(entity);when(event.getLocation()).thenReturn(location);
        when(event.getSpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.NATURAL);
        var listener=new ClaimProtectionListener(plugin);listener.onCreatureSpawn(event);verify(event).setCancelled(true);
        claims.getClaimAt(inside).setChoice("mob-spawning",true);clearInvocations(event);
        listener.onCreatureSpawn(event);verify(event,never()).setCancelled(true);
        claims.getClaimAt(inside).setChoice("mob-spawning",false);when(event.getSpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.SPAWNER);
        listener.onCreatureSpawn(event);verify(event,never()).setCancelled(true);
    }
    @ParameterizedTest @MethodSource("hostileTypes")
    void hostileCleanupIncludesNonMonsterEnemiesAndKeepsAnimals(Class<? extends Mob> type) {
        var inside=chunk(0,0);var owner=player("Owner",inside);claims.claimChunk(owner,inside);
        var location=owner.getLocation();var hostile=mock(type);when(hostile.isValid()).thenReturn(true);when(hostile.getLocation()).thenReturn(location);
        when(hostile.getType()).thenReturn(type==Slime.class?EntityType.SLIME:type==Ghast.class?EntityType.GHAST:EntityType.PHANTOM);
        Cow animal=mock(Cow.class);when(animal.isValid()).thenReturn(true);when(animal.getLocation()).thenReturn(location);when(animal.getType()).thenReturn(EntityType.COW);
        when(world.isChunkLoaded(0,0)).thenReturn(true);when(inside.getEntities()).thenReturn(new Entity[]{hostile,animal});
        List<Runnable> scans=new ArrayList<>();
        when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),anyLong(),anyLong())).thenAnswer(i->{scans.add(i.getArgument(1));return mock(org.bukkit.scheduler.BukkitTask.class);});
        values.set("protection.mobs.remove-on-entry.enabled",true);features.reloadTasks();
        for(int tick=0;tick<3;tick++)scans.get(0).run();
        verify(hostile,atLeastOnce()).remove();verify(animal,never()).remove();
    }
    @Test void disablingHostileSpawningStillAllowsNaturalAnimals() {
        var inside=chunk(0,0);var owner=player("Owner",inside);claims.claimChunk(owner,inside);
        Cow animal=mock(Cow.class);var event=mock(CreatureSpawnEvent.class);var location=owner.getLocation();
        when(event.getEntity()).thenReturn(animal);when(event.getLocation()).thenReturn(location);when(event.getSpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.NATURAL);
        new ClaimProtectionListener(plugin).onCreatureSpawn(event);verify(event,never()).setCancelled(true);
    }
}
