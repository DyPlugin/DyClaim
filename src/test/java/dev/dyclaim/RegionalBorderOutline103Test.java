package dev.dyclaim;

import dev.dyclaim.platform.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RegionalBorderOutline103Test extends ManagerFixture {
    @Test void joinedBedrockOutlineRespectsCurrentPlayerDistanceBeforePacketsAreSent(){
        var c=chunk(0,0);var p=player("Owner",c);for(int x=0;x<5;x++)claims.claimChunk(p,chunk(x,0));
        when(plugin.isEnabled()).thenReturn(true);when(world.getMaxHeight()).thenReturn(320);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        when(p.getLocation()).thenReturn(new Location(world,8,64,8));values.set("visualization.max-distance",32);
        var tasks=mock(PlatformTasks.class);Deque<Runnable> regions=new ArrayDeque<>(),players=new ArrayDeque<>();
        when(tasks.regionLater(eq(world),anyInt(),anyInt(),any(),eq(1L))).thenAnswer(i->{regions.add(i.getArgument(3));return mock(BukkitTask.class);});
        when(tasks.playerLater(eq(p),any(),anyLong())).thenAnswer(i->{if(i.<Long>getArgument(2)==1)players.add(i.getArgument(1));return mock(BukkitTask.class);});
        BlockData border=mock(BlockData.class);bukkit.when(()->Bukkit.createBlockData(Material.ORANGE_STAINED_GLASS)).thenReturn(border);
        Block b=mock(Block.class);when(b.getType()).thenReturn(Material.AIR);when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(b);
        doAnswer(i->{Location l=i.getArgument(0);Location now=p.getLocation();assertTrue(Math.pow(l.getX()-now.getX(),2)+Math.pow(l.getZ()-now.getZ(),2)<=1024,"Fake border outside max-distance");return null;}).when(p).sendBlockChange(any(Location.class),eq(border));
        var view=new RegionalBorderView(plugin,tasks,actor->true);view.show(p,c);
        while(!regions.isEmpty())regions.remove().run();
        when(p.getLocation()).thenReturn(new Location(world,4,64,8));
        while(!players.isEmpty())players.remove().run();
        verify(p,atLeastOnce()).sendBlockChange(any(Location.class),eq(border));
    }
    @Test void joinedBedrockOutlineReadsEachRegionSeparatelyAndRestoresAllSentBlocks(){
        var c=chunk(0,0);var p=player("Owner",c);claims.claimChunk(p,c);claims.claimChunk(p,chunk(1,0));
        when(plugin.isEnabled()).thenReturn(true);when(world.getMaxHeight()).thenReturn(320);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        var tasks=mock(PlatformTasks.class);Deque<Runnable> regions=new ArrayDeque<>(),players=new ArrayDeque<>();int[] owned={-1};
        when(tasks.regionLater(eq(world),anyInt(),anyInt(),any(),eq(1L))).thenAnswer(i->{int x=i.getArgument(1);Runnable r=i.getArgument(3);regions.add(()->{owned[0]=x;try{r.run();}finally{owned[0]=-1;}});return mock(BukkitTask.class);});
        when(tasks.playerLater(eq(p),any(),anyLong())).thenAnswer(i->{if(i.<Long>getArgument(2)==1)players.add(i.getArgument(1));return mock(BukkitTask.class);});
        BlockData border=mock(BlockData.class),actual=mock(BlockData.class);bukkit.when(()->Bukkit.createBlockData(Material.ORANGE_STAINED_GLASS)).thenReturn(border);
        Block b=mock(Block.class);when(b.getType()).thenReturn(Material.AIR);when(b.getBlockData()).thenReturn(actual);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(i->{assertEquals(Math.floorDiv(i.<Integer>getArgument(0),16),owned[0],"Foreign region block read");return b;});
        var view=new RegionalBorderView(plugin,tasks,actor->true);view.show(p,c);
        while(!regions.isEmpty())regions.remove().run();while(!players.isEmpty())players.remove().run();
        verify(p,times(92)).sendBlockChange(any(Location.class),eq(border));
        verify(p,never()).sendBlockChange(argThat(l->(l.getBlockX()==15||l.getBlockX()==16)&&l.getBlockZ()>0&&l.getBlockZ()<15),eq(border));
        view.hide(p);while(!regions.isEmpty())regions.remove().run();while(!players.isEmpty())players.remove().run();
        verify(p,times(92)).sendBlockChange(any(Location.class),eq(actual));
    }
}
