package dev.dyclaim;

import dev.dyclaim.manager.ClaimManager;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BulkOperationRegressionTest extends ManagerFixture {
    private Runnable bulkSale(int batchSize) {
        Player first=player("First",chunk(0,0)),second=player("Second",chunk(1,0));
        claims.claimChunk(first,first.getLocation().getChunk());claims.claimChunk(second,second.getLocation().getChunk());
        when(first.hasPermission("dyclaim.admin")).thenReturn(true);
        values.set("admin.purge.batch-size",batchSize);
        List<Runnable> scheduled=new ArrayList<>();
        when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),anyLong(),anyLong())).thenAnswer(i->{scheduled.add(i.getArgument(1));return mock(BukkitTask.class);});
        assertTrue(features.handle(first,new String[]{"admin","bulksell"}));
        assertTrue(confirmations.confirm(first.getUniqueId()));
        return scheduled.get(0);
    }

    @Test void shutdownDuringBulkRefundStopsBeforeNextClaim() throws Exception {
        Runnable batch=bulkSale(2);
        for(String name:List.of("featureManager","claimManager")) {
            var field=DyClaim.class.getDeclaredField(name);field.setAccessible(true);field.set(plugin,name.equals("featureManager")?features:claims);
        }
        doCallRealMethod().when(plugin).onDisable();AtomicBoolean disabled=new AtomicBoolean();var manager=plugin.getServer().getPluginManager();
        doAnswer(i->{if(disabled.compareAndSet(false,true))plugin.onDisable();return null;}).when(manager).disablePlugin(plugin);
        Files.createDirectory(dir.resolve("state.json.tmp"));batch.run();
        assertTrue(disabled.get());assertEquals(1,claims.getAllClaims().size());
        verify(economy,times(1)).deposit(any(UUID.class),anyDouble());
        assertEquals(1,new ClaimManager(plugin).getAllClaims().size());
    }

    @Test void reloadStopsRemainingBulkSaleBatches() {
        Runnable batch=bulkSale(1);batch.run();assertEquals(1,claims.getAllClaims().size());
        features.reloadTasks();batch.run();
        assertEquals(1,claims.getAllClaims().size());verify(economy,times(1)).deposit(any(UUID.class),anyDouble());
    }

    @Test void changedEconomyDoesNotRemoveClaimWithoutConfirmedRefund() {
        Runnable batch=bulkSale(1);batch.run();assertEquals(1,claims.getAllClaims().size());
        when(economy.isEnabled()).thenReturn(false);batch.run();
        assertEquals(1,claims.getAllClaims().size());verify(economy,times(1)).deposit(any(UUID.class),anyDouble());
    }

    @Test void priceDifferenceStopsPayingWhenClaimStorageFails() throws Exception {
        Player owner=player("Owner",chunk(0,0));claims.claimChunk(owner,chunk(0,0));claims.claimChunk(owner,chunk(1,0));claims.saveAllSync();
        when(config.getClaimPrice()).thenReturn(500.0);Files.createDirectory(dir.resolve("claims.json.tmp"));
        claims.refundPriceDifference(500);
        verify(economy,times(1)).deposit(owner.getUniqueId(),500);
        assertEquals(1,transactions.unresolved().size());
        assertEquals(1,claims.getAllClaims().values().stream().filter(c->c.getRefundBasis(500)==1000).count());
        var restarted=new dev.dyclaim.manager.TransactionManager(plugin);
        assertEquals(1,restarted.unresolved().size());
    }
}
