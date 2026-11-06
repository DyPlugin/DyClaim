package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import dev.dyclaim.model.ClaimData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * /claim fly: flight inside claims the player manages or, when allowed, builds in. DyClaim only ever takes away
 * flight it granted itself, so creative mode and other plugins' flight are left alone. Every call runs on the
 * player's own thread (movement, commands, respawn), which is what Folia requires for changing a player.
 */
public final class ClaimFlight {
    private final DyClaim plugin;
    /** Players who switched claim flight on; it resumes when they walk back into a claim where it is allowed. */
    private final Set<UUID> wanted=ConcurrentHashMap.newKeySet();
    /** Players whose flight DyClaim switched on and must switch off again. */
    private final Set<UUID> granted=ConcurrentHashMap.newKeySet();
    /** Fall damage is forgiven until this time after flight ended mid-air. */
    private final Map<UUID,Long> fallProtection=new ConcurrentHashMap<>();
    public ClaimFlight(DyClaim plugin){this.plugin=plugin;}

    private boolean featureEnabled(){return plugin.getConfigManager().values().getBoolean("claim-fly.enabled");}
    public boolean active(UUID uuid){return wanted.contains(uuid);}
    public boolean granted(UUID uuid){return granted.contains(uuid);}
    /** Owners and coowners may fly in their claim; players who can build there may too when allow-trusted is on. */
    public boolean allowed(Player player,ClaimData claim){
        if(claim==null||!featureEnabled()||!player.hasPermission("dyclaim.fly"))return false;
        if(claim.canManage(player.getUniqueId()))return true;
        return plugin.getConfigManager().values().getBoolean("claim-fly.allow-trusted",true)&&plugin.getAccessManager().allows(player,claim,"build");
    }
    public void command(Player player,String[] args,BiConsumer<Player,String> send){
        if(!featureEnabled()){send.accept(player,"feature-disabled");return;}
        if(!player.hasPermission("dyclaim.fly")){send.accept(player,"no-permission");return;}
        if(args.length>2){send.accept(player,"fly-usage");return;}
        UUID uuid=player.getUniqueId();
        boolean turnOn=args.length==1?!wanted.contains(uuid):FeatureManager.parseToggle(args[1]);
        if(!turnOn){wanted.remove(uuid);revoke(player);send.accept(player,"fly-disabled");return;}
        var at=player.getLocation();
        ClaimData here=plugin.getClaimManager().getClaimAt(at.getWorld().getName(),at.getBlockX()>>4,at.getBlockZ()>>4);
        if(!allowed(player,here)){send.accept(player,"fly-not-here");return;}
        wanted.add(uuid);grant(player);send.accept(player,"fly-enabled");
    }
    /** Called when the player reaches another claim or leaves claimed land. */
    public void update(Player player,ClaimData to,BiConsumer<Player,String> send){
        UUID uuid=player.getUniqueId();
        if(!wanted.contains(uuid))return;
        if(allowed(player,to))grant(player);
        else if(granted.contains(uuid)){revoke(player);send.accept(player,"fly-left");}
    }
    /** Fall damage within the protection window after flight ended is cancelled once. */
    public boolean consumeFallProtection(UUID uuid){
        if(fallProtection.isEmpty())return false;
        Long until=fallProtection.remove(uuid);
        return until!=null&&until>System.currentTimeMillis();
    }
    private static boolean flyingMode(Player player){return player.getGameMode()==GameMode.CREATIVE||player.getGameMode()==GameMode.SPECTATOR;}
    private void grant(Player player){
        if(player.getAllowFlight())return;
        player.setAllowFlight(true);granted.add(player.getUniqueId());
    }
    private void revoke(Player player){
        if(!granted.remove(player.getUniqueId())||flyingMode(player))return;
        boolean midAir=player.isFlying();
        player.setFlying(false);player.setAllowFlight(false);
        long seconds=plugin.getConfigManager().values().getLong("claim-fly.fall-protection-seconds",10);
        if(midAir&&seconds>0)fallProtection.put(player.getUniqueId(),System.currentTimeMillis()+seconds*1000);
    }
    public void quit(Player player){wanted.remove(player.getUniqueId());revoke(player);fallProtection.remove(player.getUniqueId());}
    /** After a reload, flight that is no longer allowed (feature off, permission removed) ends on the player's thread. */
    public void reload(BiConsumer<Player,Runnable> onPlayer){
        for(UUID uuid:Set.copyOf(granted)){
            Player player=Bukkit.getPlayer(uuid);
            if(player==null){granted.remove(uuid);wanted.remove(uuid);continue;}
            onPlayer.accept(player,()->{
                var at=player.getLocation();
                if(!allowed(player,plugin.getClaimManager().getClaimAt(at.getWorld().getName(),at.getBlockX()>>4,at.getBlockZ()>>4))){revoke(player);if(!featureEnabled())wanted.remove(uuid);}
            });
        }
    }
    /** On shutdown only players this thread may change are touched; the rest lose the flag when they relog. */
    public void close(){
        for(UUID uuid:Set.copyOf(granted)){
            Player player=Bukkit.getPlayer(uuid);
            if(player!=null&&plugin.getPlatformTasks().ownsEntity(player))revoke(player);
        }
        granted.clear();wanted.clear();fallProtection.clear();
    }
}
