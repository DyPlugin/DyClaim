package dev.dyclaim;

import dev.dyclaim.listener.ClaimProtectionListener;
import dev.dyclaim.manager.MessageManager;
import dev.dyclaim.model.ClaimData;
import dev.dyclaim.model.TrustGrant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Protection hot path (1.3.1): evaluation order, lock-free claim reads, coordinate lookups and cached denial texts. */
class ProtectionHotPathTest extends ManagerFixture {

    /** A player that fails the test if any permission is looked up. */
    private Player noPermissionLookups(Player player) {
        when(player.hasPermission(anyString())).thenThrow(new AssertionError("permission lookup on the claim-level fast path"));
        return player;
    }

    @Test void ownerCoownerAndTrustedPlayersAreAllowedWithoutAnyPermissionLookup() {
        Chunk inside = chunk(0, 0);
        Player owner = player("Owner", inside), coowner = player("Coowner", inside), friend = player("Friend", inside);
        assertTrue(claims.claimChunk(owner, inside));
        ClaimData claim = claims.getClaimAt(inside);
        claim.setCoowner(coowner.getUniqueId());
        claim.setTrust(friend.getUniqueId(), Set.of("build"), 0);
        for (Player player : List.of(owner, coowner, friend)) {
            noPermissionLookups(player);
            assertTrue(access.allows(player, claim, "build"), player.getName());
            assertTrue(access.member(player, claim), player.getName());
        }
        // Owner and coowner hold every right, so no right name is compared for them.
        assertTrue(access.allows(owner, claim, "teleport"));
        assertTrue(access.allows(coowner, claim, "containers"));
    }

    @Test void reorderedChecksGiveTheSameAnswersForVisitorsAndBypass() {
        Chunk inside = chunk(0, 0);
        Player owner = player("Owner", inside), visitor = player("Visitor", inside), admin = player("Admin", inside);
        claims.claimChunk(owner, inside);
        ClaimData claim = claims.getClaimAt(inside);
        assertFalse(access.allows(visitor, claim, "build"));
        assertFalse(access.member(visitor, claim));
        verify(visitor, atLeastOnce()).hasPermission("dyclaim.admin.bypass");
        when(admin.hasPermission("dyclaim.admin.bypass")).thenReturn(true);
        assertTrue(access.allows(admin, claim, "build"));
        assertTrue(access.member(admin, claim));
        access.setBypass(admin, false);
        assertFalse(access.allows(admin, claim, "build"));
        assertFalse(access.member(admin, claim));
        // Doors still follow the claim's visitor policy after the bypass check.
        assertEquals(access.enabled(claim, "visitor-doors"), access.allows(visitor, claim, "doors"));
    }

