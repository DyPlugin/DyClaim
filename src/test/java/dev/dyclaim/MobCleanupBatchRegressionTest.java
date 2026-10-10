package dev.dyclaim;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Slime;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MobCleanupBatchRegressionTest extends ManagerFixture {
    @ParameterizedTest @ValueSource(booleans={true,false})
    void cachedMobsUseCurrentSpawningPolicyBetweenBatches(boolean onlyWhenSpawningDisabled) {
        var inside=chunk(0,0);var owner=player("Owner",inside);claims.claimChunk(owner,inside);
        var location=owner.getLocation();Slime first=mock(Slime.class),second=mock(Slime.class);
        for(Slime slime:List.of(first,second)) {
            when(slime.isValid()).thenReturn(true);when(slime.getLocation()).thenReturn(location);when(slime.getType()).thenReturn(EntityType.SLIME);
        }
        when(world.isChunkLoaded(0,0)).thenReturn(true);when(inside.getEntities()).thenReturn(new Entity[]{first,second});
        List<Runnable> scans=new ArrayList<>();
        when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),anyLong(),anyLong())).thenAnswer(i->{scans.add(i.getArgument(1));return mock(BukkitTask.class);});
        values.set("protection.mobs.remove-on-entry.enabled",true);
        values.set("protection.mobs.remove-on-entry.batch-size",1);
        values.set("protection.mobs.remove-on-entry.only-when-spawning-disabled",onlyWhenSpawningDisabled);
        features.reloadTasks();
        for(int tick=0;tick<3;tick++)scans.get(0).run();
        verify(first).remove();verify(second,never()).remove();
        claims.getClaimAt(inside).setChoice("mob-spawning",true);
        scans.get(0).run();
        verify(second,onlyWhenSpawningDisabled?never():times(1)).remove();
    }
}
