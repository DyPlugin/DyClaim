package dev.dyclaim;

import dev.dyclaim.manager.FeatureManager;
import dev.dyclaim.util.AtomicJson;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PurgeReloadRegressionTest extends ManagerFixture {
    @Test void shutdownDuringARefundStopsTheRestOfTheCurrentPurgeBatch() throws Exception {
        var first=player("First",chunk(0,0));var second=player("Second",chunk(1,0));
        claims.claimChunk(first,first.getLocation().getChunk());claims.claimChunk(second,second.getLocation().getChunk());
        var state=new FeatureManager.State();
        for(var owner:List.of(first,second)) {
            var seen=new FeatureManager.Seen();seen.at=System.currentTimeMillis()-8*86_400_000L;state.lastSeen.put(owner.getUniqueId(),seen);
            bukkit.when(()->Bukkit.getPlayer(owner.getUniqueId())).thenReturn(null);
        }
        AtomicJson.write(dir.resolve("state.json"),state);features=new FeatureManager(plugin);when(plugin.getFeatureManager()).thenReturn(features);
        for(var name:List.of("featureManager","claimManager")) {
            var field=DyClaim.class.getDeclaredField(name);field.setAccessible(true);field.set(plugin,name.equals("featureManager")?features:claims);
        }
        doCallRealMethod().when(plugin).onDisable();var disabled=new java.util.concurrent.atomic.AtomicBoolean();var pluginManager=plugin.getServer().getPluginManager();
        doAnswer(i->{if(disabled.compareAndSet(false,true))plugin.onDisable();return null;}).when(pluginManager).disablePlugin(plugin);
        values.set("admin.purge.scheduled-enabled",true);values.set("admin.purge.inactive-days",7);values.set("admin.purge.batch-size",2);values.set("admin.purge.refund",true);
        List<Runnable> scheduled=new ArrayList<>();
        when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),anyLong(),anyLong())).thenAnswer(i->{scheduled.add(i.getArgument(1));return mock(BukkitTask.class);});
        features.reloadTasks();scheduled.get(0).run();java.nio.file.Files.createDirectory(dir.resolve("state.json.tmp"));
        scheduled.get(1).run();assertTrue(disabled.get());assertEquals(1,claims.getAllClaims().size());
        verify(economy,times(1)).deposit(any(java.util.UUID.class),anyDouble());
        assertEquals(1,new dev.dyclaim.manager.ClaimManager(plugin).getAllClaims().size());
    }
    @Test void reloadingStopsAnActiveScheduledPurgeUsingOldInactivityRules() throws Exception {
        var first=player("First",chunk(0,0));var second=player("Second",chunk(1,0));
        claims.claimChunk(first,first.getLocation().getChunk());claims.claimChunk(second,second.getLocation().getChunk());
        var state=new FeatureManager.State();
        for(var owner:List.of(first,second)) {
            var seen=new FeatureManager.Seen();seen.at=System.currentTimeMillis()-8*86_400_000L;state.lastSeen.put(owner.getUniqueId(),seen);
            bukkit.when(()->Bukkit.getPlayer(owner.getUniqueId())).thenReturn(null);
        }
        AtomicJson.write(dir.resolve("state.json"),state);features=new FeatureManager(plugin);when(plugin.getFeatureManager()).thenReturn(features);
        values.set("admin.purge.scheduled-enabled",true);values.set("admin.purge.inactive-days",7);values.set("admin.purge.batch-size",1);values.set("admin.purge.refund",false);
        List<Runnable> scheduled=new ArrayList<>();
        when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),anyLong(),anyLong())).thenAnswer(i->{scheduled.add(i.getArgument(1));return mock(BukkitTask.class);});
        features.reloadTasks();scheduled.get(0).run();assertEquals(2,scheduled.size());
        var activeBatch=scheduled.get(1);activeBatch.run();assertEquals(1,claims.getAllClaims().size());
        values.set("admin.purge.inactive-days",30);features.reloadTasks();activeBatch.run();assertEquals(1,claims.getAllClaims().size());
        scheduled.get(2).run();assertEquals(1,claims.getAllClaims().size());
    }
}
