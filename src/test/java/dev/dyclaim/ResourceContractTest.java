package dev.dyclaim;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
class ResourceContractTest {
    @Test void bothLanguagesHaveMatchingKeysAndPlaceholders() throws Exception {
        YamlConfiguration en=load("messages_en.yml"),tr=load("messages_tr.yml");assertEquals(en.getKeys(true),tr.getKeys(true));Pattern placeholder=Pattern.compile("\\{[^}]+}");
        for(String key:en.getKeys(true)){Set<String> first=new HashSet<>(),second=new HashSet<>();var a=placeholder.matcher(en.getString(key));while(a.find())first.add(a.group());var b=placeholder.matcher(tr.getString(key));while(b.find())second.add(b.group());assertEquals(first,second,key);}
    }
    @Test void everyNewHelpEntryUsesTheOriginalCommandAndDescriptionFormat() throws Exception {
        for(String resource:List.of("messages_en.yml","messages_tr.yml")) {
            var messages=load(resource);
            assertFalse(messages.contains("help-new-features"));
            assertFalse(messages.contains("admin-help-new-features"));
            for(String suffix:List.of("mobexplosion","villager","doors","trapdoors","trustperm","coowner-add","coowner-remove","transfer","market-list","market-cancel","market-buy","auto","name","setspawn","warn","lang","confirm","cancel"))
                assertTrue(messages.getString("help-"+suffix,"").matches("&6/[^\\r\\n]+ &7- .+"),resource+": help-"+suffix);
            for(String suffix:List.of("bypass","ban","unban","purge","transactions"))
                assertTrue(messages.getString("admin-help-"+suffix,"").matches("&6/claim admin [^\\r\\n]+ &7- .+"),resource+": admin-help-"+suffix);
        }
    }
    @Test void newUsageMessagesMatchTheExistingLocalizedUsageStyle() throws Exception {
        for(String language:List.of("en","tr")) {
            var messages=load("messages_"+language+".yml");
            String prefix=language.equals("en")?"&cUsage: &e/claim ":"&cKullanım: &e/claim ";
            for(String key:List.of("trustperm-usage","coowner-usage","transfer-usage","market-usage","ban-usage","purge-usage","warn-usage"))
                assertTrue(messages.getString(key).startsWith(prefix),key);
        }
        assertTrue(load("messages_tr.yml").getString("help-cancel").startsWith("&6/reddet "));
    }
    private YamlConfiguration load(String resource) throws Exception {var config=new YamlConfiguration();config.loadFromString(new String(getClass().getResourceAsStream("/"+resource).readAllBytes(),StandardCharsets.UTF_8));return config;}
}
