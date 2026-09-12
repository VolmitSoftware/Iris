package art.arcane.iris.world.runtime;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class InPlaceChunkRegeneratorDiffTest {
    private static final int MIN_HEIGHT = 0;
    private static final int MAX_HEIGHT = 8;

    @Test
    public void applyBlockDiffsSkipsAllWritesWhenChunkMatchesBuffer() {
        BlockData stone = mock(BlockData.class);
        when(stone.getAsString()).thenReturn("minecraft:stone");
        ChunkSnapshot snapshot = mock(ChunkSnapshot.class);
        when(snapshot.getBlockType(anyInt(), anyInt(), anyInt())).thenReturn(Material.STONE);
        when(snapshot.getBlockData(anyInt(), anyInt(), anyInt())).thenReturn(stone);

        ChunkData generated = mock(ChunkData.class);
        when(generated.getType(anyInt(), anyInt(), anyInt())).thenReturn(Material.STONE);
        when(generated.getBlockData(anyInt(), anyInt(), anyInt())).thenReturn(stone);

        Chunk chunk = mock(Chunk.class);
        Block block = mock(Block.class);
        when(chunk.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(block);

        InPlaceChunkRegenerator.applyBlockDiffs(chunk, snapshot, generated, MIN_HEIGHT, MAX_HEIGHT);

        verify(block, never()).setBlockData(eq(stone), eq(false));
    }

    @Test
    public void applyBlockDiffsWritesOnlyMaterialChanges() {
        BlockData stone = mock(BlockData.class);
        when(stone.getAsString()).thenReturn("minecraft:stone");
        BlockData dirt = mock(BlockData.class);
        when(dirt.getAsString()).thenReturn("minecraft:dirt");
        ChunkSnapshot snapshot = mock(ChunkSnapshot.class);
        when(snapshot.getBlockType(anyInt(), anyInt(), anyInt())).thenReturn(Material.STONE);
        when(snapshot.getBlockData(anyInt(), anyInt(), anyInt())).thenReturn(stone);

        ChunkData generated = mock(ChunkData.class);
        when(generated.getType(anyInt(), anyInt(), anyInt())).thenReturn(Material.STONE);
        when(generated.getBlockData(anyInt(), anyInt(), anyInt())).thenReturn(stone);
        when(generated.getType(3, 5, 7)).thenReturn(Material.DIRT);
        when(generated.getBlockData(3, 5, 7)).thenReturn(dirt);

        Chunk chunk = mock(Chunk.class);
        Block block = mock(Block.class);
        when(chunk.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(block);

        InPlaceChunkRegenerator.applyBlockDiffs(chunk, snapshot, generated, MIN_HEIGHT, MAX_HEIGHT);

        verify(chunk, times(1)).getBlock(3, 5, 7);
        verify(block, times(1)).setBlockData(dirt, false);
        verify(block, never()).setBlockData(eq(stone), eq(false));
    }

    @Test
    public void applyBlockDiffsWritesWhenMaterialMatchesButStateDiffers() {
        BlockData liveStairs = mock(BlockData.class);
        when(liveStairs.getAsString()).thenReturn("minecraft:oak_stairs[facing=north]");
        BlockData rotatedStairs = mock(BlockData.class);
        when(rotatedStairs.getAsString()).thenReturn("minecraft:oak_stairs[facing=east]");
        ChunkSnapshot snapshot = mock(ChunkSnapshot.class);
        when(snapshot.getBlockType(anyInt(), anyInt(), anyInt())).thenReturn(Material.OAK_STAIRS);
        when(snapshot.getBlockData(anyInt(), anyInt(), anyInt())).thenReturn(liveStairs);

        ChunkData generated = mock(ChunkData.class);
        when(generated.getType(anyInt(), anyInt(), anyInt())).thenReturn(Material.OAK_STAIRS);
        when(generated.getBlockData(anyInt(), anyInt(), anyInt())).thenReturn(liveStairs);
        when(generated.getBlockData(1, 2, 3)).thenReturn(rotatedStairs);

        Chunk chunk = mock(Chunk.class);
        Block block = mock(Block.class);
        when(chunk.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(block);

        InPlaceChunkRegenerator.applyBlockDiffs(chunk, snapshot, generated, MIN_HEIGHT, MAX_HEIGHT);

        verify(block, times(1)).setBlockData(rotatedStairs, false);
    }
}
