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
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Random;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class EngineBiomeColumnTest {
    private static final int SAMPLES = 96;
    private static MantleDataAdapter<Matter> chunkAdapter;
    private static final EnginePlatformHooks PLATFORM_HOOKS = new EnginePlatformHooks() {};

    @BeforeClass
    public static void initializeMantleBlockState() throws Exception {
        NativeBlockState air = mock(NativeBlockState.class, withSettings().stubOnly());
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getState("AIR")).thenReturn(air);
            Class.forName(EngineMantle.class.getName());
            chunkAdapter = IrisEngineMantle.createRuntimeDataAdapter(null);
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

        Interpolated<IrisBiome> biomeInterpolation = Interpolated.of(biome -> 0D, value -> null);
        Interpolated<IrisRegion> regionInterpolation = Interpolated.of(region -> 0D, value -> null);
        ProceduralStream<IrisBiome> surfaceStream = ProceduralStream.of((x, z) ->
                surfaces[pick(salt, x, z, 1, surfaces.length)], biomeInterpolation);
        ProceduralStream<IrisBiome> caveStream = ProceduralStream.of((x, z) ->
                caves[pick(salt, x, z, 2, caves.length)], biomeInterpolation);
        ProceduralStream<IrisRegion> regionStream = ProceduralStream.of((x, z) ->
                regions[pick(salt, x, z, 3, regions.length)], regionInterpolation);
        ProceduralStream<Double> heightStream = ProceduralStream.of((x, z) ->
                (double) (40 + pick(salt, x, z, 4, 300)), Interpolated.DOUBLE);

        IrisComplex complex = mock(IrisComplex.class, withSettings().stubOnly());
        when(complex.getTrueBiomeStream()).thenReturn(surfaceStream);
        when(complex.getCaveBiomeStream()).thenReturn(caveStream);
        when(complex.getRegionStream()).thenReturn(regionStream);
        when(complex.getHeightStream()).thenReturn(heightStream);
        when(complex.isTerrain3DSurface(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                pick(salt, (int) call.getArgument(0) * 31 + (int) call.getArgument(1), (int) call.getArgument(2), 5, 9) == 0);

        Mantle<Matter> mantle = mock(Mantle.class, withSettings().stubOnly());
        when(mantle.getWorldHeight()).thenReturn(384);
        when(mantle.getLoadedRegions()).thenReturn(new KMap<>());
        when(mantle.hasTectonicPlate(anyInt(), anyInt())).thenReturn(true);
        when(mantle.get(anyInt(), anyInt(), anyInt(), eq(MatterCavern.class))).thenAnswer(call ->
                cavern(salt, call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        when(mantle.useChunk(anyInt(), anyInt())).thenAnswer(call ->
                new CoordinateChunk(salt, new Position2(call.getArgument(0), call.getArgument(1))).use());
        EngineMantle engineMantle = (EngineMantle) Proxy.newProxyInstance(EngineMantle.class.getClassLoader(),
                new Class<?>[]{EngineMantle.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMantle" -> mantle;
                    case "getHighest" -> 20 + pick(salt, (int) arguments[0], (int) arguments[1], 7, 330);
                    default -> throw new UnsupportedOperationException(method.getName());
                });

        IrisData data = mock(IrisData.class, withSettings().stubOnly());
        ResourceLoader<IrisBiome> loader = mock(ResourceLoader.class, withSettings().stubOnly());
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

        IrisWorld world = IrisWorld.builder().minHeight(-64).maxHeight(320).build();
        return (Engine) Proxy.newProxyInstance(Engine.class.getClassLoader(), new Class<?>[]{Engine.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getComplex" -> complex;
                    case "getMantle" -> engineMantle;
                    case "getPlatformHooks" -> PLATFORM_HOOKS;
                    case "getData" -> data;
                    case "getDimension" -> dimension;
                    case "getWorld" -> world;
                    default -> InvocationHandler.invokeDefault(proxy, method, arguments);
                });
    }

    private static final class CoordinateChunk extends MantleChunk<Matter> {
        private final long salt;
        private final Position2 coordinates;

        private CoordinateChunk(long salt, Position2 coordinates) {
            super(24, coordinates.getX(), coordinates.getZ(), chunkAdapter, null);
            this.salt = salt;
            this.coordinates = coordinates;
        }

        @Override
        public <T> T get(int x, int y, int z, Class<T> type) {
            return type == MatterCavern.class
                    ? type.cast(cavern(salt, coordinates.getX() * 16 + x, y, coordinates.getZ() * 16 + z))
                    : null;
        }
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
