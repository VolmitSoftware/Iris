package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.bake.BakePlane;
import art.arcane.iris.generation.bake.ChunkBakeCache;
import art.arcane.iris.generation.bake.PlaneSource;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMetrics;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.stream.BakedDoubleStream;
import art.arcane.iris.generation.stream.BakedStream;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MantleCarvingComponentBakedFieldTest {
    private static final double FLUID_HEIGHT = 63D;
    private static final int[][] ORDER = {
            {1, 1}, {-2, 0}, {0, 0}, {2, -1}, {-1, -1}, {0, 1}, {1, -2}, {-2, 2}, {2, 2}, {-1, 1}, {0, -1}, {1, 0}
    };

    private final IrisCaveProfile dimensionProfile = new IrisCaveProfile().setEnabled(true);
    private final IrisCaveProfile regionProfile = new IrisCaveProfile().setEnabled(true);
    private final IrisCaveProfile surfaceProfile = new IrisCaveProfile().setEnabled(true);
    private final IrisCaveProfile shallowCaveProfile = new IrisCaveProfile().setEnabled(true);
    private final IrisCaveProfile deepCaveProfile = new IrisCaveProfile().setEnabled(true);
    private final IrisRegion profiledRegion = new IrisRegion().setCaveProfile(regionProfile);
    private final IrisRegion plainRegion = new IrisRegion();
    private final IrisBiome profiledSurface = new IrisBiome().setCaveProfile(surfaceProfile);
    private final IrisBiome plainSurface = new IrisBiome();
    private final IrisBiome shallowCave = new IrisBiome().setCaveProfile(shallowCaveProfile);
    private final IrisBiome deepCave = new IrisBiome().setCaveProfile(deepCaveProfile).setCaveMinDepthBelowSurface(60);
    private final IrisBiome plainCave = new IrisBiome();
    private final NativeBlockState water = fluid(true);
    private final NativeBlockState air = fluid(false);

    @Test
    public void bakedFieldReadsMatchPerColumnStreamReads() throws Exception {
        List<String> plain = run(false, false);
        List<String> baked = run(true, false);
        List<String> warmed = run(true, true);

        assertTrue(plain.size() > ORDER.length);
        assertEquals(plain, baked);
        assertEquals(plain, warmed);
    }

    @Test
    public void apronHeightsAreComputedThroughThePlaneSourceAndNeverPerColumn() throws Exception {
        AtomicInteger scalarHeights = new AtomicInteger();
        Harness harness = harness(true, scalarHeights);
        for (int[] chunk : ORDER) {
            harness.component.generateLayer(harness.writer, chunk[0], chunk[1], harness.context);
        }

        assertEquals(0, scalarHeights.get());
    }

    private List<String> run(boolean baked, boolean warm) throws Exception {
        Harness harness = harness(baked, new AtomicInteger());
        if (warm) {
            for (int x = -40; x < 56; x += 3) {
                for (int z = -40; z < 56; z += 5) {
                    harness.complex.getNaturalHeightStream().getDouble(x, z);
                    harness.complex.getCaveBiomeStream().get(x, z);
                    harness.complex.getFluidStream().get(x, z);
                }
            }
        }
        for (int[] chunk : ORDER) {
            harness.component.generateLayer(harness.writer, chunk[0], chunk[1], harness.context);
        }
        return harness.calls;
    }

    @SuppressWarnings("unchecked")
    private Harness harness(boolean baked, AtomicInteger scalarHeights) throws ReflectiveOperationException {
        Engine engine = mock(Engine.class);
        EngineMantle engineMantle = mock(EngineMantle.class);
        when(engineMantle.getEngine()).thenReturn(engine);
        MantleCarvingComponent component = new MantleCarvingComponent(engineMantle);
        IrisDimension dimension = new IrisDimension();
        dimension.setCaveProfile(dimensionProfile);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getWorld()).thenReturn(IrisWorld.builder().minHeight(0).maxHeight(128).build());
        when(engine.getMetrics()).thenReturn(new EngineMetrics(16));
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.allowsMantleChunkWrite(anyInt(), anyInt())).thenReturn(true);
        when(complex.getFluidHeight()).thenReturn(FLUID_HEIGHT);

        ProceduralStream<Double> heights = mock(ProceduralStream.class);
        when(heights.getDouble(anyDouble(), anyDouble())).thenAnswer(call -> {
            scalarHeights.incrementAndGet();
            return height((int) (double) call.getArgument(0), (int) (double) call.getArgument(1));
        });
        ProceduralStream<IrisRegion> regions = objects(this::region);
        ProceduralStream<IrisBiome> surfaces = objects(this::surface);
        ProceduralStream<IrisBiome> caves = objects(this::cave);
        ProceduralStream<NativeBlockState> fluids = objects(this::fluidAt);
        if (baked) {
            ChunkBakeCache cache = new ChunkBakeCache(new FunctionSource(), 256);
            when(complex.getNaturalHeightStream()).thenReturn(new BakedDoubleStream(cache, BakePlane.NATURAL_HEIGHT, heights));
            when(complex.getRegionStream()).thenReturn(new BakedStream<>(cache, BakePlane.REGION, regions));
            when(complex.getNaturalTrueBiomeStream()).thenReturn(new BakedStream<>(cache, BakePlane.NATURAL_TRUE_BIOME, surfaces));
            when(complex.getCaveBiomeStream()).thenReturn(new BakedStream<>(cache, BakePlane.CAVE_BIOME, caves));
            when(complex.getFluidStream()).thenReturn(new BakedStream<>(cache, BakePlane.FLUID, fluids));
        } else {
            when(complex.getNaturalHeightStream()).thenReturn(heights);
            when(complex.getRegionStream()).thenReturn(regions);
            when(complex.getNaturalTrueBiomeStream()).thenReturn(surfaces);
            when(complex.getCaveBiomeStream()).thenReturn(caves);
            when(complex.getFluidStream()).thenReturn(fluids);
        }
        ChunkContext context = mock(ChunkContext.class);
        when(context.getComplex()).thenReturn(complex);
        MantleWriter writer = mock(MantleWriter.class);
        Mantle<Matter> storage = mock(Mantle.class);
        when(storage.getWorldHeight()).thenReturn(128);
        when(writer.getMantle()).thenReturn(storage);

        List<String> calls = new ArrayList<>();
        Map<IrisCaveProfile, IrisCaveCarver3D> carvers = new IdentityHashMap<>();
        IrisCaveProfile[] profiles = {dimensionProfile, regionProfile, surfaceProfile, shallowCaveProfile, deepCaveProfile};
        for (int index = 0; index < profiles.length; index++) {
            int profileIndex = index;
            IrisCaveCarver3D carver = mock(IrisCaveCarver3D.class);
            doAnswer(call -> {
                IrisRange range = call.getArgument(6);
                calls.add(call.getArgument(1) + "," + call.getArgument(2) + " profile=" + profileIndex
                        + " range=" + (range == null ? "-" : range.getMin() + ".." + range.getMax())
                        + " weights=" + Arrays.toString((double[]) call.getArgument(3))
                        + " heights=" + Arrays.toString((int[]) call.getArgument(7))
                        + " boundaries=" + Arrays.toString((long[]) call.getArgument(8)));
                return 0;
            }).when(carver).carve(any(), anyInt(), anyInt(), any(), anyDouble(), anyDouble(),
                    any(), any(), any(), any(), any());
            carvers.put(profiles[index], carver);
        }
        Field profileCarvers = MantleCarvingComponent.class.getDeclaredField("profileCarvers");
        profileCarvers.setAccessible(true);
        profileCarvers.set(component, carvers);
        return new Harness(component, complex, context, writer, calls);
    }

    @SuppressWarnings("unchecked")
    private static <T> ProceduralStream<T> objects(ColumnFunction<T> function) {
        ProceduralStream<T> stream = mock(ProceduralStream.class);
        when(stream.get(anyDouble(), anyDouble())).thenAnswer(call ->
                function.at((int) (double) call.getArgument(0), (int) (double) call.getArgument(1)));
        return stream;
    }

    private static double height(int x, int z) {
        return 64D + 9.5D * Math.sin(x * 0.37D) * Math.cos(z * 0.23D) + Math.floorMod(x * 31 + z * 17, 7) * 0.31D;
    }

    private IrisRegion region(int x, int z) {
        return Math.floorMod((x >> 3) + (z >> 3), 3) == 0 ? profiledRegion : plainRegion;
    }

    private IrisBiome surface(int x, int z) {
        return Math.floorMod(x * 7 + z * 3, 11) < 4 ? profiledSurface : plainSurface;
    }

    private IrisBiome cave(int x, int z) {
        return switch (Math.floorMod((x >> 2) ^ (z >> 2), 5)) {
            case 0 -> shallowCave;
            case 1, 2 -> deepCave;
            case 3 -> plainCave;
            default -> null;
        };
    }

    private NativeBlockState fluidAt(int x, int z) {
        return Math.floorMod(x + z, 5) == 0 ? air : water;
    }

    private static NativeBlockState fluid(boolean fluid) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.isFluid()).thenReturn(fluid);
        return state;
    }

    private interface ColumnFunction<T> {
        T at(int x, int z);
    }

    private final class FunctionSource implements PlaneSource {
        @Override
        public void fill(BakePlane plane, int chunkX, int chunkZ, long[] missing, Object values) {
            for (int index = 0; index < 256; index++) {
                if ((missing[index >>> 6] & (1L << (index & 63))) == 0L) {
                    continue;
                }
                int x = (chunkX << 4) + (index & 15);
                int z = (chunkZ << 4) + (index >> 4);
                switch (plane) {
                    case NATURAL_HEIGHT -> ((double[]) values)[index] = height(x, z);
                    case REGION -> ((Object[]) values)[index] = region(x, z);
                    case NATURAL_TRUE_BIOME -> ((Object[]) values)[index] = surface(x, z);
                    case CAVE_BIOME -> ((Object[]) values)[index] = cave(x, z);
                    case FLUID -> ((Object[]) values)[index] = fluidAt(x, z);
                    default -> throw new IllegalStateException("Unexpected plane " + plane);
                }
            }
        }

        @Override
        public boolean mainThread() {
            return false;
        }
    }

    private record Harness(MantleCarvingComponent component, IrisComplex complex, ChunkContext context,
                           MantleWriter writer, List<String> calls) {
    }
}
