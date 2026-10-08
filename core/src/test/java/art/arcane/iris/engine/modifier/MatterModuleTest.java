package art.arcane.iris.engine.modifier;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.UpperDimensionContext;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Inserting copies the mantle's blocks of a chunk into it, below the upper dimension's surface where there is one. */
public class MatterModuleTest {
    @Before
    public void bindPlatform() {
        UsableBlockPlatform.bind();
    }

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    private static final int CHUNK_X = 1;
    private static final int CHUNK_Z = 2;
    private static final int UPPER_SURFACE = 100;

    private final PlatformBlockState planned = mock(PlatformBlockState.class);
    private final Mantle<Matter> mantle = mockMantle();
    private final MantleChunk<Matter> mantleChunk = mockMantleChunk();

    @Test
    public void aDimensionWithoutAMantleIsLeftAlone() {
        IrisDimension dimension = dimension(false);
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, 128, 16);

        new MatterModule.MatterInsertion(engine(dimension, null)).insert(CHUNK_X, CHUNK_Z, blocks);

        verifyNoInteractions(mantle);
        assertNull(blocks.get(3, 50, 4));
    }

    @Test
    public void plannedBlocksAreCopiedIntoTheChunk() {
        plan(new int[]{3, 50, 4}, new int[]{3, 120, 4});
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, 128, 16);

        new MatterModule.MatterInsertion(engine(dimension(true), null)).insert(CHUNK_X, CHUNK_Z, blocks);

        assertSame(planned, blocks.get(3, 50, 4));
        assertSame(planned, blocks.get(3, 120, 4));
        verify(mantleChunk).release();
    }

    @Test
    public void blocksAtOrAboveTheUpperDimensionSurfaceAreLeftOut() {
        plan(new int[]{3, UPPER_SURFACE - 1, 4}, new int[]{3, UPPER_SURFACE, 4});
        UpperDimensionContext upper = mock(UpperDimensionContext.class);
        when(upper.getUpperSurfaceY(anyInt(), anyInt())).thenReturn(UPPER_SURFACE);
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, 128, 16);

        new MatterModule.MatterInsertion(engine(dimension(true), upper)).insert(CHUNK_X, CHUNK_Z, blocks);

        assertSame(planned, blocks.get(3, UPPER_SURFACE - 1, 4));
        assertNull(blocks.get(3, UPPER_SURFACE, 4));
    }

    /** The mantle chunk holds {@code planned} at each of the given {@code {x, y, z}} positions. */
    private void plan(int[]... positions) {
        org.mockito.Mockito.doAnswer(invocation -> {
            Consumer4<Integer, Integer, Integer, PlatformBlockState> consumer = invocation.getArgument(1);
            for (int[] at : positions) {
                consumer.accept(at[0], at[1], at[2], planned);
            }
            return null;
        }).when(mantleChunk).iterate(any(), any());
    }

    private IrisDimension dimension(boolean useMantle) {
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.isUseMantle()).thenReturn(useMantle);
        when(dimension.getUpperDimensionGap()).thenReturn(0);
        return dimension;
    }

    @SuppressWarnings("unchecked")
    private Engine engine(IrisDimension dimension, UpperDimensionContext upper) {
        EngineMantle engineMantle = mock(EngineMantle.class);
        when(engineMantle.getMantle()).thenReturn(mantle);
        ProceduralStream<Double> heights = mock(ProceduralStream.class);
        when(heights.get(anyDouble(), anyDouble())).thenReturn(0D);
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.getHeightStream()).thenReturn(heights);
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getMantle()).thenReturn(engineMantle);
        when(engine.getUpperContext()).thenReturn(upper);
        when(engine.getComplex()).thenReturn(complex);
        return engine;
    }

    @SuppressWarnings("unchecked")
    private Mantle<Matter> mockMantle() {
        return mock(Mantle.class);
    }

    @SuppressWarnings("unchecked")
    private MantleChunk<Matter> mockMantleChunk() {
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(chunk.use()).thenReturn(chunk);
        when(mantle.getChunk(CHUNK_X, CHUNK_Z)).thenReturn(chunk);
        return chunk;
    }
}
