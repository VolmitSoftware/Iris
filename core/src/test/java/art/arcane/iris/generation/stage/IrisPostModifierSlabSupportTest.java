package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomePaletteLayer;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.ChunkedDataCache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisSlopeClip;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IrisPostModifierSlabSupportTest {
    private static final int SURFACE = 12;
    private static final int PLANE_WIDTH = 5;
    private static final NativeBlockState AIR = state("minecraft:air", false, true, false);
    private static final NativeBlockState WATER = state("minecraft:water", false, false, true);
    private static final NativeBlockState STONE = state("minecraft:stone", true, false, false);
    private static final NativeBlockState SLAB = state("minecraft:cobblestone_slab", true, false, false);

    @Test
    public void supportedStepReceivesSmoothingBlock() throws ReflectiveOperationException {
        assertSame(SLAB, smoothedBlock(STONE));
    }

    @Test
    public void carvedAwaySurfaceDoesNotReceiveFloatingSmoothingBlock() throws ReflectiveOperationException {
        assertSame(AIR, smoothedBlock(AIR));
    }

    @Test
    public void fluidSurfaceDoesNotSupportSmoothingBlock() throws ReflectiveOperationException {
        assertSame(AIR, smoothedBlock(WATER));
    }

    @SuppressWarnings("unchecked")
    private NativeBlockState smoothedBlock(NativeBlockState support) throws ReflectiveOperationException {
        Hunk<NativeBlockState> output = Hunk.newHunk(3, 24, 3);
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                for (int y = 0; y < output.getHeight(); y++) {
                    output.set(x, y, z, y <= SURFACE ? STONE : AIR);
                }
            }
        }
        output.set(1, SURFACE, 1, support);
        output.set(2, SURFACE + 1, 1, STONE);

        IrisBiomePaletteLayer slabLayer = mock(IrisBiomePaletteLayer.class);
        doReturn(new IrisSlopeClip()).when(slabLayer).getSlopeCondition();
        doReturn(SLAB).when(slabLayer).get(any(RNG.class), anyDouble(), anyDouble(), anyDouble(), any());
        IrisBiome biome = mock(IrisBiome.class);
        doReturn(slabLayer).when(biome).getSlab();
        ChunkedDataCache<IrisBiome> biomes = mock(ChunkedDataCache.class);
        doReturn(biome).when(biomes).get(1, 1);
        ChunkContext context = mock(ChunkContext.class);
        doReturn(biomes).when(context).getBiome();

        ProceduralStream<Double> zeroStream = mock(ProceduralStream.class);
        doReturn(0D).when(zeroStream).get(anyDouble(), anyDouble());
        IrisComplex complex = mock(IrisComplex.class);
        doReturn(zeroStream).when(complex).getRiverCarveWeightStream();
        doReturn(zeroStream).when(complex).getRiverWaterSurfaceStream();
        doReturn(zeroStream).when(complex).getSlopeStream();
        Engine engine = mock(Engine.class);
        doReturn(complex).when(engine).getComplex();
        doReturn(mock(IrisData.class)).when(engine).getData();

        IrisPostModifier modifier = mock(IrisPostModifier.class, CALLS_REAL_METHODS);
        doReturn(engine).when(modifier).getEngine();
        Field rng = IrisPostModifier.class.getDeclaredField("rng");
        rng.setAccessible(true);
        rng.set(modifier, new RNG(88L));
        int[] heights = new int[PLANE_WIDTH * PLANE_WIDTH];
        Arrays.fill(heights, SURFACE);
        heights[2 * PLANE_WIDTH + 3] = SURFACE + 1;

        Method post = IrisPostModifier.class.getDeclaredMethod("post", int.class, int.class, Hunk.class,
                int.class, int.class, ChunkContext.class, int[].class, int.class, boolean.class, boolean.class);
        post.setAccessible(true);
        post.invoke(modifier, 1, 1, output, 1, 1, context, heights, PLANE_WIDTH, false, true);
        return output.get(1, SURFACE + 1, 1);
    }

    private static NativeBlockState state(String key, boolean solid, boolean air, boolean fluid) {
        NativeBlockState state = mock(NativeBlockState.class);
        doReturn(key).when(state).key();
        doReturn(key).when(state).materialKey();
        doReturn(solid).when(state).isSolid();
        doReturn(air).when(state).isAir();
        doReturn(fluid).when(state).isFluid();
        return state;
    }
}
