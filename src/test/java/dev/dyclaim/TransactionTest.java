package dev.dyclaim;

import org.junit.jupiter.api.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionTest extends ManagerFixture {
    @Test void definiteWithdrawalFailureLeavesClaimAndUnlocks(){UUID payer=UUID.randomUUID();when(economy.withdraw(payer,100)).thenReturn(false);AtomicInteger mutations=new AtomicInteger();assertFalse(transactions.execute("test","world:0:0",payer,null,100,0,mutations::incrementAndGet));assertEquals(0,mutations.get());assertFalse(transactions.isLocked("world:0:0"));}
    @Test void uncertainWithdrawalLocksAcrossRestartAndIsNeverRepeated(){UUID payer=UUID.randomUUID();when(economy.withdraw(payer,100)).thenReturn(false);when(economy.wasUncertain()).thenReturn(true);assertFalse(transactions.execute("test","world:0:0",payer,null,100,0,()->fail("must not mutate")));var restarted=new dev.dyclaim.manager.TransactionManager(plugin);assertTrue(restarted.isLocked("world:0:0"));assertFalse(restarted.execute("test","world:0:0",payer,null,100,0,()->fail("must not retry")));verify(economy,times(1)).withdraw(payer,100);}
    @Test void sellerFailureCompensatesBuyerWithoutChangingOwnership(){UUID buyer=UUID.randomUUID(),seller=UUID.randomUUID();when(economy.deposit(seller,95)).thenReturn(false);assertFalse(transactions.execute("market","world:0:0",buyer,seller,100,95,()->fail("must not mutate")));verify(economy).deposit(buyer,100);assertFalse(transactions.isLocked("world:0:0"));}
    @Test void uncertainSellerPaymentDoesNotBlindlyRefundBuyer(){UUID buyer=UUID.randomUUID(),seller=UUID.randomUUID();when(economy.deposit(seller,95)).thenReturn(false);when(economy.wasUncertain()).thenReturn(true);assertFalse(transactions.execute("market","world:0:0",buyer,seller,100,95,()->fail("must not mutate")));verify(economy,never()).deposit(buyer,100);assertTrue(transactions.isLocked("world:0:0"));}
    @Test void interruptedMutationKeepsAuditLock(){UUID buyer=UUID.randomUUID();assertFalse(transactions.execute("test","world:0:0",buyer,null,100,0,()->{throw new IllegalStateException("disk failure");}));assertTrue(transactions.isLocked("world:0:0"));}
    @Test void successfulOperationMutatesOnceAndPersists(){AtomicInteger mutations=new AtomicInteger();assertTrue(transactions.execute("free","world:0:0",null,null,0,0,mutations::incrementAndGet));assertEquals(1,mutations.get());assertFalse(new dev.dyclaim.manager.TransactionManager(plugin).isLocked("world:0:0"));}
}
