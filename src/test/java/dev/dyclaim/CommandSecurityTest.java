package dev.dyclaim;

import dev.dyclaim.command.SellHelper;
import dev.dyclaim.manager.*;
import dev.dyclaim.model.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandSecurityTest extends ManagerFixture {
    @BeforeEach void buttons(){values.set("confirmations.clickable-buttons",false);}
    @Test void movingBeforeSaleCannotDeleteAnotherOwnersClaim(){var aChunk=chunk(0,0);var bChunk=chunk(2,0);Player a=player("A",aChunk),b=player("B",bChunk);claims.claimChunk(a,aChunk);claims.claimChunk(b,bChunk);SellHelper.request(plugin,a,false);org.bukkit.Location moved=b.getLocation();when(a.getLocation()).thenReturn(moved);assertTrue(confirmations.confirm(a.getUniqueId()));assertNull(claims.getClaimAt(aChunk));assertEquals(b.getUniqueId(),claims.getClaimAt(bChunk).getOwnerUUID());verify(economy).deposit(a.getUniqueId(),600);}
    @Test void changingOwnerInvalidatesSale(){var c=chunk(0,0);Player a=player("A",c),b=player("B",c);claims.claimChunk(a,c);SellHelper.request(plugin,a,false);claims.transfer(claims.getClaimAt(c),b);assertFalse(confirmations.confirm(a.getUniqueId()));assertEquals(b.getUniqueId(),claims.getClaimAt(c).getOwnerUUID());verify(economy,never()).deposit(any(UUID.class),anyDouble());}
    @Test void changedPricePreventsStaleRefund(){var c=chunk(0,0);Player a=player("A",c);claims.claimChunk(a,c);SellHelper.request(plugin,a,false);when(config.getSellRefundPercent()).thenReturn(90);assertTrue(confirmations.confirm(a.getUniqueId()));assertNotNull(claims.getClaimAt(c));verify(economy,never()).deposit(any(UUID.class),anyDouble());}
    @Test void noMoneyIsWithdrawnWhenPolicyChangesDuringClaimConfirmation(){var c=chunk(0,0);Player a=player("A",c);features.requestClaim(a);values.set("blacklisted-worlds",List.of("world"));confirmations.confirm(a.getUniqueId());assertNull(claims.getClaimAt(c));verify(economy,never()).withdraw(any(UUID.class),anyDouble());}
    @Test void permissionIsRecheckedAtConfirmation(){var c=chunk(0,0);Player a=player("A",c);features.requestClaim(a);when(a.hasPermission("dyclaim.claim")).thenReturn(false);confirmations.confirm(a.getUniqueId());assertNull(claims.getClaimAt(c));verify(economy,never()).withdraw(any(UUID.class),anyDouble());}
    @Test void claimConfirmationIsBoundToOriginalChunk(){org.bukkit.Chunk first=chunk(0,0),second=chunk(3,0);Player a=player("A",first),other=player("Other",second);features.requestClaim(a);org.bukkit.Location moved=other.getLocation();when(a.getLocation()).thenReturn(moved);confirmations.confirm(a.getUniqueId());assertNotNull(claims.getClaimAt(first));assertNull(claims.getClaimAt(second));}
    @Test void limitIsRecheckedAtConfirmation(){values.set("claim.max-claims-per-player",1);org.bukkit.Chunk first=chunk(0,0),second=chunk(3,0);Player a=player("A",first);features.requestClaim(a);claims.claimChunk(a,second);confirmations.confirm(a.getUniqueId());assertNull(claims.getClaimAt(first));assertEquals(1,claims.getPlayerClaimCount(a.getUniqueId()));}
    @Test void trustPermissionDenialCannotManageTrust(){var c=chunk(0,0);Player a=player("A",c),b=player("B",c);claims.claimChunk(a,c);when(a.hasPermission("dyclaim.trust")).thenReturn(false);features.handle(a,new String[]{"trust","B"});assertFalse(claims.getClaimAt(c).isTrusted(b.getUniqueId()));}
    @Test void coownerNeedsConsentCountsInLimitsAndCannotSell(){var c=chunk(0,0);Player a=player("A",c),b=player("B",c);claims.claimChunk(a,c);features.handle(a,new String[]{"coowner","add","B"});assertNull(claims.getClaimAt(c).getCoowner());confirmations.confirm(b.getUniqueId());assertEquals(b.getUniqueId(),claims.getClaimAt(c).getCoowner());assertEquals(1,claims.getPlayerClaimCount(b.getUniqueId()));SellHelper.request(plugin,b,false);assertFalse(confirmations.hasPending(b.getUniqueId()));}
    @Test void transferRequiresRecipientAndClearsOldAccess(){var c=chunk(0,0);Player a=player("A",c),b=player("B",c);claims.claimChunk(a,c);ClaimData claim=claims.getClaimAt(c);claim.grant(UUID.randomUUID(),0);features.handle(a,new String[]{"transfer","B"});assertEquals(a.getUniqueId(),claim.getOwnerUUID());assertFalse(confirmations.hasPending(a.getUniqueId()));assertTrue(confirmations.confirm(b.getUniqueId()));assertEquals(b.getUniqueId(),claim.getOwnerUUID());assertTrue(claim.getTrustGrants().isEmpty());}
    @Test void priceDifferenceRefundCannotBeRepeated(){var c=chunk(0,0);Player a=player("A",c);claims.claimChunk(a,c);when(config.getClaimPrice()).thenReturn(900.0);assertEquals(1,claims.refundPriceDifference(100));assertEquals(0,claims.refundPriceDifference(100));verify(economy,times(1)).deposit(a.getUniqueId(),100);}
    @Test void staleButtonCannotConfirmReplacementAndTextStillWorks() throws Exception {
        Player a=player("A",chunk(0,0));List<String> executed=new ArrayList<>();confirmations.addPending(a.getUniqueId(),ConfirmationManager.ActionType.CLAIM,uuid->executed.add("first"),uuid->{});
        var field=ConfirmationManager.class.getDeclaredField("pending");field.setAccessible(true);var map=(Map<?,?>)field.get(confirmations);Object action=map.get(a.getUniqueId());var tokenField=action.getClass().getDeclaredField("token");tokenField.setAccessible(true);String token=(String)tokenField.get(action);
        confirmations.addPending(a.getUniqueId(),ConfirmationManager.ActionType.SELL,uuid->executed.add("second"),uuid->{});assertFalse(confirmations.confirm(a.getUniqueId(),token));assertTrue(confirmations.confirm(a.getUniqueId()));assertFalse(confirmations.confirm(a.getUniqueId()));assertEquals(List.of("second"),executed);
    }
    @Test void timeoutRemovesPendingAction(){Player a=player("A",chunk(0,0));confirmations.addPending(a.getUniqueId(),ConfirmationManager.ActionType.CLAIM,uuid->fail("expired"),uuid->{});timers.get(timers.size()-1).run();assertFalse(confirmations.confirm(a.getUniqueId()));}
}
