package dev.dyclaim;
import dev.dyclaim.platform.core.ProtectionRules;
import dev.dyclaim.model.ClaimData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PortableProtectionRulesTest {
    @Test void serverForcingOverridesWorldForcingAndAnExplicitClaimChoice(){
        ClaimData claim=new ClaimData(UUID.randomUUID(),"Owner","world",0,0);claim.setChoice("explosions",false);
        Map<String,Object> values=Map.of("protection.explosions.forced-value",true,"claim-rules.worlds.world.protection.explosions.forced-value",false);
        var config=new ProtectionRules.Config(){public Object get(String path){return values.get(path);}public boolean contains(String path){return values.containsKey(path);}};
        assertEquals(new ProtectionRules.Value(true,true,"server"),ProtectionRules.resolve(config,claim,"explosions"));
    }
    @Test void portablePolicyMatchesBukkitPrecedenceAndInheritedMobExplosionRules(){
        ClaimData claim=new ClaimData(UUID.randomUUID(),"Owner","world",0,0);claim.inheritChoices();
        var config=new YamlConfiguration();
        var view=new ProtectionRules.Config(){public Object get(String path){return config.get(path);}public boolean contains(String path){return config.contains(path);}};
        for(Map<String,Object> options:List.of(
            Map.<String,Object>of("protection.explosions.default",true),
            Map.<String,Object>of("protection.explosions.forced-value",true,"claim-rules.worlds.world.protection.explosions.forced-value",false),
            Map.<String,Object>of("claim-rules.worlds.world.protection.explosions.default",true,"protection.explosions.player-toggle",false),
            Map.<String,Object>of("protection.mob-explosions.default",true,"claim-rules.worlds.world.protection.mob-explosions.player-toggle",false))) {
            for(String key:new HashSet<>(config.getKeys(true)))config.set(key,null);options.forEach(config::set);
            for(String key:List.of("explosions","mob-explosions","pvp","visitor-doors")){
                var old=dev.dyclaim.manager.ProtectionPolicy.resolve(config,claim,key);var portable=ProtectionRules.resolve(view,claim,key);
                assertEquals(new ProtectionRules.Value(old.enabled(),old.locked(),old.source()),portable,key+" "+options);
            }
        }
    }
}
