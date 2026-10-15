package dev.dyclaim;
import dev.dyclaim.model.ClaimData;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class OwnershipSnapshotTest {
    @Test void snapshotsCannotObserveNewOwnershipWithOldNameOrAccess() throws Exception {
        UUID original=UUID.randomUUID(),recipient=UUID.randomUUID(),friend=UUID.randomUUID();
        CountDownLatch ownerChanged=new CountDownLatch(1),release=new CountDownLatch(1);
        ClaimData claim=new ClaimData(original,"Original","world",0,0){
            @Override public void setOwnerUUID(UUID owner){super.setOwnerUUID(owner);ownerChanged.countDown();try{assertTrue(release.await(3,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
        };
        claim.grant(friend,0);claim.setCoowner(UUID.randomUUID());claim.setMarketPrice(100.0);
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try {
            Future<?> transfer=workers.submit(()->claim.transferOwnership(recipient,"Recipient"));assertTrue(ownerChanged.await(3,TimeUnit.SECONDS));
            Future<ClaimData> snapshot=workers.submit(claim::snapshot);
            try{assertThrows(TimeoutException.class,()->snapshot.get(200,TimeUnit.MILLISECONDS),"A snapshot must wait for the complete ownership change");}finally{release.countDown();}
            transfer.get(3,TimeUnit.SECONDS);ClaimData saved=snapshot.get(3,TimeUnit.SECONDS);
            assertEquals(recipient,saved.getOwnerUUID());assertEquals("Recipient",saved.getOwnerName());assertFalse(saved.allows(friend,"build"));assertNull(saved.getCoowner());assertNull(saved.getMarketPrice());
        }finally{release.countDown();workers.shutdownNow();}
    }
}
