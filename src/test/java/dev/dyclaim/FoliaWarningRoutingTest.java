package dev.dyclaim;
import dev.dyclaim.platform.PlatformTasks;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaWarningRoutingTest extends ManagerFixture {
    String scope="owner";Player owner,visitor;Deque<Runnable> owners=new ArrayDeque<>(),visitors=new ArrayDeque<>();
    @BeforeEach void configure(){
        var home=chunk(0,0);owner=player("Owner",home);visitor=player("Visitor",home);assertTrue(claims.claimChunk(owner,home));
        values.set("warnings.enabled",true);values.set("warnings.sound-enabled",false);values.set("warnings.required-count",3);
        var visitorLocation=visitor.getLocation();
        when(visitor.getLocation()).thenAnswer(i->{assertEquals("visitor",scope,"Visitor location accessed from another owner");return visitorLocation;});
        var tasks=mock(PlatformTasks.class);when(tasks.isRegionThreaded()).thenReturn(true);when(plugin.getPlatformTasks()).thenReturn(tasks);
        when(tasks.ownsEntity(any())).thenAnswer(i->i.getArgument(0)==visitor?scope.equals("visitor"):scope.equals("owner"));
        when(tasks.playerLater(eq(visitor),any(),anyLong())).thenAnswer(i->{Runnable action=i.getArgument(1);visitors.add(()->runAs("visitor",action));return mock(BukkitTask.class);});
        when(tasks.playerLater(eq(owner),any(),anyLong())).thenAnswer(i->{Runnable action=i.getArgument(1);owners.add(()->runAs("owner",action));return mock(BukkitTask.class);});
    }
    void runAs(String next,Runnable action){String previous=scope;scope=next;try{action.run();}finally{scope=previous;}}
    void drain(){for(int i=0;i<12&&(!owners.isEmpty()||!visitors.isEmpty());i++){if(!owners.isEmpty())owners.remove().run();if(!visitors.isEmpty())visitors.remove().run();}}
    @Test void warningChecksVisitorOnlyOnTheirOwner(){
        assertDoesNotThrow(()->features.handle(owner,new String[]{"warn","Visitor"}));
        assertFalse(owners.isEmpty()&&visitors.isEmpty());drain();verify(visitor).sendMessage("warning-received");
    }
    @Test void revokedOwnerPermissionPreventsAQueuedWarning(){
        features.handle(owner,new String[]{"warn","Visitor"});when(owner.hasPermission("dyclaim.warn")).thenReturn(false);
        drain();verify(visitor,never()).sendMessage("warning-received");
    }
}
