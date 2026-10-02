package dev.dyclaim.manager;

import dev.dyclaim.model.ClaimData;
import org.bukkit.configuration.file.FileConfiguration;

public class ProtectionPolicy {
    public record Value(boolean enabled, boolean locked, String source) {}
    public static Value resolve(FileConfiguration config, ClaimData claim, String key) {
        String root="protection."+key,world="claim-rules.worlds."+claim.getWorld()+".protection."+key;
        Object globalForced=config.get(root+".forced-value"),worldForced=config.get(world+".forced-value");
        boolean locked=!config.getBoolean(world+".player-toggle",config.getBoolean(root+".player-toggle",true));
        if(globalForced instanceof Boolean value)return new Value(value,true,"server");
        if(worldForced instanceof Boolean value)return new Value(value,true,"world");
        Boolean choice=claim.getChoice(key);
        if(choice!=null)return new Value(choice,locked,"claim");
        if(config.contains(world+".default"))return new Value(config.getBoolean(world+".default"),locked,"world-default");
        boolean fallback=switch(key){case "mob-explosions"->resolve(config,claim,"explosions").enabled();case "pvp"->!config.getBoolean("protection.pvp-disabled",true);case "explosions"->!config.getBoolean("protection.explosion-disabled",true);
            case "mob-spawning"->!config.getBoolean("protection.mob-griefing-disabled",true);default->false;};
        return new Value(config.getBoolean(root+".default",fallback),locked,"default");
    }
}
