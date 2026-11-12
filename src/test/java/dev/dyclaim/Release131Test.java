package dev.dyclaim;

import dev.dyclaim.api.DyClaimAPI;
import dev.dyclaim.api.event.ClaimTransferEvent;
import dev.dyclaim.api.event.ClaimTrustChangeEvent;
import dev.dyclaim.manager.ClaimManager;
import dev.dyclaim.model.ClaimData;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Developer write API for estate and economy plugins (1.3.1). */
class Release131Test extends ManagerFixture {
    private Field instance;
    private Object previousInstance;
    private final List<Event> fired = new CopyOnWriteArrayList<>();

    @BeforeEach void enableApi() throws Exception {
        instance = DyClaim.class.getDeclaredField("instance"); instance.setAccessible(true);
        previousInstance = instance.get(null); instance.set(null, plugin); when(plugin.isEnabled()).thenReturn(true);
        bukkit.when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(true);
        var manager = plugin.getServer().getPluginManager();
        doAnswer(i -> { fired.add(i.getArgument(0)); return null; }).when(manager).callEvent(any());
        for (HandlerList handlers : List.of(ClaimTransferEvent.getHandlerList(), ClaimTrustChangeEvent.getHandlerList()))
            handlers.register(new RegisteredListener(mock(Listener.class), (l, e) -> {}, EventPriority.NORMAL, mock(org.bukkit.plugin.Plugin.class), false));
    }
    @AfterEach void restoreApi() throws Exception { HandlerList.unregisterAll(); instance.set(null, previousInstance); }

