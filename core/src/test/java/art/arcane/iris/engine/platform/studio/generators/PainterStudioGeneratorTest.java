package art.arcane.iris.engine.platform.studio.generators;

import art.arcane.iris.core.runtime.StudioPainterSlots;
import art.arcane.iris.engine.data.chunk.TerrainChunk;
import art.arcane.iris.engine.platform.studio.TerrainChunkCanvas;
import art.arcane.iris.spi.PlatformBlockState;
import com.volmit.block.BlockState;
import com.volmit.object.BlockEntityData;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.world.ChunkPainter;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PainterStudioGeneratorTest {
    private static final BlockEntityData CHEST_DATA =
            new BlockEntityData(ResourceId.parse("iris:tile"), 1, new byte[] {1});

    private TerrainChunk chunk;
    private final List<StudioPainterSlots.PaintedChunk> sunk = new ArrayList<>();

    @Before
    public void anEmptyChunk() {
        chunk = mock(TerrainChunk.class);
        when(chunk.getMinHeight()).thenReturn(0);
        when(chunk.getMaxHeight()).thenReturn(256);
    }

    private PainterStudioGenerator generatorPainting(ChunkPainter painter) {
        StudioPainterSlots.Slot slot = new StudioPainterSlots.Slot(painter, sunk::add);
        return new PainterStudioGenerator(slot, terrain -> new TerrainChunkCanvas(terrain, key -> mock(PlatformBlockState.class)));
    }

    @Test
    public void theChunkIsWhatThePainterPaintsForThatChunkPosition() {
        List<int[]> asked = new ArrayList<>();
        PainterStudioGenerator generator = generatorPainting((chunkX, chunkZ, canvas) -> {
            asked.add(new int[] {chunkX, chunkZ});
            canvas.set(0, 5, 0, BlockState.parse("minecraft:stone"));
        });

        generator.paint(chunk, 3, -4);

        assertEquals(1, asked.size());
        assertEquals(3, asked.get(0)[0]);
        assertEquals(-4, asked.get(0)[1]);
        verify(chunk).setBlock(eq(0), eq(5), eq(0), any());
        assertTrue("no block entities were painted", sunk.isEmpty());
    }

    @Test
    public void blockEntitiesGoToTheSlotWithTheChunkTheyBelongTo() {
        PainterStudioGenerator generator = generatorPainting((chunkX, chunkZ, canvas) ->
                canvas.set(1, 64, 2, BlockState.parse("minecraft:chest"), CHEST_DATA));

        generator.paint(chunk, 7, 8);

        assertEquals(1, sunk.size());
        assertEquals(7, sunk.get(0).chunkX());
        assertEquals(8, sunk.get(0).chunkZ());
        assertEquals(List.of(new StudioPainterSlots.PaintedBlockEntity(1, 64, 2, CHEST_DATA)), sunk.get(0).blockEntities());
    }

    @Test
    public void aPainterThatThrowsLeavesTheChunkAsFarAsItGotAndDoesNotFailTheGeneration() {
        PainterStudioGenerator generator = generatorPainting((chunkX, chunkZ, canvas) -> {
            canvas.set(0, 5, 0, BlockState.parse("minecraft:stone"));
            throw new IllegalStateException("painter bug");
        });

        generator.paint(chunk, 0, 0);

        verify(chunk).setBlock(eq(0), eq(5), eq(0), any());
    }
}
