package dev.dyclaim;
import dev.dyclaim.manager.TransactionManager;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionReservationTest extends ManagerFixture {
    @Test void priceDifferenceIsRecalculatedAfterObtainingReservation(){
        var chunk=chunk(0,0);var owner=player("Owner",chunk);assertTrue(claims.claimChunk(owner,chunk));
        when(config.getClaimPrice()).thenReturn(900.0);
        var watched=spy(transactions);when(plugin.getTransactionManager()).thenReturn(watched);
        var calls=new AtomicInteger();
        doAnswer(i->{if(calls.getAndIncrement()==0)assertEquals(1,claims.refundPriceDifference(100));return i.callRealMethod();})
            .when(watched).reserve("world:0:0");
        assertEquals(0,claims.refundPriceDifference(100));
        verify(economy,times(1)).deposit(owner.getUniqueId(),100);
    }
    @Test void priceDifferenceDoesNotPayForClaimRemovedBeforeReservation(){
        var chunk=chunk(0,0);var owner=player("Owner",chunk);assertTrue(claims.claimChunk(owner,chunk));
        when(config.getClaimPrice()).thenReturn(900.0);
        var watched=spy(transactions);when(plugin.getTransactionManager()).thenReturn(watched);
        doAnswer(i->{assertTrue(claims.remove("world:0:0"));return i.callRealMethod();}).when(watched).reserve("world:0:0");
        assertEquals(0,claims.refundPriceDifference(100));verify(economy,never()).deposit(any(UUID.class),anyDouble());
    }
    @Test void providerCallbackCannotRemoveClaimButItsAuthorizedRefundMutationCan(){
        var chunk=chunk(0,0);var owner=player("Owner",chunk);assertTrue(claims.claimChunk(owner,chunk));var claim=claims.getClaimAt(chunk);
        when(economy.deposit(eq(owner.getUniqueId()),anyDouble())).thenAnswer(i->{assertFalse(claims.remove(claim.getChunkKey()),"Unrelated mutation ran inside provider callback");return true;});
        assertTrue(claims.refundAndRemove(claim));assertNull(claims.getClaimAt(chunk));assertTrue(transactions.unresolved().isEmpty());
    }
    @Test void reservedChunkRejectsOtherTransactionsBeforeAnyPayment(){
        try(var lease=transactions.reserve("world:0:0")){
            assertNotNull(lease);assertTrue(transactions.isLocked("world:0:0"));
            assertFalse(transactions.execute("claim","world:0:0",UUID.randomUUID(),null,100,0,()->fail("Mutation ran")));
            verify(economy,never()).withdraw(any(UUID.class),anyDouble());
        }
        assertFalse(transactions.isLocked("world:0:0"));
    }
    @Test void reservationCanBeConsumedOnlyOnce(){
        var mutation=new AtomicInteger();UUID payer=UUID.randomUUID();
        try(var lease=transactions.reserve("world:0:0")){
            assertTrue(transactions.execute(lease,"claim","world:0:0",payer,null,100,0,mutation::incrementAndGet));
            assertFalse(transactions.execute(lease,"claim","world:0:0",payer,null,100,0,mutation::incrementAndGet));
        }
        assertEquals(1,mutation.get());verify(economy,times(1)).withdraw(payer,100);
    }
    @Test void closingOldLeaseAgainCannotUnlockNewReservation(){
        var old=transactions.reserve("world:0:0");old.close();
        try(var next=transactions.reserve("world:0:0")){
            assertNotNull(next);old.close();assertTrue(transactions.isLocked("world:0:0"));
        }
    }
    @Test void durableIntentFailureRemainsLockedAfterReservationScopeCloses(){
        var manager=new TransactionManager(plugin,(path,entries)->{throw new java.io.IOException("Disk write failed");});
        try(var lease=manager.reserve("world:0:0")){
            assertThrows(IllegalStateException.class,()->manager.execute(lease,"claim","world:0:0",null,null,0,0,()->fail("Mutation ran")));
        }
        assertTrue(manager.isLocked("world:0:0"));assertEquals(1,manager.unresolved().size());
    }
}
