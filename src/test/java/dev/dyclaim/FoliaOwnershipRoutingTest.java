package dev.dyclaim;
import dev.dyclaim.platform.PlatformTasks;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaOwnershipRoutingTest {
    @Test void nativeOwnershipQueriesUseActualPlatformOwnershipWithoutInferringFromPluginEnabledState() {
        DyClaim plugin=mock(DyClaim.class);Server server=mock(Server.class);when(plugin.getServer()).thenReturn(server);
        World world=mock(World.class);Entity entity=mock(Entity.class);
        when(server.isOwnedByCurrentRegion(entity)).thenReturn(true);
        when(server.isOwnedByCurrentRegion(world,-2,3)).thenReturn(true);
        var tasks=PlatformTasks.folia(plugin);
        assertTrue(tasks.ownsEntity(entity));assertTrue(tasks.ownsRegion(world,-2,3));
        when(server.isOwnedByCurrentRegion(entity)).thenReturn(false);assertFalse(tasks.ownsEntity(entity));
        assertFalse(tasks.ownsRegion(world,-2,4));
    }
}
