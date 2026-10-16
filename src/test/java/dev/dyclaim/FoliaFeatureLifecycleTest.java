package dev.dyclaim;
import dev.dyclaim.platform.PlatformTasks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaFeatureLifecycleTest extends ManagerFixture {
    Player online;String scope="global";List<Runnable> queued=new ArrayList<>();
    @BeforeEach void nativeTasks(){
        online=player("Online",chunk(0,0));var tasks=mock(PlatformTasks.class);when(tasks.isRegionThreaded()).thenReturn(true);
        when(tasks.ownsEntity(online)).thenAnswer(i->scope.equals("player"));
        when(tasks.playerLater(eq(online),any(),anyLong())).thenAnswer(i->{queued.add(i.getArgument(1));return mock(BukkitTask.class);});
        when(plugin.getPlatformTasks()).thenReturn(tasks);when(plugin.isEnabled()).thenReturn(true);
        when(online.hasPermission(anyString())).thenAnswer(i->{assertEquals("player",scope);return false;});
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(online));
    }
    @Test void constructionRecordsOnlinePlayerPermissionsOnTheirOwner(){
        assertDoesNotThrow(()->new dev.dyclaim.manager.FeatureManager(plugin));assertEquals(1,queued.size());
        scope="player";queued.remove(0).run();verify(online).hasPermission("dyclaim.admin.purge.exempt");
    }
    @Test void shutdownRecordsDirectlyWhenNativeShutdownOwnershipIsGranted(){
        scope="player";when(plugin.isEnabled()).thenReturn(false);assertDoesNotThrow(features::close);
        verify(online).hasPermission("dyclaim.admin.purge.exempt");assertTrue(queued.isEmpty());
    }
    @Test void disabledOutsidePlayerOwnerDoesNotQueryForeignPermissionsOrQueueImpossibleTasks(){
        when(plugin.isEnabled()).thenReturn(false);assertDoesNotThrow(features::close);
        verify(online,never()).hasPermission(anyString());assertTrue(queued.isEmpty());
    }
}
