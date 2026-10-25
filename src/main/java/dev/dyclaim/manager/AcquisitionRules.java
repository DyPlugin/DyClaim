package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import dev.dyclaim.hook.*;
import dev.dyclaim.model.ClaimData;
import dev.dyclaim.util.Rules;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import java.util.*;

public class AcquisitionRules {
    private final DyClaim plugin;
    private final Map<String,Admission> admissions=new LinkedHashMap<>();
    public AcquisitionRules(DyClaim plugin){this.plugin=plugin;}
    public final class Admission implements AutoCloseable {
        private final String rejection,key,world;
        private final UUID actor,primaryOwner;
        private final int x,z;
        private final TransactionManager.Reservation transaction;
        Admission(String rejection,String key,String world,UUID actor,UUID primaryOwner,int x,int z,TransactionManager.Reservation transaction){
            this.rejection=rejection;this.key=key;this.world=world;this.actor=actor;this.primaryOwner=primaryOwner;this.x=x;this.z=z;
            this.transaction=transaction;
        }
        public String rejection(){return rejection;}
        public TransactionManager.Reservation transaction(){return transaction;}
        @Override public void close(){synchronized(admissions){admissions.remove(key,this);if(transaction!=null)transaction.close();}}
    }
    private Admission rejected(String reason){return new Admission(reason,null,null,null,null,0,0,null);}
    /** Hold admission across the operation, never a Java monitor across a provider callback. */
    public Admission reserve(Player player,Chunk chunk,ClaimData replacing,boolean acquiringCoowner){
        String rejection=check(player,chunk,replacing,acquiringCoowner);if(rejection!=null)return rejected(rejection);
        synchronized(admissions){synchronized(plugin.getClaimManager()){
            rejection=checkState(player,chunk,replacing,acquiringCoowner);if(rejection!=null)return rejected(rejection);
            String key=plugin.getClaimManager().getChunkKey(chunk);UUID actor=player.getUniqueId();
            var transaction=plugin.getTransactionManager().reserve(key);if(transaction==null)return rejected("transaction-locked");
            Admission admission=new Admission(null,key,chunk.getWorld().getName(),actor,acquiringCoowner&&replacing!=null?replacing.getOwnerUUID():actor,chunk.getX(),chunk.getZ(),transaction);
            admissions.put(key,admission);return admission;
        }}
    }
    /** The highest granted dyclaim.limit.&lt;n&gt; replaces the configured total; dyclaim.limit.unlimited removes it. */
    public int limit(Player player){
        var cfg=plugin.getConfigManager();int base=cfg.getMaxClaimsPerPlayer();
        if(player==null||!cfg.values().getBoolean("claim.permission-limits.enabled",true))return base;
        int best=-1;
        for(var info:player.getEffectivePermissions()){
            if(!info.getValue())continue;String node=info.getPermission().toLowerCase(Locale.ROOT);
            if(!node.startsWith("dyclaim.limit."))continue;String suffix=node.substring("dyclaim.limit.".length());
            if(suffix.equals("unlimited"))return Integer.MAX_VALUE;
            try{int value=Integer.parseInt(suffix);if(value>=0)best=Math.max(best,value);}catch(NumberFormatException ignored){}
        }
        return best<0?base:best;
    }
    public double price(String world){return Rules.money(plugin.getConfigManager().values().getDouble("claim-rules.worlds."+world+".chunk-price",plugin.getConfigManager().getClaimPrice()));}
    public String check(Player player,Chunk chunk,ClaimData replacing,boolean acquiringCoowner) {
        var cfg=plugin.getConfigManager(); var values=cfg.values();
        if(plugin.getTransactionManager().isLocked(plugin.getClaimManager().getChunkKey(chunk)))return "transaction-locked";
        if(!cfg.isAllowClaiming())return "claiming-disabled";
        if(cfg.isWorldBlacklisted(chunk.getWorld().getName()))return "world-blacklisted";
        if(plugin.getFeatureManager().isClaimBanned(player.getUniqueId()))return "claim-banned";
        if(WorldGuardHook.isRegionProtected(chunk)||GriefPreventionHook.isRegionProtected(chunk)||ClaimPluginHooks.isRegionProtected(chunk))return "claim-region-protected";
        synchronized(admissions){synchronized(plugin.getClaimManager()){return checkState(player,chunk,replacing,acquiringCoowner);}}
    }
    private String checkState(Player player,Chunk chunk,ClaimData replacing,boolean acquiringCoowner){
        var cfg=plugin.getConfigManager();var values=cfg.values();String world=chunk.getWorld().getName();UUID actor=player.getUniqueId();
        String key=plugin.getClaimManager().getChunkKey(chunk);
        if(admissions.containsKey(key)||plugin.getTransactionManager().isLocked(key))return "transaction-locked";
        if(replacing==null&&plugin.getClaimManager().isChunkClaimed(chunk))return "acquire-occupied";
        boolean already=replacing!=null&&(player.getUniqueId().equals(replacing.getOwnerUUID())||player.getUniqueId().equals(replacing.getCoowner()));
        long pendingTotal=0,pendingWorld=0;
        for(Admission admission:admissions.values()){
            if(!actor.equals(admission.actor))continue;
            ClaimData current=plugin.getClaimManager().getByKey(admission.key);
            if(current!=null&&(actor.equals(current.getOwnerUUID())||actor.equals(current.getCoowner())))continue;
            pendingTotal++;if(world.equals(admission.world))pendingWorld++;
        }
        int totalLimit=limit(player);
        if(!already&&plugin.getClaimManager().getPlayerClaimCount(actor)+pendingTotal>=totalLimit)return "acquire-limit";
        // Without a per-world override the world shares the player's effective total limit.
        int worldLimit=values.getInt("claim-rules.worlds."+chunk.getWorld().getName()+".claim-limit",totalLimit);
        long count=plugin.getClaimManager().getPlayerClaims(player.getUniqueId()).stream().filter(c->c.getWorld().equals(chunk.getWorld().getName())).count();
        if(!already&&count+pendingWorld>=worldLimit)return "acquire-limit";
        if(values.getBoolean("claim-rules.spacing.enabled")) {
            int distance=values.getInt("claim-rules.spacing.minimum-empty-chunks",1);
            for(ClaimData other:plugin.getClaimManager().getAllClaims().values()) {
                if(other==replacing||!other.getWorld().equals(chunk.getWorld().getName()))continue;
                // Primary ownership alone grants the same-owner exemption. Coowner/trust do not.
                Admission future=admissions.get(other.getChunkKey());
                // A pending transfer can still fail; neither owner may gain an early exemption.
                if(!acquiringCoowner&&other.getOwnerUUID().equals(actor)&&(future==null||future.primaryOwner.equals(actor)))continue;
                if(!Rules.spacing(chunk.getX(),chunk.getZ(),other.getChunkX(),other.getChunkZ(),distance))return "acquire-spacing";
            }
            for(Admission future:admissions.values()){
                if(!world.equals(future.world)||plugin.getClaimManager().getByKey(future.key)!=null)continue;
                if(!acquiringCoowner&&future.primaryOwner.equals(actor))continue;
                if(!Rules.spacing(chunk.getX(),chunk.getZ(),future.x,future.z,distance))return "acquire-spacing";
            }
        }
        return null;
    }
}
