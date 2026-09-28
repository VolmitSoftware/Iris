package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingEntry;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class EngineBiomeColumnTest {
    private static final int SAMPLES = 96;

    @BeforeClass
    public static void initializeMantleBlockState() throws Exception {
        NativeBlockState air = mock(NativeBlockState.class);
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getState("AIR")).thenReturn(air);
            Class.forName(EngineMantle.class.getName());
        }
    }

    @Test
    public void columnMatchesPointLookupsAcrossCavesSurfacesAndMantleBiomes() {
        Random random = new Random(1337L);
        for (int trial = 0; trial < 48; trial++) {
            Engine engine = randomEngine(random, trial);
            for (int column = 0; column < 24; column++) {
                int x = random.nextInt(4096) - 2048;
                int z = random.nextInt(4096) - 2048;
                IrisBiome[] biomes = new IrisBiome[SAMPLES];
                IrisRegion[] regions = new IrisRegion[SAMPLES];
                engine.getBiomeOrMantleColumn(x, z, 4, biomes, regions);
                for (int index = 0; index < SAMPLES; index++) {
                    assertSame(engine.getBiomeOrMantle(x, index * 4, z), biomes[index]);
                    assertSame(engine.getRegion(x, index * 4, z), regions[index]);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Engine randomEngine(Random random, int trial) {
        long salt = random.nextLong();
        IrisBiome[] surfaces = {biome("iris:plains", 0), biome("iris:desert", 0), biome("iris:forest", 0)};
        IrisBiome[] caves = {biome("iris:lush_caves", 0), biome("iris:deep_dark", 24), new IrisBiome(), null};
        IrisRegion[] regions = {region("iris:temperate"), region("iris:arid")};
        IrisBiome custom = biome("iris:custom_cave", 0);
        IrisBiome configured = biome("iris:configured_cave", 0);

        ProceduralStream<IrisBiome> surfaceStream = mock(ProceduralStream.class);
        when(surfaceStream.get(anyDouble(), anyDouble())).thenAnswer(call ->
                surfaces[pick(salt, call.getArgument(0), call.getArgument(1), 1, surfaces.length)]);
        ProceduralStream<IrisBiome> caveStream = mock(ProceduralStream.class);
        when(caveStream.get(anyDouble(), anyDouble())).thenAnswer(call ->
                caves[pick(salt, call.getArgument(0), call.getArgument(1), 2, caves.length)]);
        ProceduralStream<IrisRegion> regionStream = mock(ProceduralStream.class);
        when(regionStream.get(anyDouble(), anyDouble())).thenAnswer(call ->
                regions[pick(salt, call.getArgument(0), call.getArgument(1), 3, regions.length)]);
        ProceduralStream<Double> heightStream = mock(ProceduralStream.class);
        when(heightStream.get(anyDouble(), anyDouble())).thenAnswer(call ->
                (double) (40 + pick(salt, call.getArgument(0), call.getArgument(1), 4, 300)));

        IrisComplex complex = mock(IrisComplex.class);
        when(complex.getTrueBiomeStream()).thenReturn(surfaceStream);
        when(complex.getCaveBiomeStream()).thenReturn(caveStream);
        when(complex.getRegionStream()).thenReturn(regionStream);
        when(complex.getHeightStream()).thenReturn(heightStream);
        when(complex.isTerrain3DSurface(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                pick(salt, (int) call.getArgument(0) * 31 + (int) call.getArgument(1), (int) call.getArgument(2), 5, 9) == 0);

        Mantle<Matter> mantle = mock(Mantle.class);
        when(mantle.getWorldHeight()).thenReturn(384);
        when(mantle.getLoadedRegions()).thenReturn(new KMap<>());
        when(mantle.hasTectonicPlate(anyInt(), anyInt())).thenReturn(true);
        when(mantle.get(anyInt(), anyInt(), anyInt(), eq(MatterCavern.class))).thenAnswer(call ->
                cavern(salt, call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        when(mantle.getChunk(anyInt(), anyInt())).thenAnswer(call -> {
            int chunkX = call.getArgument(0);
            int chunkZ = call.getArgument(1);
            MantleChunk<Matter> chunk = mock(MantleChunk.class);
            when(chunk.use()).thenReturn(chunk);
            when(chunk.get(anyInt(), anyInt(), anyInt(), eq(MatterCavern.class))).thenAnswer(read -> cavern(salt,
                    chunkX * 16 + (int) read.getArgument(0), read.getArgument(1), chunkZ * 16 + (int) read.getArgument(2)));
            return chunk;
        });
        EngineMantle engineMantle = mock(EngineMantle.class);
        when(engineMantle.getMantle()).thenReturn(mantle);
        doAnswer(call -> 20 + pick(salt, (int) call.getArgument(0), (int) call.getArgument(1), 7, 330))
                .when(engineMantle).getHighest(anyInt(), anyInt(), any(IrisData.class), anyBoolean());

        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisBiome> loader = mock(ResourceLoader.class);
        when(data.getBiomeLoader()).thenReturn(loader);
        when(loader.load(anyString())).thenReturn(null);
        when(loader.load("iris:custom_cave")).thenReturn(custom);
        when(loader.load("iris:configured_cave")).thenReturn(configured);

        IrisDimension dimension = new IrisDimension();
        if (trial % 2 == 1) {
            IrisDimensionCarvingEntry root = new IrisDimensionCarvingEntry();
            root.setBiome("iris:configured_cave");
            int bottom = -64 + random.nextInt(120);
            root.setWorldYRange(new IrisRange(bottom, bottom + 8 + random.nextInt(80)));
            root.setChildRecursionDepth(0);
            dimension.setCarving(new KList<>(List.of(root)));
        }

        EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        doReturn(complex).when(engine).getComplex();
        doReturn(engineMantle).when(engine).getMantle();
        doReturn(hooks).when(engine).getPlatformHooks();
        doReturn(data).when(engine).getData();
        doReturn(dimension).when(engine).getDimension();
        doReturn(IrisWorld.builder().minHeight(-64).maxHeight(320).build()).when(engine).getWorld();
        return engine;
    }

    private static MatterCavern cavern(long salt, int x, int y, int z) {
        return switch (pick(salt, x * 17 + y, z, 6, 12)) {
            case 0 -> new MatterCavern(true, "iris:custom_cave", (byte) 0);
            case 1 -> new MatterCavern(true, "iris:missing_cave", (byte) 1);
            case 2 -> new MatterCavern(true, "", (byte) 0);
            default -> null;
        };
    }

    private static int pick(long salt, double x, double z, int channel, int bound) {
        long mixed = salt ^ ((long) x * 0x9E3779B97F4A7C15L) ^ ((long) z * 0xC2B2AE3D27D4EB4FL) ^ channel * 0x165667B19E3779F9L;
        mixed ^= mixed >>> 33;
        mixed *= 0xFF51AFD7ED558CCDL;
        mixed ^= mixed >>> 33;
        return (int) Math.floorMod(mixed, (long) bound);
    }

    private static IrisBiome biome(String key, int caveMinDepth) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(key);
        biome.setCaveMinDepthBelowSurface(caveMinDepth);
        return biome;
    }

    private static IrisRegion region(String key) {
        IrisRegion region = new IrisRegion();
        region.setLoadKey(key);
        return region;
    }
}
