package art.arcane.iris.engine.platform.studio;

import art.arcane.iris.core.runtime.StudioPainterSlots.PaintedBlockEntity;
import art.arcane.iris.engine.data.chunk.TerrainChunk;
import art.arcane.iris.spi.PlatformBlockState;
import com.volmit.block.BlockState;
import com.volmit.object.BlockEntityData;
import com.volmit.resource.ResourceId;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TerrainChunkCanvasTest {
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;
    private static final BlockEntityData CHEST_DATA =
            new BlockEntityData(ResourceId.parse("iris:tile"), 1, new byte[] {1, 2, 3});
    private static final BlockEntityData SIGN_DATA =
            new BlockEntityData(ResourceId.parse("iris:tile"), 1, new byte[] {9});

    private TerrainChunk chunk;
    private final Map<String, PlatformBlockState> platformStates = new HashMap<>();
    private TerrainChunkCanvas canvas;

    @Before
    public void anEmptyChunk() {
        chunk = mock(TerrainChunk.class);
        when(chunk.getMinHeight()).thenReturn(MIN_Y);
        when(chunk.getMaxHeight()).thenReturn(MAX_Y);
        canvas = new TerrainChunkCanvas(chunk, key -> platformStates.computeIfAbsent(key, ignored -> mock(PlatformBlockState.class)));
    }

    @Test
    public void theCanvasReportsTheHeightRangeOfTheChunk() {
        assertEquals(MIN_Y, canvas.minY());
        assertEquals(MAX_Y, canvas.maxY());
    }

    @Test
    public void aBlockGoesIntoTheChunkAsThePlatformsStateOfItsKey() {
        canvas.set(3, 70, 12, BlockState.parse("minecraft:oak_log[axis=y]"));

        PlatformBlockState written = platformStates.get("minecraft:oak_log[axis=y]");
        verify(chunk).setBlock(3, 70, 12, written);
    }

    @Test
    public void thePlatformIsAskedOnceForAKeyAndItsAnswerIsReused() {
        canvas.set(0, 0, 0, BlockState.parse("minecraft:stone"));
        canvas.set(1, 0, 0, BlockState.parse("minecraft:stone"));

        PlatformBlockState stone = platformStates.get("minecraft:stone");
        verify(chunk).setBlock(0, 0, 0, stone);
        verify(chunk).setBlock(1, 0, 0, stone);
        assertEquals(1, platformStates.size());
    }

    @Test
    public void positionsOutsideTheChunkOrTheWorldAreRefusedAndNothingIsWritten() {
        BlockState stone = BlockState.parse("minecraft:stone");

        assertThrows(IllegalArgumentException.class, () -> canvas.set(16, 0, 0, stone));
        assertThrows(IllegalArgumentException.class, () -> canvas.set(-1, 0, 0, stone));
        assertThrows(IllegalArgumentException.class, () -> canvas.set(0, 0, 16, stone));
        assertThrows(IllegalArgumentException.class, () -> canvas.set(0, MIN_Y - 1, 0, stone));
        assertThrows(IllegalArgumentException.class, () -> canvas.set(0, MAX_Y, 0, stone));
        verify(chunk, never()).setBlock(anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    public void theLowestAndHighestBlocksOfTheWorldAreInside() {
        canvas.set(0, MIN_Y, 0, BlockState.parse("minecraft:bedrock"));
        canvas.set(15, MAX_Y - 1, 15, BlockState.parse("minecraft:glass"));

        verify(chunk).setBlock(0, MIN_Y, 0, platformStates.get("minecraft:bedrock"));
        verify(chunk).setBlock(15, MAX_Y - 1, 15, platformStates.get("minecraft:glass"));
    }

    @Test
    public void blockEntitiesAreKeptInPaintOrderWithTheirPositions() {
        canvas.set(1, 64, 2, BlockState.parse("minecraft:chest"), CHEST_DATA);
        canvas.set(4, 65, 5, BlockState.parse("minecraft:oak_sign"), SIGN_DATA);

        assertEquals(List.of(new PaintedBlockEntity(1, 64, 2, CHEST_DATA), new PaintedBlockEntity(4, 65, 5, SIGN_DATA)),
                canvas.blockEntities());
    }

    @Test
    public void paintingAnotherBlockOverABlockEntityDropsTheEntity() {
        canvas.set(1, 64, 2, BlockState.parse("minecraft:chest"), CHEST_DATA);
        canvas.set(1, 64, 2, BlockState.parse("minecraft:stone"));

        assertTrue(canvas.blockEntities().isEmpty());
    }

    @Test
    public void paintingTwoEntitiesAtOnePositionKeepsTheLaterOne() {
        canvas.set(1, 64, 2, BlockState.parse("minecraft:chest"), CHEST_DATA);
        canvas.set(1, 64, 2, BlockState.parse("minecraft:chest"), SIGN_DATA);

        assertEquals(List.of(new PaintedBlockEntity(1, 64, 2, SIGN_DATA)), canvas.blockEntities());
    }

    @Test
    public void aStateThePlatformLacksFailsThePaint() {
        TerrainChunkCanvas strict = new TerrainChunkCanvas(chunk, key -> {
            throw new IllegalArgumentException("no " + key);
        });

        assertThrows(IllegalArgumentException.class, () -> strict.set(0, 0, 0, BlockState.parse("example:nothing")));
    }
}
