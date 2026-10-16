package dev.dyclaim;
import org.junit.jupiter.api.Test;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TrustListingSnapshotTest extends ManagerFixture {
    @Test void editsDuringNameLookupCannotInvalidateTheDisplayedTrustIterator(){
        var chunk=chunk(0,0);var owner=player("Owner",chunk);assertTrue(claims.claimChunk(owner,chunk));
        var claim=claims.getClaimAt(chunk);claim.grant(UUID.randomUUID(),0);claim.grant(UUID.randomUUID(),0);
        UUID later=UUID.randomUUID();OfflinePlayer friend=mock(OfflinePlayer.class);when(friend.getName()).thenReturn("Friend");
        bukkit.when(()->Bukkit.getOfflinePlayer(any(UUID.class))).thenAnswer(i->{claim.grant(later,0);return friend;});
        assertDoesNotThrow(()->features.handle(owner,new String[]{"trustlist"}));
    }
}
