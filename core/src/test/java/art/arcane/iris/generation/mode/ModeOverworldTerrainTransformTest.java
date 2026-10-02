package art.arcane.iris.generation.mode;

import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.transform.TerrainTransformRuntime;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ModeOverworldTerrainTransformTest {
    @Test
    public void studioRawSnapshotsKeepTerrainMatterOnTheBoundCallingThread() {
        Fixture fixture = new Fixture();
        doReturn(mock(TerrainTransformRuntime.class)).when(fixture.complex).getTerrainTransform();
        Thread owner = Thread.currentThread();
        doAnswer(invocation -> {
            assertSame(owner, Thread.currentThread());
            assertSame(fixture.context, IrisContext.require().getChunkContext());
            return null;
        }).when(fixture.mantle).generateTerrainMatter(eq(-2), eq(3), anyBoolean(), eq(fixture.context));
        try (IrisContext.Scope scope = IrisContext.open(fixture.engine, 41L, fixture.context)) {
            fixture.mode.generateTerrainMatterForChunk(-2, 3, false, fixture.context);
        }
        verify(fixture.mantle).generateTerrainMatter(-2, 3, false, fixture.context);
    }

    @Test
    public void ordinaryStudioTerrainStillUsesParallelMatter() {
        Fixture fixture = new Fixture();
        fixture.mode.generateTerrainMatterForChunk(-2, 3, false, fixture.context);
        verify(fixture.mantle).generateTerrainMatter(-2, 3, true, fixture.context);
    }

    @Test
    public void explicitParallelGenerationRemainsParallelWithATransformer() {
        Fixture fixture = new Fixture();
        doReturn(mock(TerrainTransformRuntime.class)).when(fixture.complex).getTerrainTransform();
        fixture.mode.generateTerrainMatterForChunk(-2, 3, true, fixture.context);
        verify(fixture.mantle).generateTerrainMatter(-2, 3, true, fixture.context);
    }

    private static final class Fixture {
        private final ModeOverworld mode = mock(ModeOverworld.class, CALLS_REAL_METHODS);
        private final Engine engine = mock(Engine.class);
        private final EngineMantle mantle = mock(EngineMantle.class);
        private final IrisComplex complex = mock(IrisComplex.class);
        private final ChunkContext context = mock(ChunkContext.class);

        private Fixture() {
            doReturn(engine).when(mode).getEngine();
            doReturn(mantle).when(engine).getMantle();
            doReturn(true).when(engine).isStudio();
            doReturn(complex).when(context).getComplex();
            doReturn(true).when(context).isNaturalTerrain();
        }
    }
}