    private ClaimData claimed(String owner, int x, int z) {
        var c = chunk(x, z); claims.claimChunk(player(owner, c), c); return claims.getClaimAt("world", x, z);
    }
    private static UUID offline(String name) { return UUID.nameUUIDFromBytes(("offline:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }

    @Test void claimsAreFoundByIdAndMissesAreEmpty() {
        ClaimData claim = claimed("Owner", 3, -4);
        var view = DyClaimAPI.getClaimById(claim.getId()).orElseThrow();
        assertEquals(3, view.getChunkX()); assertEquals(-4, view.getChunkZ()); assertEquals(claim.getOwnerUUID(), view.getOwner());
        assertTrue(DyClaimAPI.getClaimById(UUID.randomUUID()).isEmpty());
        claims.remove(claim.getChunkKey());
        assertTrue(DyClaimAPI.getClaimById(claim.getId()).isEmpty(), "removed claims leave the id index");
        assertNull(claims.getById(null));
    }

    @Test void idIndexIsRebuiltWhenClaimsAreLoaded() {
        ClaimData claim = claimed("Owner", 1, 1);
        claims.saveAllSync();
        var reloaded = new ClaimManager(plugin);
        assertNotNull(reloaded.getById(claim.getId()));
        assertSame(reloaded.getByKey("world:1:1"), reloaded.getById(claim.getId()));
    }

    @Test void claimsTransferToOfflinePlayersAndClearTheOldOwnersState() {
        ClaimData claim = claimed("Seller", 0, 0); UUID seller = claim.getOwnerUUID(); UUID buyer = offline("Buyer");
        UUID friend = offline("Friend"), partner = offline("Partner");
        claim.grant(friend, 0); claims.changeCoowner(claim, partner, seller, claim.getRevision()); claim.setMarketPrice(500.0); claim.setName("Farm");
        assertNull(org.bukkit.Bukkit.getPlayer(buyer), "the new owner is not online");

        assertTrue(DyClaimAPI.transferClaim(claim.getId(), buyer, "Buyer"));

        var view = DyClaimAPI.getClaimById(claim.getId()).orElseThrow();
        assertEquals(buyer, view.getOwner()); assertEquals("Buyer", view.getOwnerName());
        assertTrue(view.getCoowner().isEmpty()); assertTrue(view.getTrustedPlayers().isEmpty());
        assertNull(claim.getMarketPrice(), "an open market listing ends with the transfer");
        assertEquals(Optional.of("Farm"), view.getName());
        assertEquals(0, DyClaimAPI.getClaimCount(seller)); assertEquals(0, DyClaimAPI.getClaimCount(partner));
        assertEquals(List.of(claim.getId()), DyClaimAPI.getClaims(buyer).stream().map(c -> c.getId()).toList());
        var event = (ClaimTransferEvent) fired.stream().filter(ClaimTransferEvent.class::isInstance).findFirst().orElseThrow();
        assertEquals(seller, event.getPreviousOwner()); assertEquals(buyer, event.getNewOwner()); assertEquals(buyer, event.getClaim().getOwner());

        claims.saveAllSync();
        var reloaded = new ClaimManager(plugin).getById(claim.getId());
        assertEquals(buyer, reloaded.getOwnerUUID()); assertEquals("Buyer", reloaded.getOwnerName());
    }

    @Test void transferKeepsNameUniquePerOwnerAndFallsBackToTheUuidAsName() {
        UUID buyer = offline("Buyer");
        ClaimData first = claimed("Seller", 0, 0); first.setName("Home");
        ClaimData second = claimed("Other", 5, 5); second.setName("home");
        assertTrue(DyClaimAPI.transferClaim(first.getId(), buyer, null));
        assertTrue(DyClaimAPI.transferClaim(second.getId(), buyer, " "));
        assertEquals(buyer.toString(), first.getOwnerName());
        assertNull(second.getName(), "a duplicate claim name of the new owner is dropped");
        assertEquals(2, DyClaimAPI.getClaimCount(buyer));
    }

    @Test void transferIsRefusedForMissingServerSameOwnerAndLockedClaims() {
        UUID buyer = offline("Buyer");
        assertFalse(DyClaimAPI.transferClaim(UUID.randomUUID(), buyer, "Buyer"));
        ClaimData server = claims.serverClaim("world", 9, 9);
        assertFalse(DyClaimAPI.transferClaim(server.getId(), buyer, "Buyer"));
        ClaimData claim = claimed("Seller", 0, 0);
        assertFalse(DyClaimAPI.transferClaim(claim.getId(), claim.getOwnerUUID(), "Seller"));
        assertFalse(DyClaimAPI.transferClaim(claim.getId(), ClaimData.SERVER, "Server"));
        try (var reservation = transactions.reserve(claim.getChunkKey())) {
            assertNotNull(reservation);
            assertFalse(DyClaimAPI.transferClaim(claim.getId(), buyer, "Buyer"), "a running DyClaim payment keeps its lock");
        }
        assertTrue(DyClaimAPI.transferClaim(claim.getId(), buyer, "Buyer"));
        assertEquals(1, fired.stream().filter(ClaimTransferEvent.class::isInstance).count());
    }

    @Test void timedTrustIsGrantedReplacedAndRemoved() {
        ClaimData claim = claimed("Owner", 0, 0); UUID tenant = offline("Tenant");
        long until = System.currentTimeMillis() + 3_600_000L;

        assertTrue(DyClaimAPI.setTrust(claim.getId(), tenant, Set.of("build", "containers"), until));
        assertEquals(Set.of("build", "containers"), DyClaimAPI.getTrustRights(claim.getId(), tenant));
        assertEquals(until, claim.getTrustGrants().get(tenant).expiresAt);
        var view = DyClaimAPI.getClaimById(claim.getId()).orElseThrow();
        assertTrue(view.allows(tenant, "build")); assertFalse(view.allows(tenant, "doors")); assertTrue(view.getTrustedPlayers().contains(tenant));

        assertTrue(DyClaimAPI.setTrust(claim.getId(), tenant, Set.of("doors"), 0));
        assertEquals(Set.of("doors"), DyClaimAPI.getTrustRights(claim.getId(), tenant), "a new grant replaces the old rights");
        assertEquals(0, claim.getTrustGrants().get(tenant).expiresAt);

        assertTrue(DyClaimAPI.removeTrust(claim.getId(), tenant));
        assertTrue(DyClaimAPI.getTrustRights(claim.getId(), tenant).isEmpty());
        assertFalse(DyClaimAPI.removeTrust(claim.getId(), tenant), "nothing left to remove");
        assertFalse(DyClaimAPI.removeTrust(UUID.randomUUID(), tenant));

        var trustEvents = fired.stream().filter(ClaimTrustChangeEvent.class::isInstance).map(ClaimTrustChangeEvent.class::cast).toList();
        assertEquals(List.of(true, true, false), trustEvents.stream().map(ClaimTrustChangeEvent::isTrusted).toList());
        assertTrue(trustEvents.stream().allMatch(e -> e.getPlayer().equals(tenant)));
    }

    @Test void expiredGrantsReportNoRightsAndTimedGrantsSurviveReload() {
        ClaimData claim = claimed("Owner", 0, 0); UUID tenant = offline("Tenant");
        long until = System.currentTimeMillis() + 86_400_000L;
        assertTrue(DyClaimAPI.setTrust(claim.getId(), tenant, Set.of("build", "teleport"), until));
        claims.saveAllSync();
        ClaimData reloaded = new ClaimManager(plugin).getById(claim.getId());
        assertEquals(Set.of("build", "teleport"), reloaded.activeTrustRights(tenant));
        assertEquals(until, reloaded.getTrustGrants().get(tenant).expiresAt);

        claim.getTrustGrants().get(tenant).expiresAt = System.currentTimeMillis() - 1;
        assertTrue(DyClaimAPI.getTrustRights(claim.getId(), tenant).isEmpty());
        assertTrue(DyClaimAPI.getTrustRights(UUID.randomUUID(), tenant).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> DyClaimAPI.getTrustRights(claim.getId(), tenant).add("build"));
    }

    @Test void invalidTrustRequestsChangeNothing() {
        ClaimData claim = claimed("Owner", 0, 0); UUID tenant = offline("Tenant"); long future = System.currentTimeMillis() + 60_000L;
        assertFalse(DyClaimAPI.setTrust(UUID.randomUUID(), tenant, Set.of("build"), 0));
        assertFalse(DyClaimAPI.setTrust(claim.getId(), tenant, Set.of("build", "fly"), future), "unknown right names are refused");
        assertFalse(DyClaimAPI.setTrust(claim.getId(), tenant, Set.of("BUILD"), future), "right names match /claim trustperm exactly");
        assertFalse(DyClaimAPI.setTrust(claim.getId(), tenant, Set.of(), future));
        assertFalse(DyClaimAPI.setTrust(claim.getId(), tenant, Set.of("build"), -5));
        assertFalse(DyClaimAPI.setTrust(claim.getId(), tenant, Set.of("build"), System.currentTimeMillis() - 1000), "an expiry in the past is refused");
        assertFalse(DyClaimAPI.setTrust(claim.getId(), claim.getOwnerUUID(), Set.of("build"), 0), "owners are never trusted");
        assertTrue(claim.getTrustGrants().isEmpty());
        assertTrue(fired.isEmpty());
    }

    @Test void trustingLiftsAVisitorBan() {
        ClaimData claim = claimed("Owner", 0, 0); UUID tenant = offline("Tenant");
        claim.banVisitor(tenant, 0);
        assertTrue(DyClaimAPI.setTrust(claim.getId(), tenant, Set.of("doors"), 0));
        assertFalse(claim.isVisitorBanned(tenant));
    }

    @Test void writeCallsFromManyThreadsKeepIndexesConsistent() throws Exception {
        HandlerList.unregisterAll();
        List<ClaimData> owned = new ArrayList<>();
        for (int i = 0; i < 16; i++) owned.add(claimed("Seller", i * 3, 0));
        UUID seller = owned.get(0).getOwnerUUID(), buyer = offline("Buyer");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (ClaimData claim : owned) {
                results.add(pool.submit(() -> DyClaimAPI.transferClaim(claim.getId(), buyer, "Buyer")));
                results.add(pool.submit(() -> DyClaimAPI.setTrust(claim.getId(), offline("T" + claim.getChunkX()), Set.of("build"), 0)));
            }
            for (Future<Boolean> result : results) result.get(10, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(16, DyClaimAPI.getClaimCount(buyer)); assertEquals(0, DyClaimAPI.getClaimCount(seller));
        assertTrue(owned.stream().allMatch(c -> c.getOwnerUUID().equals(buyer)));
    }

    @Test void writeMethodsRefuseWhenDyClaimIsDisabled() throws Exception {
        instance.set(null, null);
        assertThrows(IllegalStateException.class, () -> DyClaimAPI.transferClaim(UUID.randomUUID(), UUID.randomUUID(), "x"));
        assertThrows(IllegalStateException.class, () -> DyClaimAPI.setTrust(UUID.randomUUID(), UUID.randomUUID(), Set.of("build"), 0));
        assertThrows(IllegalStateException.class, () -> DyClaimAPI.getClaimById(UUID.randomUUID()));
    }
}
