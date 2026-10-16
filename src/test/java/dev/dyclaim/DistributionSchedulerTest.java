package dev.dyclaim;
import dev.dyclaim.platform.PlatformTasks;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DistributionSchedulerTest {
    @Test void ordinaryDistributionPreservesBukkitScheduling(){assertFalse(PlatformTasks.distribution(mock(Plugin.class)).isRegionThreaded());}
    @Test void foliaDistributionSelectsNativeScheduling(){
        var plugin=mock(Plugin.class);when(plugin.getResource("dyclaim-platform.properties")).thenReturn(new ByteArrayInputStream("platform=folia".getBytes(StandardCharsets.UTF_8)));
        assertTrue(PlatformTasks.distribution(plugin).isRegionThreaded());
    }
    @Test void unknownDistributionCannotSilentlyUseTheWrongScheduler(){
        var plugin=mock(Plugin.class);when(plugin.getResource("dyclaim-platform.properties")).thenReturn(new ByteArrayInputStream("platform=unknown".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalStateException.class,()->PlatformTasks.distribution(plugin));
    }
}
