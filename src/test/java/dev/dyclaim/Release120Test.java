package dev.dyclaim;

import dev.dyclaim.model.ClaimData;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class Release120Test extends ManagerFixture {
    Player admin(String name,org.bukkit.Chunk chunk){
        Player p=player(name,chunk);when(p.hasPermission(anyString())).thenAnswer(i->!Set.of("dyclaim.admin.bypass").contains(i.getArgument(0)));return p;
    }
    @Test void groupTrustFollowsThePermissionGroupAndCanBeTurnedOff(){
        var chunk=chunk(0,0);Player owner=player("Owner",chunk),member=player("Member",chunk),stranger=player("Stranger",chunk);
        when(member.hasPermission("group.builders")).thenReturn(true);when(stranger.hasPermission("group.builders")).thenReturn(false);
        claims.claimChunk(owner,chunk);ClaimData c=claims.getClaimAt(chunk);
        features.handle(owner,new String[]{"trust","group:Builders"});
        assertTrue(c.getClanGrants().containsKey("group:builders"));
        assertTrue(access.allows(member,c,"build"));assertFalse(access.allows(stranger,c,"build"));
        values.set("integrations.permission-groups.enabled",false);
        assertFalse(access.allows(member,c,"build"),"switching the integration off stops group trust");
        values.set("integrations.permission-groups.enabled",true);
        features.handle(owner,new String[]{"untrust","group:builders"});
        assertFalse(access.allows(member,c,"build"));
    }
    @Test void invalidGroupNamesAreRejected(){
        var chunk=chunk(0,0);Player owner=player("Owner",chunk);claims.claimChunk(owner,chunk);
        features.handle(owner,new String[]{"trust","group:bad name!"});
        verify(owner).sendMessage("group-unavailable");assertTrue(claims.getClaimAt(chunk).getClanGrants().isEmpty());
    }
    @Test void serverClaimsHaveNoOwnerAndOnlyAdminsRemoveThem(){
        var chunk=chunk(3,4);Player op=admin("Admin",chunk),visitor=player("Visitor",chunk);
        features.handle(op,new String[]{"admin","server"});
        verify(op).sendMessage("server-claim-created");
        ClaimData c=claims.getClaimAt(chunk);assertNotNull(c);assertTrue(c.isServerClaim());
        assertFalse(access.allows(visitor,c,"build"));
        features.handle(op,new String[]{"admin","server"});verify(op).sendMessage("server-claim-exists");
        features.handle(visitor,new String[]{"admin","server","remove"});assertNotNull(claims.getClaimAt(chunk),"players cannot remove server claims");
        features.handle(op,new String[]{"admin","sunucu","kaldır"});
        verify(op).sendMessage("server-claim-removed");assertNull(claims.getClaimAt(chunk));
    }
    @Test void serverClaimsDoNotReplacePlayerClaims(){
        var chunk=chunk(1,1);Player owner=player("Owner",chunk),op=admin("Admin",chunk);claims.claimChunk(owner,chunk);
        features.handle(op,new String[]{"admin","server"});
        verify(op).sendMessage("server-claim-owned");assertFalse(claims.getClaimAt(chunk).isServerClaim());
    }
}
