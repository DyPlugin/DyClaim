package dev.dyclaim;
import dev.dyclaim.platform.PlatformTasks;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaOwnershipOfferTest extends ManagerFixture {
    String scope="source";Player source,target;Deque<Runnable> sources=new ArrayDeque<>(),targets=new ArrayDeque<>(),regions=new ArrayDeque<>();
    @BeforeEach void nativeFlow(){
        var home=chunk(0,0);source=player("Source",home);target=player("Target",home);assertTrue(claims.claimChunk(source,home));
        values.set("confirmations.clickable-buttons",false);
        var tasks=mock(PlatformTasks.class);when(tasks.isRegionThreaded()).thenReturn(true);when(plugin.getPlatformTasks()).thenReturn(tasks);
        when(tasks.ownsEntity(any())).thenAnswer(i->i.getArgument(0)==source?scope.equals("source"):scope.equals("target"));
        when(tasks.playerLater(eq(source),any(),anyLong())).thenAnswer(i->{Runnable action=i.getArgument(1);sources.add(()->runAs("source",action));return mock(BukkitTask.class);});
        when(tasks.playerLater(eq(target),any(),anyLong())).thenAnswer(i->{Runnable action=i.getArgument(1);if((long)i.getArgument(2)==1)targets.add(()->runAs("target",action));return mock(BukkitTask.class);});
        when(tasks.entityLater(eq(target),any(),any(),anyLong())).thenAnswer(i->{Runnable action=i.getArgument(1);targets.add(()->runAs("target",action));return mock(BukkitTask.class);});
        when(tasks.regionLater(eq(world),eq(0),eq(0),any(),anyLong())).thenAnswer(i->{Runnable action=i.getArgument(3);regions.add(()->runAs("region",action));return mock(BukkitTask.class);});
        when(world.getChunkAt(0,0)).thenAnswer(i->{assertEquals("region",scope,"Accepted offer accessed a foreign claim chunk");return home;});
        when(source.isOnline()).thenAnswer(i->{assertEquals("source",scope);return true;});
        when(source.hasPermission("dyclaim.transfer")).thenAnswer(i->{assertEquals("source",scope);return true;});
        when(source.hasPermission("dyclaim.coowner")).thenAnswer(i->{assertEquals("source",scope);return true;});
    }
    void runAs(String next,Runnable action){String previous=scope;scope=next;try{action.run();}finally{scope=previous;}}
    void installOffer(){int limit=12;while(!targets.isEmpty()&&limit-->0)targets.remove().run();assertTrue(confirmations.hasPending(target.getUniqueId()));}
    void drain(){for(int i=0;i<15&&(!sources.isEmpty()||!targets.isEmpty()||!regions.isEmpty());i++){if(!sources.isEmpty())sources.remove().run();if(!regions.isEmpty())regions.remove().run();if(!targets.isEmpty())targets.remove().run();}}
    @Test void transferConsentRechecksSourceOnSourceOwnerAndCommitsOnClaimRegion(){
        features.handle(source,new String[]{"transfer","Target"});installOffer();
        runAs("target",()->assertTrue(confirmations.confirm(target.getUniqueId())));drain();
        assertEquals(target.getUniqueId(),claims.getByKey("world:0:0").getOwnerUUID());
        assertEquals(0,claims.getPlayerClaimCount(source.getUniqueId()));assertEquals(1,claims.getPlayerClaimCount(target.getUniqueId()));
    }
    @Test void revokedSourcePermissionPreventsAcceptedCoownerOffer(){
        features.handle(source,new String[]{"coowner","add","Target"});installOffer();
        when(source.hasPermission("dyclaim.coowner")).thenReturn(false);
        runAs("target",()->assertTrue(confirmations.confirm(target.getUniqueId())));drain();
        assertNull(claims.getByKey("world:0:0").getCoowner());assertTrue(regions.isEmpty());
    }
    @Test void marketplaceConfirmationAfterMovingRechecksAndPurchasesOnClaimRegion(){
        values.set("market.enabled",true);values.set("market.tax-percent",10.0);values.set("confirmations.required.market-purchase",true);
        claims.getByKey("world:0:0").setMarketPrice(1000.0);
        runAs("target",()->features.handle(target,new String[]{"market","buy"}));
        assertTrue(confirmations.hasPending(target.getUniqueId()));
        when(target.getLocation()).thenReturn(new org.bukkit.Location(world,4096,65,4096));
        runAs("target",()->assertTrue(confirmations.confirm(target.getUniqueId())));drain();
        assertEquals(target.getUniqueId(),claims.getByKey("world:0:0").getOwnerUUID());
        verify(economy).withdraw(target.getUniqueId(),1000.0);verify(economy).deposit(source.getUniqueId(),900.0);
    }
}
