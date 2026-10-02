package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import dev.dyclaim.hook.*;
import dev.dyclaim.model.ClaimData;
import dev.dyclaim.util.Rules;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

public class AcquisitionRules {
    private final DyClaim plugin;
    public AcquisitionRules(DyClaim plugin){this.plugin=plugin;}
    public double price(String world){return Rules.money(plugin.getConfigManager().values().getDouble("claim-rules.worlds."+world+".chunk-price",plugin.getConfigManager().getClaimPrice()));}
    public String check(Player player,Chunk chunk,ClaimData replacing,boolean acquiringCoowner) {
        var cfg=plugin.getConfigManager(); var values=cfg.values();
        if(plugin.getTransactionManager().isLocked(plugin.getClaimManager().getChunkKey(chunk)))return "transaction-locked";
        if(!cfg.isAllowClaiming())return "claiming-disabled";
        if(cfg.isWorldBlacklisted(chunk.getWorld().getName()))return "world-blacklisted";
        if(plugin.getFeatureManager().isClaimBanned(player.getUniqueId()))return "claim-banned";
        if(WorldGuardHook.isRegionProtected(chunk)||GriefPreventionHook.isRegionProtected(chunk)||ClaimPluginHooks.isRegionProtected(chunk))return "claim-region-protected";
        if(replacing==null&&plugin.getClaimManager().isChunkClaimed(chunk))return "acquire-occupied";
        boolean already=replacing!=null&&(player.getUniqueId().equals(replacing.getOwnerUUID())||player.getUniqueId().equals(replacing.getCoowner()));
        if(!already&&plugin.getClaimManager().getPlayerClaimCount(player.getUniqueId())>=cfg.getMaxClaimsPerPlayer())return "acquire-limit";
        int worldLimit=values.getInt("claim-rules.worlds."+chunk.getWorld().getName()+".claim-limit",cfg.getMaxClaimsPerPlayer());
        long count=plugin.getClaimManager().getPlayerClaims(player.getUniqueId()).stream().filter(c->c.getWorld().equals(chunk.getWorld().getName())).count();
        if(!already&&count>=worldLimit)return "acquire-limit";
        if(values.getBoolean("claim-rules.spacing.enabled")) {
            int distance=values.getInt("claim-rules.spacing.minimum-empty-chunks",1);
            for(ClaimData other:plugin.getClaimManager().getAllClaims().values()) {
                if(other==replacing||!other.getWorld().equals(chunk.getWorld().getName()))continue;
                // Primary ownership alone grants the same-owner exemption. Coowner/trust do not.
                if(!acquiringCoowner&&other.getOwnerUUID().equals(player.getUniqueId()))continue;
                if(!Rules.spacing(chunk.getX(),chunk.getZ(),other.getChunkX(),other.getChunkZ(),distance))return "acquire-spacing";
            }
        }
        return null;
    }
}
