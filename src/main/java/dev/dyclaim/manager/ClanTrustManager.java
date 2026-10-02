package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import dev.dyclaim.model.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.util.*;

/** Small fail-closed adapter; SimpleClans membership is read from its in-memory manager. */
public class ClanTrustManager {
    private final DyClaim plugin;
    public ClanTrustManager(DyClaim plugin){this.plugin=plugin;}
    private Object manager() throws ReflectiveOperationException {
        if(!plugin.getConfigManager().values().getBoolean("integrations.clans.enabled"))return null;
        String provider=plugin.getConfigManager().values().getString("integrations.clans.provider","SimpleClans");
        if(!provider.equalsIgnoreCase("SimpleClans")&&!provider.equalsIgnoreCase("auto"))return null;
        Plugin clans=Bukkit.getPluginManager().getPlugin("SimpleClans");
        if(clans==null||!clans.isEnabled())return null;
        return clans.getClass().getMethod("getClanManager").invoke(clans);
    }
    private String identity(Object clan) throws ReflectiveOperationException {
        // The creation timestamp survives tag/name changes. Reject collisions explicitly.
        long founded=(long)clan.getClass().getMethod("getFounded").invoke(clan);
        if(founded<=0)throw new IllegalArgumentException("Missing clan identity");
        Object manager=manager();
        Collection<?> all=(Collection<?>)manager.getClass().getMethod("getClans").invoke(manager);
        int matches=0;
        for(Object candidate:all)if((long)candidate.getClass().getMethod("getFounded").invoke(candidate)==founded)matches++;
        if(matches!=1)throw new IllegalArgumentException("Ambiguous clan creation identity");
        return "SimpleClans:"+founded;
    }
    public String resolve(String name) {
        try {
            if(name.contains(":")) {
                String[] parts=name.split(":",2);if(!parts[0].equalsIgnoreCase("SimpleClans"))return null;name=parts[1];
            }
            Object manager=manager();if(manager==null)return null;
            Object clan=manager.getClass().getMethod("getClan",String.class).invoke(manager,name);
            return clan==null?null:identity(clan);
        }catch(ReflectiveOperationException|RuntimeException ex){return null;}
    }
    public boolean allows(UUID player,ClaimData claim,String right) {
        if(claim.getClanGrants().isEmpty())return false;
        try {
            Object manager=manager();if(manager==null)return false;
            Object member=manager.getClass().getMethod("getClanPlayer",UUID.class).invoke(manager,player);
            if(member==null)return false;
            Object clan=member.getClass().getMethod("getClan").invoke(member);if(clan==null)return false;
            TrustGrant grant=claim.getClanGrants().get(identity(clan));
            return grant!=null&&grant.allows(right,System.currentTimeMillis());
        }catch(ReflectiveOperationException|RuntimeException ex){return false;}
    }
}
