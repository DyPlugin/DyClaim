package dev.dyclaim;
import dev.dyclaim.command.ClaimCommand;
import dev.dyclaim.manager.MessageManager;
import dev.dyclaim.util.CommandWords;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandLanguageTest extends ManagerFixture {
    private org.bukkit.entity.Player setupPlayer(String configured,String locale,String manual){
        var player=player("Owner",chunk(0,0));when(config.getLang()).thenReturn(configured);when(player.getLocale()).thenReturn(locale);
        if(manual!=null)features.handle(player,new String[]{"dil",manual});
        var messages=new MessageManager(plugin);when(plugin.getMessageManager()).thenReturn(messages);return player;
    }
    @Test void globalLanguageOverridesSavedPreferenceAndLocale(){var p=setupPlayer("en","tr_tr","tr");assertEquals("en",plugin.getMessageManager().getPreferredLanguage(p));when(config.getLang()).thenReturn("tr");features.handle(p,new String[]{"lang","en"});assertEquals("tr",plugin.getMessageManager().getPreferredLanguage(p));}
    @Test void automaticLanguageUsesSavedPreferenceThenLocale(){var p=setupPlayer("auto","tr_tr",null);assertEquals("tr",plugin.getMessageManager().getPreferredLanguage(p));features.handle(p,new String[]{"lang","en"});assertEquals("en",plugin.getMessageManager().getPreferredLanguage(p));features.handle(p,new String[]{"dil","otomatik"});assertEquals("tr",plugin.getMessageManager().getPreferredLanguage(p));}
    @Test void tabOnlyShowsEffectiveLanguageForBothTypedAliases(){var p=setupPlayer("tr","en_us",null);var command=new ClaimCommand(plugin);var roots=command.onTabComplete(p,null,"claim",new String[]{""});assertTrue(roots.contains("pazar"));assertFalse(roots.contains("market"));assertEquals(List.of("iptal","listele","satınal"),command.onTabComplete(p,null,"claim",new String[]{"market",""}));assertEquals(List.of("ekle","çıkar"),command.onTabComplete(p,null,"claim",new String[]{"ortak",""}));when(config.getLang()).thenReturn("en");assertEquals(List.of("buy","cancel","list"),command.onTabComplete(p,null,"claim",new String[]{"pazar",""}));}
    @Test void turkishOperationsExecuteAndEnglishAliasesRemainValid(){var chunk=chunk(0,0);var owner=player("Owner",chunk);var visitor=player("Visitor",chunk);claims.claimChunk(owner,chunk);features.handle(owner,new String[]{"güven","Visitor","kalıcı"});features.handle(owner,new String[]{"güvenizin","Visitor","sandıklar","reddet"});assertFalse(claims.getClaimAt(chunk).allows(visitor.getUniqueId(),"containers"));features.handle(owner,new String[]{"trustperm","Visitor","containers","allow"});assertTrue(claims.getClaimAt(chunk).allows(visitor.getUniqueId(),"containers"));features.handle(owner,new String[]{"ortak","ekle","Visitor"});confirmations.confirm(visitor.getUniqueId());assertEquals(visitor.getUniqueId(),claims.getClaimAt(chunk).getCoowner());features.handle(owner,new String[]{"coowner","remove"});assertNull(claims.getClaimAt(chunk).getCoowner());values.set("market.enabled",true);features.handle(owner,new String[]{"pazar","listele","1000"});assertEquals(1000.0,claims.getClaimAt(chunk).getMarketPrice());features.handle(owner,new String[]{"market","cancel"});assertNull(claims.getClaimAt(chunk).getMarketPrice());}
    @Test void grammarNormalizationPreservesPlayerNamesAndPrices(){assertArrayEquals(new String[]{"ortak","add","Çıkar"},CommandWords.normalize(new String[]{"ortak","ekle","Çıkar"}));assertArrayEquals(new String[]{"pazar","list","1000"},CommandWords.normalize(new String[]{"pazar","listele","1000"}));assertArrayEquals(new String[]{"admin","enable"},CommandWords.normalize(new String[]{"admin","aç"}));assertArrayEquals(new String[]{"admin","disable"},CommandWords.normalize(new String[]{"admin","kapat"}));assertArrayEquals(new String[]{"admin","dil","auto"},CommandWords.normalize(new String[]{"admin","dil","otomatik"}));}
}


