package dev.dyclaim;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConcurrentClaimInsertionTest extends ManagerFixture {
    @Test void simultaneousFreeAcquisitionsCannotReplaceAClaimOrLeaveAGhostOwnerIndex() throws Exception {
        var chunk=chunk(0,0);var firstPlayer=player("First",chunk);var secondPlayer=player("Second",chunk);
        CyclicBarrier beforeInsert=new CyclicBarrier(2);
        when(economy.isEnabled()).thenAnswer(i->{beforeInsert.await(3,TimeUnit.SECONDS);return false;});
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first=workers.submit(()->claims.claimChunk(firstPlayer,chunk));
            Future<Boolean> second=workers.submit(()->claims.claimChunk(secondPlayer,chunk));
            boolean firstResult=first.get(4,TimeUnit.SECONDS),secondResult=second.get(4,TimeUnit.SECONDS);
            assertNotEquals(firstResult,secondResult,"Exactly one claimant must acquire the chunk");
            var winner=firstResult?firstPlayer:secondPlayer;var loser=firstResult?secondPlayer:firstPlayer;
            assertEquals(winner.getUniqueId(),claims.getByKey("world:0:0").getOwnerUUID());
            assertEquals(1,claims.getPlayerClaimCount(winner.getUniqueId()));assertEquals(0,claims.getPlayerClaimCount(loser.getUniqueId()));
        }finally{workers.shutdownNow();}
    }
}
