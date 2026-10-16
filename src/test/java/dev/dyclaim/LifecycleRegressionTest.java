package dev.dyclaim;

import dev.dyclaim.manager.*;
import dev.dyclaim.visualizer.ChunkVisualizer;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LifecycleRegressionTest extends ManagerFixture {
    private void field(String name,Object value) throws Exception {
        var field=DyClaim.class.getDeclaredField(name);field.setAccessible(true);field.set(plugin,value);
    }
    @Test void failingFeatureShutdownStillSavesClaimsAndCleansVisuals() throws Exception {
        var feature=mock(FeatureManager.class);var visual=mock(ChunkVisualizer.class);List<String> completed=new ArrayList<>();
        doThrow(new IllegalStateException("state write failed")).when(feature).close();
        var claim=mock(ClaimManager.class);doAnswer(i->{completed.add("claims");return null;}).when(claim).saveAllSync();
        doAnswer(i->{completed.add("visuals");return null;}).when(visual).cleanupAll();
        field("featureManager",feature);field("claimManager",claim);field("chunkVisualizer",visual);
        doCallRealMethod().when(plugin).onDisable();assertDoesNotThrow(plugin::onDisable);
        assertEquals(List.of("claims","visuals"),completed);
    }
    @Test void failingClaimSaveStillCleansVisuals() throws Exception {
        var claim=mock(ClaimManager.class);var visual=mock(ChunkVisualizer.class);List<String> completed=new ArrayList<>();
        doThrow(new IllegalStateException("claim write failed")).when(claim).saveAllSync();
        doAnswer(i->{completed.add("visuals");return null;}).when(visual).cleanupAll();
        field("claimManager",claim);field("chunkVisualizer",visual);
        doCallRealMethod().when(plugin).onDisable();assertDoesNotThrow(plugin::onDisable);assertEquals(List.of("visuals"),completed);
    }
}
