package dev.dyclaim;

import dev.dyclaim.manager.ClaimStore;
import dev.dyclaim.model.*;
import dev.dyclaim.util.AtomicJson;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClaimStoreTest {
    @TempDir Path dir;
    private ClaimData claim(){return new ClaimData(UUID.randomUUID(),"Owner","world",-1,2);}
    @Test void betaMigrationPreservesFieldsAndIsIdempotent() throws Exception {
        ClaimData original=claim();UUID trusted=UUID.randomUUID();original.addTrusted(trusted);original.setPvpDisabled(false);
        var tree=AtomicJson.GSON.toJsonTree(original).getAsJsonObject();for(String key:List.of("id","trustGrants","settings","clanGrants"))tree.remove(key);
        Path file=dir.resolve("claims.json");String beta=AtomicJson.GSON.toJson(Map.of(original.getChunkKey(),tree));Files.writeString(file,beta);
        ClaimStore store=new ClaimStore(file);ClaimData loaded=store.load(1500).get(original.getChunkKey());
        assertEquals(original.getOwnerUUID(),loaded.getOwnerUUID());assertEquals(original.getClaimedAt(),loaded.getClaimedAt());assertFalse(loaded.isPvpDisabled());assertTrue(loaded.allows(trusted,"containers"));assertEquals(1500,loaded.getRefundBasis(0));
        assertEquals(beta,Files.readString(dir.resolve("claims.beta-backup.json")));
        UUID id=loaded.getId();String migrated=Files.readString(file);assertEquals(id,store.load(999).get(original.getChunkKey()).getId());assertEquals(migrated,Files.readString(file));
    }
    @Test void corruptDataIsNeverReplacedOrMigrated() throws Exception {
        for(String invalid:List.of("", "null", "{bad", "[]", "{\"schemaVersion\":99,\"claims\":{}}", "{\"world:0:0\":null}")) {
            Path file=dir.resolve("claims.json");Files.writeString(file,invalid);assertThrows(java.io.IOException.class,()->new ClaimStore(file).load(1000));assertEquals(invalid,Files.readString(file));assertFalse(Files.exists(dir.resolve("claims.beta-backup.json")));
        }
    }
    @Test void rejectsMismatchedKeyAndDuplicateIds() throws Exception {
        ClaimData c=claim();assertThrows(java.io.IOException.class,()->new ClaimStore(dir.resolve("claims.json")).save(Map.of("wrong",c)));
        ClaimData duplicate=AtomicJson.GSON.fromJson(AtomicJson.GSON.toJson(c),ClaimData.class);duplicate.setChunkX(3);
        Path file=dir.resolve("claims.json");AtomicJson.write(file,Map.of("schemaVersion",2,"claims",Map.of(c.getChunkKey(),c,duplicate.getChunkKey(),duplicate)));
        assertThrows(java.io.IOException.class,()->new ClaimStore(file).load(1000));
    }
    @Test void temporaryTrustPersistsAndExpiresWithoutCleanup() throws Exception {
        ClaimData c=claim();UUID user=UUID.randomUUID();c.grant(user,System.currentTimeMillis()-1);
        Path file=dir.resolve("claims.json");new ClaimStore(file).save(Map.of(c.getChunkKey(),c));ClaimData loaded=new ClaimStore(file).load(1000).get(c.getChunkKey());assertFalse(loaded.allows(user,"build"));
        c.grant(user,System.currentTimeMillis()+60_000);c.getTrustGrants().get(user).permissions= new HashSet<>(Set.of("doors"));assertTrue(c.allows(user,"doors"));assertFalse(c.allows(user,"containers"));assertFalse(c.canManage(user));
    }
    @Test void atomicReplacementKeepsPreviousValidBackup() throws Exception {
        Path file=dir.resolve("data.json");AtomicJson.write(file,Map.of("value",1));String first=Files.readString(file);AtomicJson.write(file,Map.of("value",2));assertEquals(first,Files.readString(dir.resolve("data.json.bak")));assertFalse(Files.exists(dir.resolve("data.json.tmp")));
    }
}
