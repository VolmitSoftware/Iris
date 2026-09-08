package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.MantleComponent;
import art.arcane.iris.engine.mantle.MantlePass;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.mantle.MatterGenerator;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MantleCarvingComponentBoundaryRadiusTest {
    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    @Test
    public void boundaryWallResolutionSchedulesAdjacentCarvingChunks() {
        EngineMantle engineMantle = mock(EngineMantle.class);
        MantleCarvingComponent component = new MantleCarvingComponent(engineMantle);

        assertEquals(1, component.getRadius());
        assertEquals(1, Math.ceilDiv(component.getRadius(), 16));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void carvingSurfaceInputUsesNaturalTerrainInsteadOfHydrologyHeight() {
        IrisComplex complex = mock(IrisComplex.class);
        ProceduralStream<Double> naturalHeight = mock(ProceduralStream.class);
        when(complex.getNaturalHeightStream()).thenReturn(naturalHeight);
        when(naturalHeight.getDouble(anyDouble(), anyDouble())).thenReturn(70D);
        ChunkContext context = mock(ChunkContext.class);
        when(context.getComplex()).thenReturn(complex);

        int[] heights = MantleCarvingComponent.prepareChunkSurfaceHeights(2, -3, context, new int[256]);

        for (int height : heights) {
            assertEquals(70, height);
        }
        verify(complex, never()).getHeightStream();
        verify(context, never()).getHeight();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void matterGenerationRunsCarvingForAllAdjacentChunks() {
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.isUseMantle()).thenReturn(true);
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(mantle.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.use()).thenReturn(chunk);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(chunk).raiseFlagSuspend(any(), any(Runnable.class));

        EngineMantle engineMantle = mock(EngineMantle.class);
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getMantle()).thenReturn(engineMantle);
        when(engineMantle.getEngine()).thenReturn(engine);

        List<String> generatedChunks = new ArrayList<>();
        MantleCarvingComponent component = spy(new MantleCarvingComponent(engineMantle));
        doAnswer(invocation -> {
            int chunkX = invocation.getArgument(1);
            int chunkZ = invocation.getArgument(2);
            generatedChunks.add(chunkX + "," + chunkZ);
            return null;
        }).when(component).generateLayer(any(), anyInt(), anyInt(), any());

        TestMatterGenerator generator = new TestMatterGenerator(engine, mantle, component);
        generator.generateMatter(4, -7, false, mock(ChunkContext.class));

        assertEquals(List.of(
                "3,-8", "3,-7", "3,-6",
                "4,-8", "4,-7", "4,-6",
                "5,-8", "5,-7", "5,-6"
        ), generatedChunks);
    }

    private static final class TestMatterGenerator implements MatterGenerator {
        private final Engine engine;
        private final Mantle<Matter> mantle;
        private final List<MantlePass> components;

        private TestMatterGenerator(Engine engine, Mantle<Matter> mantle, MantleComponent component) {
            this.engine = engine;
            this.mantle = mantle;
            this.components = List.of(new MantlePass(List.of(component), 1, 0));
        }

        @Override
        public Engine getEngine() {
            return engine;
        }

        @Override
        public Mantle<Matter> getMantle() {
            return mantle;
        }

        @Override
        public int getRadius() {
            return 1;
        }

        @Override
        public int getRealRadius() {
            return 0;
        }

        @Override
        public List<MantlePass> getComponents() {
            return components;
        }
    }
}
