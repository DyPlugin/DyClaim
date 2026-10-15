package dev.dyclaim;
import dev.dyclaim.manager.TransactionManager;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ConcurrentTransactionTest extends ManagerFixture {
    @Test void simultaneousTransactionsForOneChunkCannotBothStartPayment() throws Exception {
        CyclicBarrier legacyCheck=new CyclicBarrier(2);
        TransactionManager manager=new TransactionManager(plugin,(file,entries)->{}){
            @Override public boolean isLocked(String key){boolean result=super.isLocked(key);try{legacyCheck.await(2,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}return result;}
        };
        CountDownLatch paymentStarted=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicInteger withdrawals=new AtomicInteger(),mutations=new AtomicInteger();
        when(economy.withdraw(any(UUID.class),anyDouble())).thenAnswer(i->{withdrawals.incrementAndGet();paymentStarted.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return true;});
        ExecutorService workers=Executors.newFixedThreadPool(2);UUID payer=UUID.randomUUID();
        CompletableFuture<Boolean> first=CompletableFuture.supplyAsync(()->manager.execute("claim","world:0:0",payer,null,1,0,mutations::incrementAndGet),workers);
        CompletableFuture<Boolean> second=CompletableFuture.supplyAsync(()->manager.execute("claim","world:0:0",payer,null,1,0,mutations::incrementAndGet),workers);
        try {
            assertTrue(paymentStarted.await(3,TimeUnit.SECONDS));
            Object loser;try{loser=CompletableFuture.anyOf(first,second).get(2,TimeUnit.SECONDS);}catch(TimeoutException e){fail("A competing transaction must reject before starting a second payment");return;}
            assertEquals(false,loser);assertEquals(1,withdrawals.get());
        }finally{release.countDown();try{CompletableFuture.allOf(first,second).get(5,TimeUnit.SECONDS);}finally{workers.shutdownNow();}}
        assertEquals(1,mutations.get());
    }
}
