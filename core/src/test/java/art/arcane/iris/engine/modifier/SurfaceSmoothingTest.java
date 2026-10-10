package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.post.PostProcessingSlabsSettings;
import art.arcane.iris.builtin.post.PostProcessingWallsSettings;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.decorator.DecoratorTestEngines;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.ChunkedDataCache;
import art.arcane.iris.util.project.hunk.Hunk;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Smoothing the surface (here without slabs and walls, which post processing switches off per dimension) removes a nib that floats above columns two blocks lower and leaves a flat surface alone. */
public class SurfaceSmoothingTest {
    private static final int NIB_X = 5;
    private static final int NIB_Z = 5;
    private static final int NIB_HEIGHT = 10;
    private static final int NEIGHBOUR_HEIGHT = 4;

    private final PlatformBlockState stone = UsableBlockPlatform.block("minecraft:stone");

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Test
    public void aNibFloatingAboveItsFourNeighboursIsRemoved() {
        UsableBlockPlatform.bind();
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, 32, 16);
        blocks.set(NIB_X, NIB_HEIGHT, NIB_Z, stone);

        new SurfaceSmoothing(engine(NIB_X, NIB_Z)).smooth(0, 0, blocks, false, context());

        assertNotNull(blocks.get(NIB_X, NIB_HEIGHT, NIB_Z));
        assertNotSame("the nib is replaced by air", stone, blocks.get(NIB_X, NIB_HEIGHT, NIB_Z));
    }

    @Test
    public void aFlatSurfaceIsLeftAlone() {
        UsableBlockPlatform.bind();
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, 32, 16);
        blocks.set(NIB_X, NEIGHBOUR_HEIGHT, NIB_Z, stone);

        new SurfaceSmoothing(engine(NIB_X, NIB_Z, NEIGHBOUR_HEIGHT)).smooth(0, 0, blocks, false, context());

        assertSame(stone, blocks.get(NIB_X, NEIGHBOUR_HEIGHT, NIB_Z));
    }

    /** The surface is {@code NEIGHBOUR_HEIGHT} everywhere except at {@code nibHeight} at the nib column. */
    private Engine engine(int nibX, int nibZ) {
        return engine(nibX, nibZ, NIB_HEIGHT);
    }

    private Engine engine(int nibX, int nibZ, int nibHeight) {
        EngineMantle mantle = mock(EngineMantle.class);
        when(mantle.trueHeight(anyInt(), anyInt())).thenAnswer(invocation ->
                (int) invocation.getArgument(0) == nibX && (int) invocation.getArgument(1) == nibZ ? nibHeight : NEIGHBOUR_HEIGHT);
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getModuleSettings()).thenReturn(BuiltInPack.settings(
                TestSettings.of(PostProcessingWallsSettings.class, "{\"enabled\": false}"),
                TestSettings.of(PostProcessingSlabsSettings.class, "{\"enabled\": false}")));
        SeedManager seeds = mock(SeedManager.class);
        Engine engine = mock(Engine.class);
        when(engine.getMantle()).thenReturn(mantle);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getSeedManager()).thenReturn(seeds);
        when(engine.getMetrics()).thenReturn(new EngineMetrics(16));
        DecoratorTestEngines.withGeneration(engine);
        return engine;
    }

    @SuppressWarnings("unchecked")
    private ChunkContext context() {
        ChunkedDataCache<IrisBiome> biomes = mock(ChunkedDataCache.class);
        when(biomes.get(anyInt(), anyInt())).thenReturn(mock(IrisBiome.class));
        ChunkContext context = mock(ChunkContext.class);
        when(context.getBiome()).thenReturn(biomes);
        return context;
    }
}
