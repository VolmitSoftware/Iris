package art.arcane.iris.core.studio.noise;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.function.NoiseProvider;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TerrainHeightSourceTest {
    private static final double FLUID_HEIGHT = 63D;
    private static final int WORLD_HEIGHT = 320;
    private static final double HILL_X = 100D;
    private static final double HILL_HEIGHT = 90D;
    private static final long ANY_SEED = 7L;
    private static final double EXACT = 0D;

    private Engine engine;

    @Before
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void world() {
        engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        IrisDimension dimension = mock(IrisDimension.class);
        IrisData data = mock(IrisData.class);
        ProceduralStream<Double> heights = mock(ProceduralStream.class);
        when(heights.getDouble(anyDouble(), anyDouble()))
                .thenAnswer(call -> (double) call.getArgument(0) == HILL_X ? HILL_HEIGHT : FLUID_HEIGHT);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getData()).thenReturn(data);
        when(engine.getHeight()).thenReturn(WORLD_HEIGHT);
        when(complex.getFluidHeight()).thenReturn(FLUID_HEIGHT);
        when(complex.getHeightStream()).thenReturn(heights);
        when(dimension.getOverlayNoise()).thenReturn(new KList<>());
        ResourceLoader emptyLoader = mock(ResourceLoader.class);
        when(emptyLoader.getPossibleKeys()).thenReturn(new String[0]);
        when(data.getGeneratorLoader()).thenReturn(emptyLoader);
        when(data.getExpressionLoader()).thenReturn(emptyLoader);
    }

    @Test
    public void theTerrainIsListedInBlocksAboveTheFluidAndKeepsTheWorldsSeed() {
        NoiseSources.Source terrain = NoiseSources.of(engine).list().stream()
                .filter(source -> source.id().equals(NoiseSources.TERRAIN_HEIGHT_ID))
                .findFirst().orElseThrow();

        assertEquals("Terrain height", terrain.name());
        assertFalse(terrain.usesSeed());
        assertEquals(-FLUID_HEIGHT, terrain.blockRange().lowest(), EXACT);
        assertEquals(WORLD_HEIGHT - FLUID_HEIGHT, terrain.blockRange().highest(), EXACT);
    }

    @Test
    public void theTerrainSamplesTheWorldsHeightMinusTheFluid() {
        NoiseProvider terrain = NoiseSources.of(engine).sampler(NoiseSources.TERRAIN_HEIGHT_ID, ANY_SEED);

        assertEquals(HILL_HEIGHT - FLUID_HEIGHT, terrain.noise(HILL_X, 5), EXACT);
        assertEquals(0D, terrain.noise(0, 5), EXACT);
    }
}
