package art.arcane.iris.world.runtime;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChunkClearerBlocksTest {
    private static final int MIN_HEIGHT = 0;
    private static final int MAX_HEIGHT = 16;

    @Test
    public void clearBlocksOnlyIteratesUpToColumnHeightmap() {
        ChunkSnapshot snapshot = mock(ChunkSnapshot.class);
        when(snapshot.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(3);
        when(snapshot.getBlockType(anyInt(), anyInt(), anyInt())).thenReturn(Material.STONE);

        Chunk chunk = mock(Chunk.class);
        Block block = mock(Block.class);
        when(chunk.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(block);

        ChunkClearer.clearBlocks(chunk, snapshot, MIN_HEIGHT, MAX_HEIGHT);

        verify(block, times(16 * 16 * 5)).setType(Material.AIR, false);
    }

    @Test
    public void clearBlocksSkipsAirPositions() {
        ChunkSnapshot snapshot = mock(ChunkSnapshot.class);
        when(snapshot.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(2);
        when(snapshot.getBlockType(anyInt(), anyInt(), anyInt())).thenReturn(Material.AIR);
        when(snapshot.getBlockType(4, 1, 9)).thenReturn(Material.STONE);

        Chunk chunk = mock(Chunk.class);
        Block block = mock(Block.class);
        when(chunk.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(block);

        ChunkClearer.clearBlocks(chunk, snapshot, MIN_HEIGHT, MAX_HEIGHT);

        verify(chunk, times(1)).getBlock(4, 1, 9);
        verify(block, times(1)).setType(Material.AIR, false);
    }

    @Test
    public void clearBlocksHandlesEmptyColumns() {
        ChunkSnapshot snapshot = mock(ChunkSnapshot.class);
        when(snapshot.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(MIN_HEIGHT - 1);
        when(snapshot.getBlockType(anyInt(), anyInt(), anyInt())).thenReturn(Material.AIR);

        Chunk chunk = mock(Chunk.class);
        Block block = mock(Block.class);
        when(chunk.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(block);

        ChunkClearer.clearBlocks(chunk, snapshot, MIN_HEIGHT, MAX_HEIGHT);

        verify(block, never()).setType(Material.AIR, false);
    }
}
