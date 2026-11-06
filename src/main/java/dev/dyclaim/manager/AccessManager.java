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
        if(!permitted)return false;
        Boolean chosen=bypass.get(player.getUniqueId());
        return chosen!=null?chosen:settings().bypassDefault;
    }
    public void setBypass(Player player,boolean enabled){bypass.put(player.getUniqueId(),enabled);}
    public void quit(UUID uuid){bypass.remove(uuid);actors.remove(uuid);}
    /** Settings resolved once per configuration load; a configuration without that cache is read directly. */
    public HotSettings settings(){HotSettings hot=plugin.getConfigManager().hot();return hot!=null?hot:HotSettings.of(plugin.getConfigManager().values());}
    public ProtectionPolicy.Value policy(ClaimData claim,String key){
        HotSettings hot=plugin.getConfigManager().hot();
        return hot!=null?hot.resolve(claim,key):ProtectionPolicy.resolve(plugin.getConfigManager().values(),claim,key);
    }
    public boolean enabled(ClaimData claim,String key){return policy(claim,key).enabled();}
    public boolean allows(Player player,ClaimData claim,String right){
        if(claim==null||bypass(player)||claim.allows(player.getUniqueId(),right))return true;
        if(plugin.getClanTrustManager()!=null&&plugin.getClanTrustManager().allows(player.getUniqueId(),claim,right))return true;
        if(settings().permissionGroups&&GroupTrust.allows(player,claim,right))return true;
        return switch(right){case "doors"->enabled(claim,"visitor-doors");case "trapdoors"->enabled(claim,"visitor-trapdoors");default->false;};
    }
    /** Anyone the claim lets in when visitor entry is off: managers, trusted players, trusted clans or groups and bypass. */
    public boolean member(Player player,ClaimData claim){
        if(claim==null||bypass(player))return true;
        UUID uuid=player.getUniqueId();
        if(claim.canManage(uuid)||claim.isTrusted(uuid))return true;
        for(String right:dev.dyclaim.model.TrustGrant.RIGHTS)
            if((plugin.getClanTrustManager()!=null&&plugin.getClanTrustManager().allows(uuid,claim,right))||(settings().permissionGroups&&GroupTrust.allows(player,claim,right)))return true;
        return false;
    }
    /** Storage blocks also open for visitors when the claim allows visitor-containers; other "containers" blocks do not. */
    public boolean allowsStorage(Player player,ClaimData claim){
        return allows(player,claim,"containers")||enabled(claim,"visitor-containers");
    }
    public static boolean isStorage(String type){
        return type.equals("CHEST")||type.equals("TRAPPED_CHEST")||type.equals("BARREL")||type.endsWith("SHULKER_BOX")||type.equals("FURNACE")||type.equals("BLAST_FURNACE")
                ||type.equals("SMOKER")||type.equals("HOPPER")||type.equals("DISPENSER")||type.equals("DROPPER")||type.equals("BREWING_STAND");
    }
}
