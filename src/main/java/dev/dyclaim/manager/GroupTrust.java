package dev.dyclaim.manager;

import dev.dyclaim.model.ClaimData;
import dev.dyclaim.model.TrustGrant;
import org.bukkit.entity.Player;
import java.util.Locale;
import java.util.Map;

/**
 * Trust for a permission group, stored next to clan trust under "group:<name>". LuckPerms and most permission
 * plugins give every member the permission group.<name>, so membership is read without any plugin API.
 */
public final class GroupTrust {
    public static final String KEY="group:";
    private GroupTrust(){}
    /** A normalised group name, or null when it is not a plain permission-group name. */
    public static String name(String raw){
        if(raw==null)return null;String name=raw.trim().toLowerCase(Locale.ROOT);
        return name.matches("[a-z0-9_.\\-]{1,36}")?name:null;
    }
    public static boolean allows(Player player,ClaimData claim,String right){
        long now=System.currentTimeMillis();
        for(Map.Entry<String,TrustGrant> entry:claim.getClanGrants().entrySet()){
            if(!entry.getKey().startsWith(KEY))continue;
            if(entry.getValue().allows(right,now)&&player.hasPermission("group."+entry.getKey().substring(KEY.length())))return true;
        }
        return false;
    }
}
