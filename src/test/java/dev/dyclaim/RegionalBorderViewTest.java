package dev.dyclaim;
import dev.dyclaim.platform.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegionalBorderViewTest extends ManagerFixture {
    PlatformTasks tasks;RegionalBorderView view;Player actor;Chunk target;BlockData border,actual;
    String scope="player";Deque<Runnable> regions=new ArrayDeque<>(),players=new ArrayDeque<>(),expiry=new ArrayDeque<>();
    @BeforeEach void prepare() {
        target=chunk(0,0);actor=player("Viewer",target);tasks=mock(PlatformTasks.class);when(plugin.isEnabled()).thenReturn(true);
        when(world.getMaxHeight()).thenReturn(320);when(world.isChunkLoaded(0,0)).thenReturn(true);
        border=mock(BlockData.class);actual=mock(BlockData.class);
        bukkit.when(()->Bukkit.createBlockData(Material.ORANGE_STAINED_GLASS)).thenReturn(border);
        Block block=mock(Block.class);when(block.getType()).thenReturn(Material.AIR);when(block.getBlockData()).thenReturn(actual);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(i->{assertTrue(Set.of("region","shutdown").contains(scope),"World data read outside its region");return block;});
        doAnswer(i->{assertTrue(Set.of("player","shutdown").contains(scope),"Client packet sent outside entity owner");return null;}).when(actor).sendBlockChange(any(Location.class),any(BlockData.class));
        when(tasks.regionLater(eq(world),eq(0),eq(0),any(),eq(1L))).thenAnswer(i->{regions.add(i.getArgument(3));return mock(BukkitTask.class);});
        when(tasks.playerLater(eq(actor),any(),anyLong())).thenAnswer(i->{(i.<Long>getArgument(2)>1?expiry:players).add(i.getArgument(1));return mock(BukkitTask.class);});
        view=new RegionalBorderView(plugin,tasks,p->true);
    }
    /** Java players get glowing displays spawned on the region, shown on the player and removed on the region again. */
    @Test void displayBordersAreSpawnedHiddenShownToTheViewerAndRemovedOnTheirRegion() {
        BlockData lime=mock(BlockData.class);bukkit.when(()->Bukkit.createBlockData(Material.LIME_STAINED_GLASS)).thenReturn(lime);
        List<org.bukkit.entity.BlockDisplay> spawned=new ArrayList<>();
        when(world.spawn(any(Location.class),eq(org.bukkit.entity.BlockDisplay.class),org.mockito.ArgumentMatchers.<java.util.function.Consumer<? super org.bukkit.entity.BlockDisplay>>any())).thenAnswer(i->{
            assertEquals("region",scope,"displays are spawned on their region");
            var display=mock(org.bukkit.entity.BlockDisplay.class);when(display.isValid()).thenReturn(true);
            java.util.function.Consumer<org.bukkit.entity.BlockDisplay> setup=i.getArgument(2);setup.accept(display);spawned.add(display);return display;
        });
        var java=new RegionalBorderView(plugin,tasks,p->false);
        java.show(actor,target);assertTrue(spawned.isEmpty());
        region();assertEquals(4,spawned.size(),"one line per side of a single chunk");
        for(var display:spawned){verify(display).setVisibleByDefault(false);verify(display).setPersistent(false);verify(display).setGlowing(true);}
        players.remove().run();for(var display:spawned)verify(actor).showEntity(plugin,display);
        java.hide(actor);for(var display:spawned)verify(display,never()).remove();
        region();for(var display:spawned)verify(display).remove();
        assertFalse(java.isViewing(actor.getUniqueId()));
    }
    void region(){scope="region";try{regions.remove().run();}finally{scope="player";}}
    @Test void bedrockReadsAndRestoresCurrentBlocksOnRegionAndSendsOnPlayer() {
        view.show(actor,target);assertTrue(view.isViewing(actor.getUniqueId()));verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
        region();verify(actor,never()).sendBlockChange(any(Location.class),any(BlockData.class));
        players.remove().run();verify(actor,times(60)).sendBlockChange(any(Location.class),eq(border));
        view.hide(actor);assertFalse(view.isViewing(actor.getUniqueId()));region();players.remove().run();
        verify(actor,times(60)).sendBlockChange(any(Location.class),eq(actual));
    }
    @Test void hiddenPendingViewCannotSendBordersLater() {
        view.show(actor,target);view.hide(actor);region();
        verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());assertTrue(players.isEmpty());
    }
    @Test void oldRestorationCannotOverwriteNewlyShownBorders() {
        view.show(actor,target);region();players.remove().run();view.hide(actor);view.show(actor,target);
        region();region();players.removeLast().run();players.removeFirst().run();
        verify(actor,times(120)).sendBlockChange(any(Location.class),eq(border));
        verify(actor,never()).sendBlockChange(any(Location.class),eq(actual));assertTrue(view.isViewing(actor.getUniqueId()));
    }
    @Test void staleExpiryDoesNotCloseTheReplacementView() {
        view.show(actor,target);region();players.remove().run();Runnable stale=expiry.remove();view.hide(actor);
        view.show(actor,target);stale.run();assertTrue(view.isViewing(actor.getUniqueId()));
    }
    @Test void nativeVisualizerRoutesJavaFramesThroughPlayerTimer() {
        values.set("visualization.style","particles");
        when(tasks.isRegionThreaded()).thenReturn(true);when(plugin.getPlatformTasks()).thenReturn(tasks);
        BukkitTask timer=mock(BukkitTask.class);when(tasks.playerTimer(eq(actor),any(),eq(1L),eq(5L))).thenReturn(timer);
        var visualizer=new dev.dyclaim.visualizer.ChunkVisualizer(plugin);visualizer.showChunkBorders(actor,target);
        assertTrue(visualizer.isViewing(actor.getUniqueId()));verify(tasks).playerTimer(eq(actor),any(),eq(1L),eq(5L));
        verify(scheduler,never()).runTaskTimer(eq(plugin),any(Runnable.class),anyLong(),anyLong());
        visualizer.cleanupPlayer(actor);assertFalse(visualizer.isViewing(actor.getUniqueId()));verify(timer).cancel();
    }
    @Test void disabledShutdownOwnerRestoresWithoutSchedulingDisabledPluginWork() {
        view.show(actor,target);region();players.remove().run();clearInvocations(tasks);
        when(plugin.isEnabled()).thenReturn(false);when(tasks.ownsEntity(actor)).thenReturn(true);when(tasks.ownsRegion(world,0,0)).thenReturn(true);
        scope="shutdown";view.reset();verify(actor,times(60)).sendBlockChange(any(Location.class),eq(actual));
        verify(tasks,never()).regionLater(any(),anyInt(),anyInt(),any(),anyLong());
        verify(tasks,never()).playerLater(any(),any(),anyLong());assertFalse(view.isViewing(actor.getUniqueId()));
    }
}
