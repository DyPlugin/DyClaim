package dev.dyclaim;

import dev.dyclaim.command.ClaimCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CommandPresentationTest extends ManagerFixture {
    @BeforeEach void messages() {
        when(plugin.getMessageManager().getPrefix()).thenReturn("[DyClaim]");
        when(plugin.getMessageManager().getMessage(any(org.bukkit.command.CommandSender.class),anyString(),anyMap())).thenAnswer(i->i.getArgument(1));
    }
    @Test void helpSendsSeparateNewCommandRowsAndPreservesTheFooter() {
        var p=player("Owner",chunk(0,0));
        new ClaimCommand(plugin).onCommand(p,null,"claim",new String[]{"help"});
        var order=inOrder(p);
        order.verify(p).sendMessage("help-claim");
        order.verify(p).sendMessage("help-market-list");
        order.verify(p).sendMessage("help-market-cancel");
        order.verify(p).sendMessage("help-market-buy");
        order.verify(p).sendMessage("help-footer");
        verify(p,never()).sendMessage("help-new-features");
    }
    @Test void unnamedClaimsKeepTheOriginalListRowAndNamedClaimsExtendIt() {
        var c=chunk(0,0);var p=player("Owner",c);
        claims.claimChunk(p,c);
        features.handle(p,new String[]{"list"});
        verify(p).sendMessage("list-entry");
        verify(p).sendMessage("list-footer");
        verify(p,never()).sendMessage("list-page");
        clearInvocations(p);
        claims.getClaimAt(c).setName("Home");
        features.handle(p,new String[]{"list"});
        verify(p).sendMessage("list-named-entry");
        verify(p,never()).sendMessage("list-entry");
    }
    @Test void infoRetainsOriginalFieldsAndOmitsUnusedOptionalDetails() {
        var c=chunk(0,0);var p=player("Owner",c);claims.claimChunk(p,c);
        features.handle(p,new String[]{"info"});
        for(String key:new String[]{"info-owner","info-chunk","info-world","info-date","info-pvp","info-explosion","info-mob","info-trusted","info-footer"})
            verify(p).sendMessage(key);
        for(String key:new String[]{"claim-summary","claim-private","claim-coowner","claim-market","info-name","info-custom-spawn"})
            verify(p,never()).sendMessage(key);
    }
    @Test void ordinaryPermanentTrustKeepsTheOriginalSimpleEntry() {
        var c=chunk(0,0);var p=player("Owner",c);var friend=player("Friend",c);
        var offline=mock(org.bukkit.OfflinePlayer.class);
        when(offline.getName()).thenReturn("Friend");
        bukkit.when(()->org.bukkit.Bukkit.getOfflinePlayer(friend.getUniqueId())).thenReturn(offline);
        claims.claimChunk(p,c);
        claims.getClaimAt(c).grant(friend.getUniqueId(),0);
        features.handle(p,new String[]{"trustlist"});
        verify(p).sendMessage("trust-list-entry");
        verify(p,never()).sendMessage("trust-list-details");
    }
    @Test void previousMessageFilesGainNewRowsWithoutReplacingCustomText() throws Exception {
        when(config.getLang()).thenReturn("en");
        var previous=new org.bukkit.configuration.file.YamlConfiguration();
        previous.set("help-list","&6/claim list &7- View your claims");
        previous.set("help-tp","My custom teleport help");
        previous.save(dir.resolve("messages_en.yml").toFile());
        when(plugin.getResource(anyString())).thenAnswer(i->getClass().getResourceAsStream("/"+i.getArgument(0)));
        var messages=new dev.dyclaim.manager.MessageManager(plugin);
        org.junit.jupiter.api.Assertions.assertEquals("My custom teleport help",messages.getRawMessage("help-tp"));
        org.junit.jupiter.api.Assertions.assertTrue(messages.getRawMessage("help-list").contains("[page]"));
        org.junit.jupiter.api.Assertions.assertTrue(messages.getRawMessage("help-market-buy").startsWith("&6/claim market buy &7- "));
        org.junit.jupiter.api.Assertions.assertEquals("My custom teleport help",org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dir.resolve("messages_en.yml").toFile()).getString("help-tp"));
    }
}
