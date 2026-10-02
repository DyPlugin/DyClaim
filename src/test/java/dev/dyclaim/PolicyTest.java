package dev.dyclaim;

import dev.dyclaim.manager.*;
import dev.dyclaim.model.*;
import dev.dyclaim.listener.ClaimProtectionListener;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PolicyTest {
    @Test void precedenceAndToggleLockAreIndependent(){
        ClaimData c=new ClaimData(UUID.randomUUID(),"Owner","world",0,0);c.inheritChoices();var config=new YamlConfiguration();
        config.set("protection.visitor-doors.default",false);assertFalse(ProtectionPolicy.resolve(config,c,"visitor-doors").enabled());
        config.set("claim-rules.worlds.world.protection.visitor-doors.default",true);assertTrue(ProtectionPolicy.resolve(config,c,"visitor-doors").enabled());
        c.setChoice("visitor-doors",false);assertFalse(ProtectionPolicy.resolve(config,c,"visitor-doors").enabled());
        config.set("protection.visitor-doors.player-toggle",false);var locked=ProtectionPolicy.resolve(config,c,"visitor-doors");assertFalse(locked.enabled());assertTrue(locked.locked());
        config.set("claim-rules.worlds.world.protection.visitor-doors.forced-value",true);assertTrue(ProtectionPolicy.resolve(config,c,"visitor-doors").enabled());
        config.set("protection.visitor-doors.forced-value",false);assertFalse(ProtectionPolicy.resolve(config,c,"visitor-doors").enabled());
    }
    @Test void coownerCannotBecomePrimaryThroughTrustAndTransferClearsAccess(){
        UUID owner=UUID.randomUUID(),co=UUID.randomUUID(),friend=UUID.randomUUID();ClaimData c=new ClaimData(owner,"Owner","world",0,0);c.setCoowner(co);c.grant(friend,0);c.getClanGrants().put("SimpleClans:1",new TrustGrant(0));c.setMarketPrice(100.0);
        assertTrue(c.canManage(co));assertEquals(owner,c.getOwnerUUID());assertFalse(c.canManage(friend));c.clearAccess();assertFalse(c.canManage(co));assertFalse(c.isTrusted(friend));assertTrue(c.getClanGrants().isEmpty());assertNull(c.getMarketPrice());
    }
    @Test void boundaryRulesAreSymmetricAndSamePrimaryOwnerIsExempt(){
        UUID owner=UUID.randomUUID();ClaimData a=new ClaimData(owner,"Owner","world",0,0),b=new ClaimData(owner,"Owner","world",1,0),other=new ClaimData(UUID.randomUUID(),"Other","world",2,0);
        assertFalse(ClaimProtectionListener.crosses(null,null));assertFalse(ClaimProtectionListener.crosses(a,b));assertTrue(ClaimProtectionListener.crosses(a,null));assertTrue(ClaimProtectionListener.crosses(null,a));assertTrue(ClaimProtectionListener.crosses(a,other));assertTrue(ClaimProtectionListener.crosses(other,a));
    }
}
