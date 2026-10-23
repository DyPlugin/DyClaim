package dev.dyclaim;

import dev.dyclaim.command.ClaimCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Release104Test extends ManagerFixture {
    List<Map<String,String>> pageLines;
    @BeforeEach void messages() {
        pageLines=new ArrayList<>();
        when(plugin.getMessageManager().getPrefix()).thenReturn("[DyClaim]");
        when(plugin.getMessageManager().getMessage(any(CommandSender.class),anyString(),anyMap())).thenAnswer(i->{
            String key=i.getArgument(1);if(key.startsWith("help-page"))pageLines.add(Map.of("key",key,"page",((Map<String,String>)i.getArgument(2)).get("{page}"),"pages",((Map<String,String>)i.getArgument(2)).get("{pages}"),"command",((Map<String,String>)i.getArgument(2)).get("{command}")));return key;});
        when(plugin.getMessageManager().getPreferredLanguage(any())).thenReturn("en");
    }
    Command command(String name){Command c=mock(Command.class);when(c.getName()).thenReturn(name);return c;}
    List<String> rows(org.bukkit.entity.Player p){
        var captor=org.mockito.ArgumentCaptor.forClass(String.class);verify(p,atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues().stream().filter(s->s.startsWith("help-")||s.startsWith("admin-help-")).filter(s->!s.endsWith("header")&&!s.endsWith("footer")&&!s.startsWith("help-page")).toList();
    }

    @Test void playerHelpIsSplitIntoShortPagesWithANextCommand() {
        var p=player("Owner",chunk(0,0));
        new ClaimCommand(plugin).onCommand(p,command("claim"),"claim",new String[]{"help"});
        List<String> first=rows(p);
        assertEquals(ClaimCommandAccess.PAGE_SIZE,first.size());
        assertEquals("help-claim",first.get(0));
        assertEquals("help-page-next",pageLines.get(0).get("key"));
        assertEquals("1",pageLines.get(0).get("page"));
        assertEquals("/claim help 2",pageLines.get(0).get("command"));
        verify(p).sendMessage("help-footer");
    }

    @Test void everyHelpRowAppearsExactlyOnceAcrossAllPages() {
        var p=player("Owner",chunk(0,0));var cmd=new ClaimCommand(plugin);
        cmd.onCommand(p,command("claim"),"claim",new String[]{"help"});
        int pages=Integer.parseInt(pageLines.get(0).get("pages"));
        assertTrue(pages>1);
        List<String> all=new ArrayList<>();
        for(int page=1;page<=pages;page++){clearInvocations(p);pageLines.clear();cmd.onCommand(p,command("claim"),"claim",new String[]{"help",String.valueOf(page)});all.addAll(rows(p));
            assertEquals(page<pages?"help-page-next":"help-page",pageLines.get(0).get("key"));}
        assertEquals(new LinkedHashSet<>(all).size(),all.size());
        assertTrue(all.containsAll(List.of("help-claim","help-dyc","help-tp","help-cltp","help-market-buy","help-confirm","help-cancel")));
        assertFalse(all.contains("help-admin"),"ordinary players must not be shown the admin entry");
    }

    @Test void pagesBeyondTheEndShowTheLastPageAndInvalidPagesAreRejected() {
        var p=player("Owner",chunk(0,0));var cmd=new ClaimCommand(plugin);
        cmd.onCommand(p,command("claim"),"claim",new String[]{"help","999"});
        assertEquals("help-page",pageLines.get(0).get("key"));
        assertEquals(pageLines.get(0).get("pages"),pageLines.get(0).get("page"));
        clearInvocations(p);
        cmd.onCommand(p,command("claim"),"claim",new String[]{"help","abc"});
        verify(p).sendMessage("invalid-input");
        clearInvocations(p);
        cmd.onCommand(p,command("claim"),"claim",new String[]{"help","0"});
        verify(p).sendMessage("invalid-input");
    }

    @Test void turkishNextPageCommandUsesTheTurkishWordAndTheTypedRoot() {
        when(plugin.getMessageManager().getPreferredLanguage(any())).thenReturn("tr");
        var p=player("Owner",chunk(0,0));
        new ClaimCommand(plugin).onCommand(p,command("claim"),"dyc",new String[]{"yardım"});
        assertEquals("/dyc yardım 2",pageLines.get(0).get("command"));
    }

    @Test void adminHelpIsPagedAndDycaIsAnAdminShortcut() {
        var admin=player("Admin",chunk(0,0));when(admin.hasPermission(anyString())).thenReturn(true);
        var cmd=new ClaimCommand(plugin);
        cmd.onCommand(admin,command("dyca"),"dyca",new String[]{});
        List<String> first=rows(admin);
        assertEquals("admin-help-dyca",first.get(0));
        assertEquals(ClaimCommandAccess.PAGE_SIZE,first.size());
        assertEquals("/dyca help 2",pageLines.get(0).get("command"));
        clearInvocations(admin);pageLines.clear();
        cmd.onCommand(admin,command("claim"),"claim",new String[]{"admin","help","2"});
        assertEquals("2",pageLines.get(0).get("page"));
        assertEquals("/claim admin help 3",pageLines.get(0).get("command"));
        clearInvocations(admin);pageLines.clear();
        cmd.onCommand(admin,command("claim"),"claim",new String[]{"help"});
        assertTrue(rowsOnAllPages(cmd,admin).contains("help-admin"));
    }

    List<String> rowsOnAllPages(ClaimCommand cmd,org.bukkit.entity.Player p){
        List<String> all=new ArrayList<>();int pages=Integer.parseInt(pageLines.get(0).get("pages"));
        for(int page=1;page<=pages;page++){clearInvocations(p);cmd.onCommand(p,command("claim"),"claim",new String[]{"help",String.valueOf(page)});all.addAll(rows(p));}
        return all;
    }

    @Test void dycaRunsAdminActionsAndIsRefusedWithoutPermission() {
        var player=player("Owner",chunk(0,0));
        new ClaimCommand(plugin).onCommand(player,command("dyca"),"dyca",new String[]{"enable"});
        verify(player).sendMessage("no-permission");
        verify(config,never()).setAllowClaiming(anyBoolean());
        var admin=player("Admin",chunk(0,0));when(admin.hasPermission(anyString())).thenReturn(true);
        new ClaimCommand(plugin).onCommand(admin,command("dyca"),"dyca",new String[]{"disable"});
        verify(config).setAllowClaiming(false);
        verify(admin).sendMessage("admin-claiming-disabled");
    }

    @Test void consoleCanUseDycaAndGetsTheCompleteAdminList() {
        var console=mock(org.bukkit.command.ConsoleCommandSender.class);when(console.hasPermission(anyString())).thenReturn(true);
        new ClaimCommand(plugin).onCommand(console,command("dyca"),"dyca",new String[]{"help"});
        verify(console).sendMessage("admin-help-dyca");
        verify(console).sendMessage("admin-help-reload");
        verify(console,never()).sendMessage("admin-help-give");
        assertTrue(pageLines.isEmpty(),"console output is not paged");
    }

    @Test void cltpTeleportsLikeClaimTp() {
        var c=chunk(0,0);var p=player("Owner",c);
        var teleports=mock(dev.dyclaim.manager.TeleportManager.class);when(plugin.getTeleportManager()).thenReturn(teleports);
        claims.claimChunk(p,c);
        new ClaimCommand(plugin).onCommand(p,command("cltp"),"cltp",new String[]{"1"});
        var claim=claims.getClaimAt(c);verify(teleports).startTeleport(p,claim);
        clearInvocations(p);
        new ClaimCommand(plugin).onCommand(p,command("cltp"),"cltp",new String[]{});
        verify(p).sendMessage("tp-usage");
    }

    @Test void shortcutsCompleteLikeTheirLongForms() {
        var c=chunk(0,0);var p=player("Owner",c);claims.claimChunk(p,c);claims.getClaimAt(c).setName("Home");
        var cmd=new ClaimCommand(plugin);
        assertEquals(cmd.onTabComplete(p,command("claim"),"claim",new String[]{"tp",""}),cmd.onTabComplete(p,command("cltp"),"cltp",new String[]{""}));
        assertTrue(cmd.onTabComplete(p,command("cltp"),"cltp",new String[]{""}).contains("Home"));
        assertEquals(List.of(),cmd.onTabComplete(p,command("dyca"),"dyca",new String[]{""}),"players without admin permission get no admin suggestions");
        var admin=player("Admin",c);when(admin.hasPermission(anyString())).thenReturn(true);
        assertEquals(cmd.onTabComplete(admin,command("claim"),"claim",new String[]{"admin",""}),cmd.onTabComplete(admin,command("dyca"),"dyca",new String[]{""}));
        assertTrue(cmd.onTabComplete(admin,command("dyca"),"dyca",new String[]{"rel"}).contains("reload"));
        assertTrue(cmd.onTabComplete(p,command("claim"),"claim",new String[]{"help",""}).contains("2"));
    }

    @Test void pluginDescriptorDeclaresTheShortcuts() throws Exception {
        var yaml=YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(getClass().getResourceAsStream("/plugin.yml"),java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(yaml.getStringList("commands.claim.aliases").containsAll(List.of("dyclaim","dyc")));
        assertEquals("dyclaim.admin",yaml.getString("commands.dyca.permission"),"/dyca must stay hidden from players");
        assertEquals("dyclaim.teleport",yaml.getString("commands.cltp.permission"));
        assertTrue(yaml.getBoolean("permissions.dyclaim.teleport.default"));
    }

    @Test void bothLanguagesShipTheNewHelpMessages() {
        for(String lang:List.of("en","tr")){
            var yaml=YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(getClass().getResourceAsStream("/messages_"+lang+".yml"),java.nio.charset.StandardCharsets.UTF_8));
            for(String key:List.of("help-page","help-page-next","help-dyc","help-cltp","admin-help-dyca"))assertNotNull(yaml.getString(key),lang+":"+key);
            assertTrue(yaml.getString("help-page-next").contains("{command}"));
        }
    }

    @Test void deniedOptionalFeaturesOnlyReportTheMissingPermission() {
        var c=chunk(0,0);
        for(String[] entry:new String[][]{{"dyclaim.coowner","coowner","add","Other"},{"dyclaim.transfer","transfer","Other"},{"dyclaim.market","market","cancel"},{"dyclaim.warn","warn","Other"}}){
            var p=player("Owner",c);String node=entry[0];
            when(p.hasPermission(anyString())).thenAnswer(i->!i.getArgument(0).equals(node)&&!((String)i.getArgument(0)).startsWith("dyclaim.admin"));
            features.handle(p,Arrays.copyOfRange(entry,1,entry.length));
            verify(p).sendMessage("no-permission");
            verify(p,never()).sendMessage("feature-disabled");
        }
    }

    @Test void cancellingAPendingActionSaysItWasCancelledInsteadOfRepeatingTheButtonLabel() {
        var c=chunk(0,0);var p=player("Owner",c);claims.claimChunk(p,c);
        values.set("confirmations.required.refund-sale",true);
        dev.dyclaim.command.SellHelper.request(plugin,p,false);
        assertTrue(confirmations.deny(p.getUniqueId()));
        verify(p).sendMessage("confirm-cancelled");
        verify(p,never()).sendMessage("confirm-deny");
        assertNotNull(claims.getClaimAt(c));
    }

    @Test void untouchedOldShippedTextsUseTheNewWordingAndEditedTextsStay() throws Exception {
        when(config.getLang()).thenReturn("tr");
        var previous=new YamlConfiguration();
        previous.set("acquire-limit","&cToplam veya bu dünyadaki sahiplik limitine ulaştın.");
        previous.set("no-permission","&cBurası yasak bölge!");
        previous.save(dir.resolve("messages_tr.yml").toFile());
        when(plugin.getResource(anyString())).thenAnswer(i->getClass().getResourceAsStream("/"+i.getArgument(0)));
        var messages=new dev.dyclaim.manager.MessageManager(plugin);
        var shipped=YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(getClass().getResourceAsStream("/messages_tr.yml"),java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(shipped.getString("acquire-limit"),messages.getRawMessage("acquire-limit"));
        assertEquals("&cBurası yasak bölge!",messages.getRawMessage("no-permission"));
        assertEquals("&cToplam veya bu dünyadaki sahiplik limitine ulaştın.",YamlConfiguration.loadConfiguration(dir.resolve("messages_tr.yml").toFile()).getString("acquire-limit"),"the server file itself is not rewritten");
    }

    @Test void historyNeverContainsTheCurrentTextAndOnlyKnownKeys() {
        for(String lang:List.of("en","tr")){
            var history=YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(getClass().getResourceAsStream("/message-history_"+lang+".yml"),java.nio.charset.StandardCharsets.UTF_8));
            var current=YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(getClass().getResourceAsStream("/messages_"+lang+".yml"),java.nio.charset.StandardCharsets.UTF_8));
            assertFalse(history.getKeys(false).isEmpty());
            for(String key:history.getKeys(false)){assertTrue(current.contains(key),lang+":"+key);assertFalse(history.getStringList(key).contains(current.getString(key)),lang+":"+key);}
        }
    }

    /** Mirrors the production page size so the test fails if pages silently grow. */
    static final class ClaimCommandAccess {static final int PAGE_SIZE=8;}
}
