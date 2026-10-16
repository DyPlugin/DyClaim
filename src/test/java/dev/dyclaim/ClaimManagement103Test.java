package dev.dyclaim;

import dev.dyclaim.command.ClaimCommand;
import org.bukkit.command.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ClaimManagement103Test extends ManagerFixture {
    @BeforeEach void messages(){
        when(plugin.getMessageManager().getPrefix()).thenReturn("[DyClaim]");
        when(plugin.getMessageManager().getMessage(any(CommandSender.class),anyString(),anyMap())).thenAnswer(i->i.getArgument(1));
    }
    @Test void filteredListShowsStableTeleportIndex(){
        var p=player("Owner",chunk(0,0));claims.claimChunk(p,chunk(0,0));claims.claimChunk(p,chunk(1,0));
        claims.getClaimAt(world.getChunkAt(1,0)).setName("Home");
        features.handle(p,new String[]{"list","name","home"});
        verify(plugin.getMessageManager()).getMessage(eq(p),eq("list-named-entry"),argThat(m->"2".equals(m.get("{number}"))));
        verify(p,never()).sendMessage("list-entry");
    }
    @Test void infoUsesEffectiveRightsAndOnlyShowsTheViewersTrustExpiry(){
        var c=chunk(0,0);var owner=player("Owner",c);var viewer=player("Viewer",c);claims.claimChunk(owner,c);
        var data=claims.getClaimAt(c);data.grant(viewer.getUniqueId(),System.currentTimeMillis()+120000);data.changeTrustPermission(viewer.getUniqueId(),"containers",false);
        data.grant(UUID.randomUUID(),0);
        features.handle(viewer,new String[]{"info"});
        verify(plugin.getMessageManager()).getMessage(eq(viewer),eq("info-access"),argThat(m->m.containsKey("{rights}")&&m.get("{rights}").contains("build")&&!m.get("{rights}").contains("containers")));
        verify(plugin.getMessageManager()).getMessage(eq(viewer),eq("info-access-expiry"),anyMap());
        verify(viewer,never()).sendMessage("info-trusted");
    }
    @Test void expiredTrustIsNotAdvertisedAsActive(){
        var c=chunk(0,0);var owner=player("Owner",c);var viewer=player("Viewer",c);claims.claimChunk(owner,c);
        claims.getClaimAt(c).grant(viewer.getUniqueId(),System.currentTimeMillis()-100);
        features.handle(viewer,new String[]{"info"});
        verify(viewer,never()).sendMessage("info-access-expiry");
        verify(plugin.getMessageManager()).getMessage(eq(viewer),eq("info-access"),argThat(m->m.containsKey("{rights}")&&!m.get("{rights}").contains("build")));
    }
    @Test void diagnosticsWorkFromConsoleWithoutLeakingPaths(){
        var sender=mock(ConsoleCommandSender.class);when(sender.hasPermission("dyclaim.admin")).thenReturn(true);
        new ClaimCommand(plugin).onCommand(sender,null,"claim",new String[]{"admin","diagnose"});
        verify(sender).sendMessage("diagnose-storage");
        verify(plugin.getMessageManager()).getPrefixed(eq(sender),eq("diagnose-storage"),argThat(m->m.keySet().equals(Set.of("{status}","{count}"))));
    }
    @Test void deniedDiagnosticsAreNeitherRunNorAdvertised(){
        var p=player("Guest",chunk(0,0));var command=new ClaimCommand(plugin);
        command.onCommand(p,null,"claim",new String[]{"admin","diagnose"});
        verify(p).sendMessage("no-permission");verify(p,never()).sendMessage("diagnose-storage");
        assertTrue(command.onTabComplete(p,null,"claim",new String[]{"admin","d"}).isEmpty());
    }
    @Test void turkishAdminAndListCompletionUseOnlyLocalizedWords(){
        var p=player("Admin",chunk(0,0));when(p.hasPermission("dyclaim.admin")).thenReturn(true);
        when(plugin.getMessageManager().getPreferredLanguage(p)).thenReturn("tr");var command=new ClaimCommand(plugin);
        assertEquals(List.of("tanı"),command.onTabComplete(p,null,"claim",new String[]{"admin","tani"}));
        assertEquals(List.of("dünya"),command.onTabComplete(p,null,"claim",new String[]{"liste","dun"}));
        command.onCommand(p,null,"claim",new String[]{"admin","tani"});verify(p).sendMessage("diagnose-storage");
    }
}
