package dev.dyclaim;
import dev.dyclaim.manager.TeleportManager;
import dev.dyclaim.model.ClaimData;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TeleportCancellationTest extends ManagerFixture {
    @Test void externalTeleportCancelsWarmupAndAnAlreadyQueuedCallbackCannotTeleportLater(){
        var chunk=chunk(0,0);var owner=player("Owner",chunk);assertTrue(claims.claimChunk(owner,chunk));timers.clear();
        Location target=mock(Location.class);when(config.getTeleportWarmup()).thenReturn(3);
        TeleportManager manager=new TeleportManager(plugin){@Override public Location destination(ClaimData claim){return target;}};
        manager.startTeleport(owner,claims.getClaimAt(chunk));assertEquals(1,timers.size());
        manager.teleport(new PlayerTeleportEvent(owner,new Location(world,0,64,0),new Location(world,100,64,100),PlayerTeleportEvent.TeleportCause.COMMAND));
        timers.remove(0).run();verify(owner,never()).teleport(any(Location.class));verify(owner).sendMessage("tp-cancelled");
    }
}
