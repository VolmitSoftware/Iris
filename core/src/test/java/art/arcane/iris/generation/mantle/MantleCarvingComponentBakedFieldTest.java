package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.bake.BakePlane;
import art.arcane.iris.generation.bake.ChunkBakeCache;
import art.arcane.iris.generation.bake.PlaneSource;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.cave.IrisCave;
import art.arcane.iris.generation.cave.TestSettings;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMetrics;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.stream.BakedDoubleStream;
import art.arcane.iris.generation.stream.BakedStream;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.terrain.IrisRegionEntry;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.testsupport.FakeHydrology;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MantleCarvingComponentBakedFieldTest {
    private static final double FLUID_HEIGHT = 63D;
    private static final int WORLD_HEIGHT = 128;
    private static final int[][] ORDER = {
            {1, 1}, {-2, 0}, {0, 0}, {2, -1}, {-1, -1}, {0, 1}, {1, -2}, {-2, 2}, {2, 2}, {-1, 1}, {0, -1}, {1, 0}
    };
    private static final String HALLS = "{\"cavern\": {\"size\": 40, \"height\": 24, \"coverage\": 0.4}, \"surfaceClearance\": 4, \"allowFluid\": false}";

    private final IrisRegion listingRegion = new IrisRegion().setCaves(new KList<>("region-cave"));
    private final IrisRegion plainRegion = new IrisRegion();
    private final IrisBiome listingSurface = new IrisBiome().setCaves(new KList<>("surface-cave"));
    private final IrisBiome plainSurface = new IrisBiome();
    private final IrisBiome shallowCave = new IrisBiome().setCaves(new KList<>("shallow-cave"));
    private final IrisBiome deepCave = new IrisBiome().setCaves(new KList<>("deep-cave")).setCaveMinDepthBelowSurface(60);
    private final IrisBiome plainCave = new IrisBiome();
    private final NativeBlockState water = fluid(true);
    private final NativeBlockState air = fluid(false);

    @Test
    public void bakedFieldReadsMatchPerColumnStreamReads() {
        List<String> plain = run(false, false, true);
        List<String> baked = run(true, false, true);
        List<String> warmed = run(true, true, true);

        assertEquals(ORDER.length, plain.size());
        assertTrue(plain.toString(), plain.stream().anyMatch(carved -> !carved.contains(" carved=0 ")));
        assertEquals(plain, baked);
        assertEquals(plain, warmed);
    }

    @Test
    public void whenOnlyTheDimensionListsCavesTheFluidBoundaryFieldNeverReadsAHeightPerColumn() {
        AtomicInteger scalarHeights = new AtomicInteger();
        Harness harness = harness(true, false, scalarHeights);
        for (int[] chunk : ORDER) {
            harness.component.generateLayer(harness.writer, chunk[0], chunk[1], harness.context);
        }

        assertEquals(0, scalarHeights.get());
    }

    private List<String> run(boolean baked, boolean warm, boolean regionLists) {
        Harness harness = harness(baked, regionLists, new AtomicInteger());
        if (warm) {
            for (int x = -40; x < 56; x += 3) {
                for (int z = -40; z < 56; z += 5) {
                    harness.complex.getNaturalHeightStream().getDouble(x, z);
                    harness.complex.getCaveBiomeStream().get(x, z);
                    harness.complex.getFluidStream().get(x, z);
                }
            }
        }
        List<String> carved = new ArrayList<>();
        for (int[] chunk : ORDER) {
            harness.component.generateLayer(harness.writer, chunk[0], chunk[1], harness.context);
            carved.add(signature(chunk, harness.chunks.get(key(chunk[0], chunk[1]))));
        }
        return carved;
    }

    private static String signature(int[] chunk, Matter[] sections) {
        int carved = 0;
        long hash = 17;
        for (int y = 0; y < WORLD_HEIGHT; y++) {
            Matter section = sections[y >> 4];
            if (section == null) {
                continue;
            }
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    MatterCavern cell = section.<MatterCavern>slice(MatterCavern.class).get(x, y & 15, z);
                    if (cell != null) {
                        carved++;
                        hash = (hash * 31) + (((long) y << 8) | (x << 4) | z) + (cell.getLiquid() * 7919L);
                    }
                }
            }
        }
        return chunk[0] + "," + chunk[1] + " carved=" + carved + " hash=" + hash;
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    @SuppressWarnings("unchecked")
    private Harness harness(boolean baked, boolean regionLists, AtomicInteger scalarHeights) {
        IrisMatterSupport.ensureRegistered();
        Engine engine = mock(Engine.class);
        EngineMantle engineMantle = mock(EngineMantle.class);
        when(engineMantle.getEngine()).thenReturn(engine);
        MantleCarvingComponent component = new MantleCarvingComponent(engineMantle);
        IrisDimension dimension = new IrisDimension().setCaves(new KList<>("dimension-cave")).setRegions(IrisRegionEntry.plainList("region"));
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getWorld()).thenReturn(IrisWorld.builder().minHeight(0).maxHeight(WORLD_HEIGHT).build());
        when(engine.getMetrics()).thenReturn(new EngineMetrics(16));
        when(engine.getHeight()).thenReturn(WORLD_HEIGHT);
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getCarve()).thenReturn(4_711L);
        when(engine.getSeedManager()).thenReturn(seeds);
        IrisData data = data(regionLists);
        when(engine.getData()).thenReturn(data);
        HydrologyRuntime hydrology = FakeHydrology.disabled();
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.allowsMantleChunkWrite(anyInt(), anyInt())).thenReturn(true);
        when(complex.getHydrologyRuntime()).thenReturn(hydrology);
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
        when(storage.getWorldHeight()).thenReturn(WORLD_HEIGHT);
        when(writer.getMantle()).thenReturn(storage);
        Map<Long, Matter[]> chunks = new HashMap<>();
        when(writer.acquireChunk(anyInt(), anyInt())).thenAnswer(call ->
                chunk(chunks.computeIfAbsent(key(call.getArgument(0), call.getArgument(1)), ignored -> new Matter[WORLD_HEIGHT >> 4])));
        return new Harness(component, complex, context, writer, chunks);
    }

    @SuppressWarnings("unchecked")
    private IrisData data(boolean regionLists) {
        IrisData data = mock(IrisData.class);
        ResourceLoader<art.arcane.iris.generation.cave.IrisCave> caves = mock(ResourceLoader.class);
        for (String key : List.of("dimension-cave", "region-cave", "surface-cave", "shallow-cave", "deep-cave")) {
            when(caves.load(key)).thenReturn(TestSettings.of(IrisCave.class, HALLS));
        }
        when(data.getCaveLoader()).thenReturn(caves);
        ResourceLoader<IrisRegion> regionLoader = mock(ResourceLoader.class);
        when(regionLoader.load("region")).thenReturn(regionLists ? listingRegion : plainRegion);
        when(data.getRegionLoader()).thenReturn(regionLoader);
        when(data.getBiomeLoader()).thenReturn(mock(ResourceLoader.class));
        return data;
    }

    @SuppressWarnings("unchecked")
    private static MantleChunk<Matter> chunk(Matter[] sections) {
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(chunk.sectionCount()).thenReturn(sections.length);
        when(chunk.get(anyInt())).thenAnswer(call -> sections[call.<Integer>getArgument(0)]);
        when(chunk.getOrCreate(anyInt())).thenAnswer(call -> {
            int index = call.getArgument(0);
            if (sections[index] == null) {
                sections[index] = new IrisMatter(16, 16, 16);
            }
            return sections[index];
        });
        return chunk;
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
        return Math.floorMod((x >> 3) + (z >> 3), 3) == 0 ? listingRegion : plainRegion;
    }

    private IrisBiome surface(int x, int z) {
        return Math.floorMod(x * 7 + z * 3, 11) < 4 ? listingSurface : plainSurface;
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
                           MantleWriter writer, Map<Long, Matter[]> chunks) {
    }
}
