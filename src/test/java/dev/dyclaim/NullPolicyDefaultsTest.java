package dev.dyclaim;
import dev.dyclaim.manager.ProtectionPolicy;
import dev.dyclaim.model.ClaimData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NullPolicyDefaultsTest {
    @Test void explicitNullChoicesRemainUnsetAndInheritTheGlobalDefault() throws Exception {
        var config=new YamlConfiguration();config.loadFromString("protection:\n  explosions:\n    default: true\n    forced-value: null\nclaim-rules:\n  worlds:\n    world:\n      protection:\n        explosions:\n          default: null\n");
        assertFalse(config.contains("protection.explosions.forced-value"));assertFalse(config.contains("claim-rules.worlds.world.protection.explosions.default"));
        var claim=new ClaimData(UUID.randomUUID(),"Owner","world",0,0);claim.inheritChoices();
        assertEquals(new ProtectionPolicy.Value(true,false,"default"),ProtectionPolicy.resolve(config,claim,"explosions"));
    }
}
