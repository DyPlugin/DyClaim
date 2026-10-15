package dev.dyclaim;

import dev.dyclaim.manager.ConfirmationManager;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrentConfirmationTest extends ManagerFixture {
    @Test void concurrentConfirmationCanConsumeAnActionOnlyOnce() throws Exception {
        CyclicBarrier legacyCancel=new CyclicBarrier(2);
        ConfirmationManager manager=new ConfirmationManager(plugin){
            @Override public void cancelPending(UUID uuid){
                if(Thread.currentThread().getName().startsWith("confirmation-race"))try{legacyCancel.await(3,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}
                super.cancelPending(uuid);
            }
        };
        UUID actor=UUID.randomUUID();AtomicInteger executions=new AtomicInteger();
        manager.addPending(actor,ConfirmationManager.ActionType.CLAIM,ignored->executions.incrementAndGet(),ignored->{});
        ExecutorService workers=Executors.newFixedThreadPool(2,r->new Thread(r,"confirmation-race"));
        try {
            Future<Boolean> first=workers.submit(()->manager.confirm(actor));
            Future<Boolean> second=workers.submit(()->manager.confirm(actor));
            assertNotEquals(first.get(4,TimeUnit.SECONDS),second.get(4,TimeUnit.SECONDS));
            assertEquals(1,executions.get());
        }finally{workers.shutdownNow();}
    }
}
