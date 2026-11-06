package dev.dyclaim.manager;

import dev.dyclaim.model.ClaimData;
import dev.dyclaim.platform.core.ProtectionRules;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Settings read by every protection, movement and redstone event. They are read once per configuration load
 * instead of walking the YAML tree on each event; protection rules are resolved once per world and setting.
 * Results are identical to {@link ProtectionRules#resolve}.
 */
public final class HotSettings {
    public final boolean externalFire,externalFluid,crossOwnerPistons,crossOwnerHoppers,crossOwnerDispensers,externalGrowth,externalRedstone,
            farmlandVisitors,farmlandMobs,permissionGroups,bypassDefault,claimMessages,messageColors,entryBans,warnings,resetWarningsOnExit;
    private final ProtectionRules.Config config;
    private final Map<String,Map<String,Rule>> rules=new ConcurrentHashMap<>();
    /** Everything about one setting in one world except the claim's own choice. */
    private record Rule(Boolean forced,String forcedSource,boolean locked,Boolean worldDefault,Boolean rootDefault,boolean fallback,boolean inheritsExplosions){}

    private HotSettings(FileConfiguration values) {
        config=new ProtectionRules.Config(){
            public Object get(String path){return values.get(path);}
            public boolean contains(String path){return values.contains(path);}
        };
        externalFire=values.getBoolean("protection.boundaries.block-external-fire");
        externalFluid=values.getBoolean("protection.boundaries.block-external-fluid");
        crossOwnerPistons=values.getBoolean("protection.boundaries.block-cross-owner-pistons");
        crossOwnerHoppers=values.getBoolean("protection.boundaries.block-cross-owner-hoppers");
        crossOwnerDispensers=values.getBoolean("protection.boundaries.block-cross-owner-dispensers",true);
        externalGrowth=values.getBoolean("protection.boundaries.block-external-growth",true);
        externalRedstone=values.getBoolean("protection.boundaries.block-external-redstone-effects");
        farmlandVisitors=values.getBoolean("protection.farmland.protect-from-visitors");
        farmlandMobs=values.getBoolean("protection.farmland.protect-from-mobs");
        permissionGroups=values.getBoolean("integrations.permission-groups.enabled",true);
        bypassDefault=values.getBoolean("admin.bypass.enabled-by-default",true);
        claimMessages=values.getBoolean("claim-messages.enabled");
        messageColors=values.getBoolean("claim-messages.allow-colors");
        entryBans=values.getBoolean("claim-entry-bans.enabled");
        warnings=values.getBoolean("warnings.enabled");
        resetWarningsOnExit=values.getBoolean("warnings.reset-on-exit");
    }
    public static HotSettings of(FileConfiguration values){return new HotSettings(values);}

    public ProtectionPolicy.Value resolve(ClaimData claim,String key) {
        String worldName=claim.getWorld();
        Map<String,Rule> world=rules.computeIfAbsent(worldName,name->new ConcurrentHashMap<>());
        Rule rule=world.get(key);
        if(rule==null)rule=world.computeIfAbsent(key,setting->rule(worldName,setting));
        if(rule.forced!=null)return new ProtectionPolicy.Value(rule.forced,true,rule.forcedSource);
        Boolean choice=claim.getChoice(key);
        if(choice!=null)return new ProtectionPolicy.Value(choice,rule.locked,"claim");
        if(rule.worldDefault!=null)return new ProtectionPolicy.Value(rule.worldDefault,rule.locked,"world-default");
        if(rule.rootDefault!=null)return new ProtectionPolicy.Value(rule.rootDefault,rule.locked,"default");
        return new ProtectionPolicy.Value(rule.inheritsExplosions?resolve(claim,"explosions").enabled():rule.fallback,rule.locked,"default");
    }
    public boolean enabled(ClaimData claim,String key){return resolve(claim,key).enabled();}

    private Rule rule(String worldName,String key) {
        String root="protection."+key,world="claim-rules.worlds."+worldName+".protection."+key;
        Object globalForced=config.get(root+".forced-value"),worldForced=config.get(world+".forced-value");
        boolean locked=!config.bool(world+".player-toggle",config.bool(root+".player-toggle",true));
        Boolean forced=globalForced instanceof Boolean value?value:worldForced instanceof Boolean value?value:null;
        Boolean worldDefault=config.contains(world+".default")?config.bool(world+".default",false):null;
        Boolean rootDefault=config.get(root+".default") instanceof Boolean value?value:null;
        return new Rule(forced,globalForced instanceof Boolean?"server":"world",locked,worldDefault,rootDefault,ProtectionRules.fallback(config,key),key.equals("mob-explosions"));
    }
}
