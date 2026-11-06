package dev.dyclaim.platform.core;
import dev.dyclaim.model.ClaimData;

/** Protection precedence shared by native backends, with no server API dependency. */
public final class ProtectionRules {
    public interface Config {
        Object get(String path);
        boolean contains(String path);
        default boolean bool(String path,boolean fallback){Object value=get(path);return value instanceof Boolean flag?flag:fallback;}
    }
    public record Value(boolean enabled,boolean locked,String source){}
    private ProtectionRules(){}
    public static Value resolve(Config config,ClaimData claim,String key){
        String root="protection."+key,world="claim-rules.worlds."+claim.getWorld()+".protection."+key;
        Object globalForced=config.get(root+".forced-value"),worldForced=config.get(world+".forced-value");
        boolean locked=!config.bool(world+".player-toggle",config.bool(root+".player-toggle",true));
        if(globalForced instanceof Boolean value)return new Value(value,true,"server");
        if(worldForced instanceof Boolean value)return new Value(value,true,"world");
        Boolean choice=claim.getChoice(key);
        if(choice!=null)return new Value(choice,locked,"claim");
        if(config.contains(world+".default"))return new Value(config.bool(world+".default",false),locked,"world-default");
        boolean fallback=key.equals("mob-explosions")?resolve(config,claim,"explosions").enabled():fallback(config,key);
        return new Value(config.bool(root+".default",fallback),locked,"default");
    }
    /** Value used when neither the claim nor the configuration chooses; mob-explosions follows explosions instead. */
    public static boolean fallback(Config config,String key){
        return switch(key){
            case "pvp"->!config.bool("protection.pvp-disabled",true);
            case "explosions"->!config.bool("protection.explosion-disabled",true);
            case "mob-spawning"->!config.bool("protection.mob-griefing-disabled",true);
            // Visitors could always walk into claims; a claim is closed only when its owner or the server says so.
            case "visitor-entry"->true;
            default->false;
        };
    }
}
