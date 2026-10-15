package dev.dyclaim;
import dev.dyclaim.model.ClaimData;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ClaimRevisionConcurrencyTest {
    @Test void concurrentEditsCannotLoseRevisionInvalidation() throws Exception {
        ClaimData claim=new ClaimData(UUID.randomUUID(),"Owner","world",0,0);
        int threads=4,edits=100_000;
        CyclicBarrier start=new CyclicBarrier(threads);
        ExecutorService workers=Executors.newFixedThreadPool(threads);
        try {
            var tasks=new java.util.ArrayList<Future<?>>();
            for(int t=0;t<threads;t++)tasks.add(workers.submit(()->{start.await(3,TimeUnit.SECONDS);for(int i=0;i<edits;i++)claim.touch();return null;}));
            for(Future<?> task:tasks)task.get(10,TimeUnit.SECONDS);
            assertEquals((long)threads*edits,claim.getRevision(),"Every concurrent edit must invalidate earlier offers");
        }finally{workers.shutdownNow();}
    }
}
