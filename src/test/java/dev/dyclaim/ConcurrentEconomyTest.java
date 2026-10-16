package dev.dyclaim;

import dev.dyclaim.manager.EconomyManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConcurrentEconomyTest extends ManagerFixture {
    @Test void successfulPaymentOnAnotherThreadCannotClearAnAmbiguousPayment() throws Exception {
        Economy provider=mock(Economy.class);
        OfflinePlayer ambiguous=mock(OfflinePlayer.class),successful=mock(OfflinePlayer.class);
        when(provider.withdrawPlayer(ambiguous,1)).thenThrow(new IllegalStateException("Provider disconnected after receiving payment"));
        when(provider.withdrawPlayer(successful,1)).thenReturn(new EconomyResponse(1,9,EconomyResponse.ResponseType.SUCCESS,""));
        EconomyManager manager=new EconomyManager(plugin,()->provider);
        CountDownLatch failed=new CountDownLatch(1),otherFinished=new CountDownLatch(1);
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> uncertain=workers.submit(()->{assertFalse(manager.withdraw(ambiguous,1));failed.countDown();assertTrue(otherFinished.await(3,TimeUnit.SECONDS));return manager.wasUncertain();});
            Future<Boolean> certain=workers.submit(()->{assertTrue(failed.await(3,TimeUnit.SECONDS));assertTrue(manager.withdraw(successful,1));boolean result=manager.wasUncertain();otherFinished.countDown();return result;});
            assertFalse(certain.get(4,TimeUnit.SECONDS));
            assertTrue(uncertain.get(4,TimeUnit.SECONDS),"Only the same execution thread may replace its payment outcome");
        }finally{workers.shutdownNow();}
    }
}
