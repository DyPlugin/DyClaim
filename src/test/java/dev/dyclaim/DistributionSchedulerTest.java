package dev.dyclaim;
import dev.dyclaim.platform.PlatformTasks;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** One JAR: the scheduler follows the server, not a marker inside the JAR. */
class DistributionSchedulerTest {
    @Test void aNonFoliaServerKeepsBukkitScheduling(){assertFalse(PlatformTasks.distribution(mock(Plugin.class)).isRegionThreaded());}
    @Test void anOldPlatformMarkerNoLongerDecides(){
        var plugin=mock(Plugin.class);when(plugin.getResource("dyclaim-platform.properties")).thenReturn(new java.io.ByteArrayInputStream("platform=folia".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertFalse(PlatformTasks.distribution(plugin).isRegionThreaded(),"Folia scheduling is only used on a Folia server");
    }
}
