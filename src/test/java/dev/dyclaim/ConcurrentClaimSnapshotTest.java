package dev.dyclaim;
import dev.dyclaim.manager.ClaimStore;
import dev.dyclaim.model.ClaimData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrentClaimSnapshotTest {
    @TempDir Path dir;
    @Test void detachedSnapshotsPreserveEverySerializedFieldAndDoNotShareTrustPermissions(){
        UUID owner=UUID.randomUUID(),friend=UUID.randomUUID();
        ClaimData claim=new ClaimData(owner,"Owner","world",1,2);claim.migrate(1000);claim.setClaimedAt(100);
        claim.setCoowner(UUID.randomUUID());claim.setName("Home");claim.setChoice("pvp",true);claim.setChoice("visitor-doors",true);
        claim.grant(friend,123456);claim.grantClan("SimpleClans:example",654321);
        claim.recordPrice(250);claim.setRefundBasis(200);claim.setMarketPrice(300.0);claim.setSpawn(17.5,64,33.5,90,30);
        ClaimData copy=claim.snapshot();
        assertEquals(dev.dyclaim.util.AtomicJson.GSON.toJsonTree(claim),dev.dyclaim.util.AtomicJson.GSON.toJsonTree(copy));
        copy.getTrustGrants().get(friend).permissions.clear();copy.getClanGrants().clear();copy.setChoice("visitor-doors",false);
        assertFalse(claim.getTrustGrants().get(friend).permissions.isEmpty());assertFalse(claim.getClanGrants().isEmpty());assertTrue(claim.getChoice("visitor-doors"));
    }
    @Test void savingDuringTrustEditsProducesAValidIndependentSnapshot() throws Exception {
        ClaimData claim=new ClaimData(UUID.randomUUID(),"Owner","world",0,0);claim.migrate(1000);
        List<UUID> users=new ArrayList<>();for(int i=0;i<128;i++)users.add(UUID.randomUUID());
        AtomicBoolean running=new AtomicBoolean(true);CountDownLatch started=new CountDownLatch(1);
        ExecutorService worker=Executors.newSingleThreadExecutor();
        Future<?> edits=worker.submit(()->{int i=0;started.countDown();while(running.get()){claim.grant(users.get(i++%users.size()),0);claim.removeTrusted(users.get(i%users.size()));claim.setChoice("visitor-doors",i%2==0);}});
        try {
            assertTrue(started.await(3,TimeUnit.SECONDS));ClaimStore store=new ClaimStore(dir.resolve("claims.json"));
            for(int i=0;i<30;i++)assertDoesNotThrow(()->store.save(Map.of(claim.getChunkKey(),claim)));
            ClaimData saved=store.load(1000).get(claim.getChunkKey());assertEquals(claim.getOwnerUUID(),saved.getOwnerUUID());assertNotSame(claim,saved);
        }finally{running.set(false);try{edits.get(5,TimeUnit.SECONDS);}finally{worker.shutdownNow();}}
    }
}
