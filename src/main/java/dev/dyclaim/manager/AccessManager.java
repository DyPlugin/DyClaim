package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import dev.dyclaim.model.ClaimData;
import org.bukkit.entity.Player;
import java.util.*;

public class AccessManager {
    private final DyClaim plugin;
    private final Map<UUID,Boolean> bypass=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID,Actor> actors=new java.util.concurrent.ConcurrentHashMap<>();
    private record Actor(Player player,String world,int x,int z,boolean bypassPermission){}
    public AccessManager(DyClaim plugin){this.plugin=plugin;}
    public void recordActor(Player player){
        if(plugin.getPlatformTasks().isRegionThreaded()&&plugin.getPlatformTasks().ownsEntity(player))recordActor(player,player.getLocation());
    }
    public void recordActor(Player player,org.bukkit.Location location){
        if(!plugin.getPlatformTasks().isRegionThreaded()||!plugin.getPlatformTasks().ownsEntity(player))return;
        actors.put(player.getUniqueId(),new Actor(player,location.getWorld().getName(),location.getBlockX()>>4,location.getBlockZ()>>4,player.hasPermission("dyclaim.admin.bypass")));
    }
    /** Synchronous remote damage decisions consume owner-published data, never foreign entity state. */
    private Actor actor(Player player){
        if(plugin.getPlatformTasks().ownsEntity(player))recordActor(player);
        Actor actor=actors.get(player.getUniqueId());return actor!=null&&actor.player==player?actor:null;
    }
    public boolean actorKnown(Player player){return !plugin.getPlatformTasks().isRegionThreaded()||actor(player)!=null;}
    public ClaimData actorClaim(Player player){
        if(!plugin.getPlatformTasks().isRegionThreaded())return plugin.getClaimManager().getClaimAt(player.getLocation().getChunk());
        Actor actor=actor(player);return actor==null?null:plugin.getClaimManager().getClaimAt(actor.world,actor.x,actor.z);
    }
    public void watchActor(Player player){
        if(!plugin.getPlatformTasks().isRegionThreaded())return;
        recordActor(player);
        plugin.getPlatformTasks().playerTimer(player,()->{if(player.isOnline())recordActor(player);},1,1);
    }
    public boolean bypass(Player player){
        boolean permitted;
        if(plugin.getPlatformTasks().isRegionThreaded()&&!plugin.getPlatformTasks().ownsEntity(player)){Actor actor=actor(player);permitted=actor!=null&&actor.bypassPermission;}
        else permitted=player.hasPermission("dyclaim.admin.bypass");
        return permitted&&bypass.getOrDefault(player.getUniqueId(),plugin.getConfigManager().values().getBoolean("admin.bypass.enabled-by-default",true));
    }
    public void setBypass(Player player,boolean enabled){bypass.put(player.getUniqueId(),enabled);}
    public void quit(UUID uuid){bypass.remove(uuid);actors.remove(uuid);}
    public ProtectionPolicy.Value policy(ClaimData claim,String key){return ProtectionPolicy.resolve(plugin.getConfigManager().values(),claim,key);}
    public boolean enabled(ClaimData claim,String key){return policy(claim,key).enabled();}
    public boolean allows(Player player,ClaimData claim,String right){
        if(claim==null||bypass(player)||claim.allows(player.getUniqueId(),right))return true;
        if(plugin.getClanTrustManager()!=null&&plugin.getClanTrustManager().allows(player.getUniqueId(),claim,right))return true;
        if(plugin.getConfigManager().values().getBoolean("integrations.permission-groups.enabled",true)&&GroupTrust.allows(player,claim,right))return true;
        return switch(right){case "doors"->enabled(claim,"visitor-doors");case "trapdoors"->enabled(claim,"visitor-trapdoors");default->false;};
    }
}
