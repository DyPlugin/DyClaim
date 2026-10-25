package dev.dyclaim.hook;

import dev.dyclaim.DyClaim;
import dev.dyclaim.model.ClaimData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * %dyclaim_&lt;name&gt;% placeholders. Location-based values use the player's current chunk and are
 * empty for offline players. Values are plain text so other plugins can compare them.
 */
public final class PlaceholderHook extends PlaceholderExpansion {
    private final DyClaim plugin;
    public PlaceholderHook(DyClaim plugin){this.plugin=plugin;}
    /** Returns the registered expansion, or null when PlaceholderAPI refused it. */
    static Object register(DyClaim plugin){PlaceholderHook hook=new PlaceholderHook(plugin);return hook.register()?hook:null;}
    static void unregister(Object hook){((PlaceholderHook)hook).unregister();}
    @Override public String getIdentifier(){return "dyclaim";}
    @Override public String getAuthor(){return "Claww";}
    @Override public String getVersion(){return plugin.getDescription().getVersion();}
    @Override public boolean persist(){return true;}
    @Override public boolean canRegister(){return true;}

    @Override public String onRequest(OfflinePlayer offline,String params){
        if(params==null)return null;
        String key=params.toLowerCase(java.util.Locale.ROOT);
        if(key.equals("total"))return String.valueOf(plugin.getClaimManager().getAllClaims().size());
        if(offline==null)return "";
        var claims=plugin.getClaimManager();
        switch(key){
            case "claims": return String.valueOf(claims.getPlayerClaimCount(offline.getUniqueId()));
            case "claims_owned": return String.valueOf(claims.getPlayerClaims(offline.getUniqueId()).stream().filter(c->c.getOwnerUUID().equals(offline.getUniqueId())).count());
            default: break;
        }
        Player player=offline.getPlayer();
        int limit=player!=null?plugin.getAcquisitionRules().limit(player):plugin.getConfigManager().getMaxClaimsPerPlayer();
        switch(key){
            case "limit": return limit==Integer.MAX_VALUE?"∞":String.valueOf(limit);
            case "remaining": return limit==Integer.MAX_VALUE?"∞":String.valueOf(Math.max(0,limit-claims.getPlayerClaimCount(offline.getUniqueId())));
            default: break;
        }
        if(player==null)return "";
        Location at=player.getLocation();
        ClaimData claim=at.getWorld()==null?null:claims.getClaimAt(at.getWorld().getName(),at.getBlockX()>>4,at.getBlockZ()>>4);
        return switch(key){
            case "in_claim" -> String.valueOf(claim!=null);
            case "owner" -> claim==null?"":claim.getOwnerName();
            case "name" -> claim==null||claim.getName()==null?"":claim.getName();
            case "pvp" -> claim==null?"":String.valueOf(plugin.getAccessManager().enabled(claim,"pvp"));
            case "can_build" -> String.valueOf(plugin.getAccessManager().allows(player,claim,"build"));
            case "role" -> claim==null?"":role(player,claim);
            default -> null;
        };
    }
    private String role(Player player,ClaimData claim){
        var id=player.getUniqueId();
        if(claim.getOwnerUUID().equals(id))return "owner";
        if(id.equals(claim.getCoowner()))return "coowner";
        if(claim.isTrusted(id))return "trusted";
        return "visitor";
    }
}