    @Test void blockBreakFindsTheClaimFromCoordinatesWithoutChunkOrPermissionLookups() {
        Chunk inside = chunk(2, 3);
        Player owner = player("Owner", inside), visitor = player("Visitor", inside);
        claims.claimChunk(owner, inside);
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world); when(block.getX()).thenReturn(2 * 16 + 5); when(block.getZ()).thenReturn(3 * 16 + 15);
        when(block.getChunk()).thenThrow(new AssertionError("getChunk() on the protection path"));
        var listener = new ClaimProtectionListener(plugin);
        noPermissionLookups(owner);
        BlockBreakEvent allowed = new BlockBreakEvent(block, owner);
        listener.onBlockBreak(allowed);
        assertFalse(allowed.isCancelled());
        BlockBreakEvent denied = new BlockBreakEvent(block, visitor);
        listener.onBlockBreak(denied);
        assertTrue(denied.isCancelled());
        verify(visitor).sendMessage("protection-block-break");
        // Negative block coordinates fall in negative chunks, as getChunk() would report them.
        Chunk negative = chunk(-1, -1);
        claims.claimChunk(player("West", negative), negative);
        Block corner = mock(Block.class);
        when(corner.getWorld()).thenReturn(world); when(corner.getX()).thenReturn(-1); when(corner.getZ()).thenReturn(-16);
        when(corner.getChunk()).thenThrow(new AssertionError("getChunk() on the protection path"));
        BlockBreakEvent west = new BlockBreakEvent(corner, visitor);
        listener.onBlockBreak(west);
        assertTrue(west.isCancelled());
    }

    @Test void actorClaimUsesBlockCoordinatesOnSingleThreadedServers() {
        Chunk inside = chunk(4, -2);
        Player owner = player("Owner", inside);
        claims.claimChunk(owner, inside);
        Location at = owner.getLocation();
        when(at.getChunk()).thenThrow(new AssertionError("getChunk() on the protection path"));
        assertSame(claims.getClaimAt(inside), access.actorClaim(owner));
    }

    @Test void protectionReadsDoNotWaitForAWriterHoldingTheClaimLock() throws Exception {
        Chunk inside = chunk(0, 0);
        Player owner = player("Owner", inside), friend = player("Friend", inside), visitor = player("Visitor", inside);
        claims.claimChunk(owner, inside);
        ClaimData claim = claims.getClaimAt(inside);
        claim.setTrust(friend.getUniqueId(), Set.of("build"), 0);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        Thread writer = new Thread(() -> { synchronized (claim) { held.countDown(); try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) { } } });
        writer.start();
        ExecutorService reader = Executors.newSingleThreadExecutor();
        try {
            assertTrue(held.await(5, TimeUnit.SECONDS));
            Future<List<Object>> reads = reader.submit(() -> List.of(
                    claim.getOwnerUUID(), claim.getWorld(), claim.getChunkX(), claim.getChunkZ(),
                    claim.canManage(owner.getUniqueId()), claim.allows(friend.getUniqueId(), "build"), claim.allows(visitor.getUniqueId(), "build"),
                    claim.isTrusted(friend.getUniqueId()), claim.isAllowed(owner.getUniqueId()), claim.isServerClaim(), claim.getClanGrants().isEmpty()));
            assertEquals(List.of(owner.getUniqueId(), "world", 0, 0, true, true, false, true, true, false, true), reads.get(2, TimeUnit.SECONDS));
        } finally {
            release.countDown(); writer.join(5000); reader.shutdownNow();
        }
    }

    @Test void writersPublishNewGrantMapsAndKeepEarlierViewsUnchanged() {
        Chunk inside = chunk(0, 0);
        Player owner = player("Owner", inside);
        claims.claimChunk(owner, inside);
        ClaimData claim = claims.getClaimAt(inside);
        UUID friend = UUID.randomUUID(), other = UUID.randomUUID();
        Map<UUID, TrustGrant> before = claim.getTrustGrants();
        claim.grant(friend, 0);
        assertFalse(before.containsKey(friend), "a published map is never changed");
        assertTrue(claim.isTrusted(friend));
        Map<UUID, TrustGrant> withFriend = claim.getTrustGrants();
        TrustGrant grant = withFriend.get(friend);
        Set<String> rights = grant.permissions;
        assertTrue(claim.changeTrustPermission(friend, "build", false));
        assertSame(grant, claim.getTrustGrants().get(friend), "a rights change keeps the grant and its expiry");
        assertTrue(rights.contains("build"), "a published rights set is never changed");
        assertFalse(claim.allows(friend, "build"));
        claim.grantRights(friend, Set.of("build"));
        assertTrue(claim.allows(friend, "build"));
        claim.grantRights(other, Set.of("doors"));
        assertTrue(claim.allows(other, "doors"));
        assertFalse(claim.allows(other, "build"));
        assertTrue(claim.removeTrusted(friend));
        assertTrue(withFriend.containsKey(friend));
        assertFalse(claim.isTrusted(friend));
        Map<String, TrustGrant> clans = claim.getClanGrants();
        claim.grantClan("group:builders", 0);
        assertTrue(clans.isEmpty());
        assertTrue(claim.getClanGrants().containsKey("group:builders"));
        assertTrue(claim.removeClan("group:builders"));
        assertFalse(claim.removeClan("group:builders"));
        // The saved copy still carries every live grant.
        assertTrue(claim.snapshot().getTrustGrants().containsKey(other));
    }

    @Test void concurrentWritersNeverLoseAGrantAndReadersAlwaysSeeTheOwner() throws Exception {
        Chunk inside = chunk(0, 0);
        Player owner = player("Owner", inside);
        claims.claimChunk(owner, inside);
        ClaimData claim = claims.getClaimAt(inside);
        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < 400; i++) players.add(UUID.randomUUID());
        ExecutorService pool = Executors.newFixedThreadPool(5);
        AtomicInteger wrongAnswers = new AtomicInteger();
        try {
            List<Future<?>> work = new ArrayList<>();
            for (int lane = 0; lane < 4; lane++) {
                int offset = lane;
                work.add(pool.submit(() -> { for (int i = offset; i < players.size(); i += 4) claim.grant(players.get(i), 0); }));
            }
            work.add(pool.submit(() -> { for (int i = 0; i < 20000; i++) if (!claim.allows(owner.getUniqueId(), "build")) wrongAnswers.incrementAndGet(); }));
            for (Future<?> future : work) future.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, wrongAnswers.get());
        for (UUID player : players) assertTrue(claim.isTrusted(player));
        assertEquals(players.size(), claim.getTrustGrants().size());
    }

    private MessageManager messages(boolean components) throws Exception {
        Files.writeString(dir.resolve("messages_en.yml"),
                "denied: \"&cYou can't break blocks in this claim.\"\n"
                        + "linked: \"&cRead dynastyplugins.com first.\"\n"
                        + "lines: \"&cFirst line\\n&7Second line\"\n"
                        + "named: \"&7[You entered {owner}'s claim]\"\n");
        Files.writeString(dir.resolve("messages_tr.yml"), "denied: \"&cBu claim'de blok kıramazsınız.\"\n");
        when(config.getLang()).thenReturn("en");
        when(config.getPrefix()).thenReturn("&7[&dDyClaim&7]");
        return components ? new MessageManager(plugin) : newTextOnly();
    }

    private MessageManager newTextOnly() throws Exception {
        var constructor = MessageManager.class.getDeclaredConstructor(DyClaim.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(plugin, false);
    }

    @Test void prefixedTextIsCachedAndRebuiltAfterReloadPrefixAndLanguageChanges() throws Exception {
        MessageManager messages = messages(true);
        Player player = player("Reader", chunk(0, 0));
        String first = messages.getPrefixed(player, "denied");
        assertEquals(messages.getPrefix() + messages.getMessage(player, "denied"), first);
        assertEquals("§7[§dDyClaim§7] §cYou can't break blocks in this claim.", first);
        assertSame(first, messages.getPrefixed(player, "denied"), "second lookup reuses the finished text");

        Files.writeString(dir.resolve("messages_en.yml"), "denied: \"&4Edited denial.\"\n");
        messages.reload();
        assertEquals("§7[§dDyClaim§7] §4Edited denial.", messages.getPrefixed(player, "denied"));

        when(config.getPrefix()).thenReturn("&8[Claims]");
        assertEquals("§8[Claims] §4Edited denial.", messages.getPrefixed(player, "denied"));

        when(config.getLang()).thenReturn("tr");
        assertEquals("§8[Claims] §cBu claim'de blok kıramazsınız.", messages.getPrefixed(player, "denied"));
        when(config.getLang()).thenReturn("auto");
        when(player.getLocale()).thenReturn("tr_tr");
        assertEquals("§8[Claims] §cBu claim'de blok kıramazsınız.", messages.getPrefixed(player, "denied"));
        when(player.getLocale()).thenReturn("en_us");
        assertEquals("§8[Claims] §4Edited denial.", messages.getPrefixed(player, "denied"));
    }

    @Test void placeholderMessagesAreNotCachedAndStillEscapeTags() throws Exception {
        MessageManager messages = messages(true);
        Player player = player("Reader", chunk(0, 0));
        String first = messages.getPrefixed(player, "named", Map.of("{owner}", "<red>Alex"));
        assertEquals("§7[§dDyClaim§7] §7[You entered <red>Alex's claim]", first);
        assertEquals("§7[§dDyClaim§7] §7[You entered Sam's claim]", messages.getPrefixed(player, "named", Map.of("{owner}", "Sam")));
    }

    @Test void adventureServersSendTheCachedComponentToPlayers() throws Exception {
        MessageManager messages = messages(true);
        Player player = player("Reader", chunk(0, 0));
        messages.sendPrefixed(player, "denied");
        messages.sendPrefixed(player, "denied");
        Component expected = LegacyComponentSerializer.legacySection().deserialize("§7[§dDyClaim§7] §cYou can't break blocks in this claim.");
        verify(player, times(2)).sendMessage(expected);
        verify(player, never()).sendMessage(anyString());
        // Bukkit splits line breaks and links web addresses in text, so those messages keep the text path.
        String linked = messages.getPrefixed(player, "linked"), lines = messages.getPrefixed(player, "lines");
        messages.sendPrefixed(player, "linked");
        verify(player).sendMessage(linked);
        messages.sendPrefixed(player, "lines");
        verify(player).sendMessage(lines);
        // The console and other senders get text.
        CommandSender console = mock(CommandSender.class);
        messages.sendPrefixed(console, "denied");
        verify(console).sendMessage("§7[§dDyClaim§7] §cYou can't break blocks in this claim.");
    }

    @Test void textOnlyServersKeepSendingTheSameString() throws Exception {
        MessageManager messages = messages(false);
        Player player = player("Reader", chunk(0, 0));
        messages.sendPrefixed(player, "denied");
        verify(player).sendMessage("§7[§dDyClaim§7] §cYou can't break blocks in this claim.");
        verify(player, never()).sendMessage(any(Component.class));
        Files.writeString(dir.resolve("messages_en.yml"), "denied: \"&4Edited denial.\"\n");
        messages.reload();
        messages.sendPrefixed(player, "denied");
        verify(player).sendMessage("§7[§dDyClaim§7] §4Edited denial.");
    }
}
