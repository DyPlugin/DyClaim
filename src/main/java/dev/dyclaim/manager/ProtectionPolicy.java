package dev.dyclaim.manager;

import dev.dyclaim.model.ClaimData;
import org.bukkit.configuration.file.FileConfiguration;

public class ProtectionPolicy {
    public record Value(boolean enabled, boolean locked, String source) {}
    public static Value resolve(FileConfiguration config, ClaimData claim, String key) {
        var resolved=dev.dyclaim.platform.core.ProtectionRules.resolve(new dev.dyclaim.platform.core.ProtectionRules.Config(){
            public Object get(String path){return config.get(path);}
            public boolean contains(String path){return config.contains(path);}
        },claim,key);
        return new Value(resolved.enabled(),resolved.locked(),resolved.source());
    }
}
