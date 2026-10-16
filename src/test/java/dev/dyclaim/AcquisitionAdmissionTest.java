package dev.dyclaim;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AcquisitionAdmissionTest extends ManagerFixture {
    @Test void pendingTransferDoesNotGrantBuyersSpacingExemptionBeforeCommit(){
        values.set("claim-rules.spacing.enabled",true);values.set("claim-rules.spacing.minimum-empty-chunks",1);
        var sale=chunk(0,0);var seller=player("Seller",sale);var buyer=player("Buyer",sale);
        assertTrue(claims.claimChunk(seller,sale));var rules=plugin.getAcquisitionRules();
        try(var pending=rules.reserve(buyer,sale,claims.getClaimAt(sale),false)){
            assertNull(pending.rejection());
            try(var adjacent=rules.reserve(buyer,chunk(1,0),null,false)){assertEquals("acquire-spacing",adjacent.rejection());}
        }
        assertEquals(seller.getUniqueId(),claims.getClaimAt(sale).getOwnerUUID());
    }
    @Test void marketplaceAdmissionPreventsAnAdminGrantExceedingBuyersLimitDuringPayment(){
        values.set("claim.max-claims-per-player",1);values.set("market.enabled",true);values.set("confirmations.required.market-purchase",false);
        var sale=chunk(0,0);var extra=chunk(2,0);var seller=player("Seller",sale);var buyer=player("Buyer",sale);var admin=player("Admin",extra);
        when(admin.hasPermission("dyclaim.admin")).thenReturn(true);assertTrue(claims.claimChunk(seller,sale));claims.getClaimAt(sale).setMarketPrice(1000.0);
        when(economy.withdraw(eq(buyer.getUniqueId()),anyDouble())).thenAnswer(i->{features.handle(admin,new String[]{"admin","give","Buyer"});return true;});
        features.handle(buyer,new String[]{"market","buy"});
        assertEquals(1,claims.getPlayerClaimCount(buyer.getUniqueId()));assertNull(claims.getClaimAt(extra));
        assertEquals(buyer.getUniqueId(),claims.getClaimAt(sale).getOwnerUUID());assertTrue(transactions.unresolved().isEmpty());
    }
    @Test void pendingAdmissionAlsoBlocksACompetingFinancialTransaction(){
        var owner=player("Owner",chunk(0,0));
        try(var lease=plugin.getAcquisitionRules().reserve(owner,chunk(0,0),null,false)){
            assertNull(lease.rejection());
            assertFalse(transactions.execute("refund","world:0:0",null,owner.getUniqueId(),0,50,()->fail("Competing mutation ran")));
            verify(economy,never()).deposit(any(java.util.UUID.class),anyDouble());
        }
    }
    @Test void claimCommandAdmissionIsHeldAcrossProviderCallbacks(){
        values.set("claim.max-claims-per-player",1);values.set("confirmations.required.claim",false);
        var first=chunk(0,0);var second=chunk(2,0);var owner=player("Owner",first);
        var moved=mock(org.bukkit.Location.class);when(moved.getChunk()).thenReturn(second);when(moved.getWorld()).thenReturn(world);
        var withdrawals=new java.util.concurrent.atomic.AtomicInteger();
        when(economy.withdraw(any(java.util.UUID.class),anyDouble())).thenAnswer(i->{
            if(withdrawals.incrementAndGet()==1){when(owner.getLocation()).thenReturn(moved);features.requestClaim(owner);}
            return true;
        });
        features.requestClaim(owner);assertEquals(1,withdrawals.get());assertEquals(1,claims.getPlayerClaimCount(owner.getUniqueId()));
        assertNotNull(claims.getClaimAt(first));assertNull(claims.getClaimAt(second));assertTrue(transactions.unresolved().isEmpty());
    }
    @Test void pendingAcquisitionCountsTowardOwnerLimitBeforeAnyPaymentOrClaimInsertion(){
        values.set("claim.max-claims-per-player",1);var owner=player("Owner",chunk(0,0));var rules=plugin.getAcquisitionRules();
        try(var first=rules.reserve(owner,chunk(0,0),null,false)){
            assertNull(first.rejection());
            try(var second=rules.reserve(owner,chunk(2,0),null,false)){assertEquals("acquire-limit",second.rejection());}
        }
        try(var later=rules.reserve(owner,chunk(2,0),null,false)){assertNull(later.rejection());}
    }
    @Test void pendingAdjacentPurchasePreventsDifferentOwnerSpacingRace(){
        values.set("claim-rules.spacing.enabled",true);values.set("claim-rules.spacing.minimum-empty-chunks",1);
        var first=player("First",chunk(0,0));var second=player("Second",chunk(1,0));var rules=plugin.getAcquisitionRules();
        try(var a=rules.reserve(first,chunk(0,0),null,false)){
            assertNull(a.rejection());try(var b=rules.reserve(second,chunk(1,0),null,false)){assertEquals("acquire-spacing",b.rejection());}
            try(var own=rules.reserve(first,chunk(1,0),null,false)){assertNull(own.rejection());}
        }
    }
    @Test void committedReservationIsNotCountedAgainWhileItsHandleCloses(){
        values.set("claim.max-claims-per-player",2);var owner=player("Owner",chunk(0,0));var rules=plugin.getAcquisitionRules();
        try(var first=rules.reserve(owner,chunk(0,0),null,false)){
            assertNull(first.rejection());assertTrue(transactions.execute(first.transaction(),"claim","world:0:0",null,null,0,0,()->assertTrue(claims.claimChunk(owner,chunk(0,0)))));
            try(var second=rules.reserve(owner,chunk(2,0),null,false)){assertNull(second.rejection());}
        }
    }
}
