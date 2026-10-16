package dev.dyclaim;
import dev.dyclaim.platform.PlatformTasks;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaTeleportRoutingTest {
    @Test void foliaTeleportUsesNativeAsyncTransferWithoutSynchronousTeleport(){
        var player=mock(Player.class);var destination=mock(Location.class);var result=new CompletableFuture<Boolean>();
        when(player.teleportAsync(destination)).thenReturn(result);
        assertSame(result,PlatformTasks.folia(mock(DyClaim.class)).teleport(player,destination));
        verify(player,never()).teleport(destination);
    }
    @Test void bukkitTeleportPreservesTheOriginalImmediateResult(){
        var player=mock(Player.class);var destination=mock(Location.class);when(player.teleport(destination)).thenReturn(true);
        assertTrue(PlatformTasks.bukkit(mock(DyClaim.class)).teleport(player,destination).join());
        verify(player,never()).teleportAsync(destination);
    }
    @Test void closedSchedulerCannotInitiateAnotherTeleport(){
        var player=mock(Player.class);var tasks=PlatformTasks.folia(mock(DyClaim.class));tasks.close();
        assertFalse(tasks.teleport(player,mock(Location.class)).join());verifyNoInteractions(player);
    }
}
