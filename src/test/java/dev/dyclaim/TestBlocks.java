package dev.dyclaim;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import static org.mockito.Mockito.*;

/** Places a mocked block or location in a mocked chunk: the chunk itself and the world and block coordinates inside it. */
final class TestBlocks {
    private TestBlocks() {}

    static Block at(Block block, Chunk chunk) {
        World world = chunk.getWorld(); int x = chunk.getX() << 4, z = chunk.getZ() << 4;
        when(block.getChunk()).thenReturn(chunk); when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x); when(block.getZ()).thenReturn(z);
        return block;
    }

    static Location at(Location location, Chunk chunk) {
        World world = chunk.getWorld(); int x = chunk.getX() << 4, z = chunk.getZ() << 4;
        when(location.getChunk()).thenReturn(chunk); when(location.getWorld()).thenReturn(world);
        when(location.getBlockX()).thenReturn(x); when(location.getBlockZ()).thenReturn(z);
        return location;
    }
}
