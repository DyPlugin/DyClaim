package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import dev.dyclaim.model.ClaimData;
import org.bukkit.entity.Player;
import java.util.*;

public class AccessManager {
    private final DyClaim plugin;
    private final Map<UUID,Boolean> bypass=new HashMap<>();
    public AccessManager(DyClaim plugin){this.plugin=plugin;}
    public boolean bypass(Player player){return player.hasPermission("dyclaim.admin.bypass")&&bypass.getOrDefault(player.getUniqueId(),plugin.getConfigManager().values().getBoolean("admin.bypass.enabled-by-default",true));}
    public void setBypass(Player player,boolean enabled){bypass.put(player.getUniqueId(),enabled);}
    public void quit(UUID uuid){bypass.remove(uuid);}
    public ProtectionPolicy.Value policy(ClaimData claim,String key){return ProtectionPolicy.resolve(plugin.getConfigManager().values(),claim,key);}
    public boolean enabled(ClaimData claim,String key){return policy(claim,key).enabled();}
    public boolean allows(Player player,ClaimData claim,String right){
        if(claim==null||bypass(player)||claim.allows(player.getUniqueId(),right))return true;
        if(plugin.getClanTrustManager()!=null&&plugin.getClanTrustManager().allows(player.getUniqueId(),claim,right))return true;
        return switch(right){case "doors"->enabled(claim,"visitor-doors");case "trapdoors"->enabled(claim,"visitor-trapdoors");default->false;};
    }
}
