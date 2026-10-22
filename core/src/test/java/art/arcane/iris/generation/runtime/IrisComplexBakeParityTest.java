package art.arcane.iris.generation.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.bake.BakePlane;
import art.arcane.iris.generation.bake.GeneratorEvaluations;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.image.IrisImageMapApplication;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class IrisComplexBakeParityTest {
    @ClassRule public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisSettings previousSettings;

    @Before
    public void prepareRuntime() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisServices.register(PreservationRegistry.class, mock(PreservationRegistry.class));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.platformName()).thenReturn("bukkit");
        IrisPlatforms.bind(platform);
    }

    @After
    public void releaseRuntime() {
        IrisPlatforms.unbind();
        IrisServices.remove(PreservationRegistry.class);
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void batchedChunkBaseHeightsEqualScalarHeightsBitForBit() throws Exception {
        try (Statics ignored = new Statics(); Fixture batched = new Fixture(pack()); Fixture scalar = new Fixture(pack())) {
            SplittableRandom random = new SplittableRandom(0xBA5EL);
            for (int chunk = 0; chunk < 48; chunk++) {
                int chunkX = random.nextInt(-400, 400);
                int chunkZ = random.nextInt(-400, 400);
                double[] baked = (double[]) batched.complex.bakePlane(BakePlane.BASE_HEIGHT, chunkX, chunkZ);
                for (int index = 0; index < 256; index++) {
                    int x = (chunkX << 4) + (index & 15);
                    int z = (chunkZ << 4) + (index >> 4);
                    assertEquals("chunk " + chunkX + "," + chunkZ + " column " + index,
                            Double.doubleToRawLongBits(scalar.complex.getBaseTerrainHeightStream().getDouble(x, z)),
                            Double.doubleToRawLongBits(baked[index]));
                }
            }
        }
    }

    @Test
    public void batchedChunksEvaluateExactlyTheScalarGeneratorSet() throws Exception {
        try (Statics ignored = new Statics(); Fixture batched = new Fixture(pack()); Fixture scalar = new Fixture(pack())) {
            SplittableRandom random = new SplittableRandom(0xC0DEL);
            long totalBatched = 0L;
            for (int chunk = 0; chunk < 32; chunk++) {
                int chunkX = random.nextInt(-400, 400);
                int chunkZ = random.nextInt(-400, 400);
                GeneratorEvaluations.Snapshot before = GeneratorEvaluations.snapshot();
                batched.complex.bakePlane(BakePlane.BASE_HEIGHT, chunkX, chunkZ);
                GeneratorEvaluations.Snapshot afterBatch = GeneratorEvaluations.snapshot();
                for (int index = 0; index < 256; index++) {
                    scalar.complex.getBaseTerrainHeightStream().getDouble((chunkX << 4) + (index & 15), (chunkZ << 4) + (index >> 4));
                }
                GeneratorEvaluations.Snapshot afterScalar = GeneratorEvaluations.snapshot();
                long batchedEvaluations = afterBatch.minus(before).bakedEvaluations();
                assertEquals(0L, afterBatch.minus(before).scalarEvaluations());
                assertEquals("chunk " + chunkX + "," + chunkZ, batchedEvaluations,
                        afterScalar.minus(afterBatch).scalarEvaluations());
                totalBatched += batchedEvaluations;
            }
            assertTrue(totalBatched > 0L);
        }
    }

    private Path pack() throws Exception {
        Path pack = temporaryFolder.newFolder().toPath();
        Files.createDirectories(pack.resolve("dimensions"));
        Files.createDirectories(pack.resolve("regions"));
        Files.createDirectories(pack.resolve("biomes"));
        Files.createDirectories(pack.resolve("generators"));
        Files.writeString(pack.resolve("dimensions/main.json"), """
                {"regions":["alpha","beta"],"landChance":0.55,"regionZoom":0.2,"biomeZoom":0.15,
                 "carvingEnabled":false,"hydrology":{"rivers":{"enabled":false}}}
                """);
        Files.writeString(pack.resolve("regions/alpha.json"),
                "{\"landBiomes\":[\"peaks\",\"meadow\",\"dunes\"],\"seaBiomes\":[\"sea\"],\"rarity\":1}");
        Files.writeString(pack.resolve("regions/beta.json"),
                "{\"landBiomes\":[\"meadow\",\"spires\"],\"seaBiomes\":[\"sea\"],\"rarity\":2}");
        generator(pack, "mountain", "BILINEAR_STARCAST_9", 52, 0.5D, """
                [{"style":{"style":"FRACTAL_SMOKE","zoom":1},"seed":3356}]""", "");
        generator(pack, "magnet", "BILINEAR_STARCAST_9", 52, 0.5D, """
                [{"style":{"style":"FRACTAL_SMOKE","zoom":0.6},"seed":911}]""", "");
        generator(pack, "plain", "BILINEAR_STARCAST_9", 12, 0.5D, """
                [{"style":{"style":"IRIS_DOUBLE","zoom":0.87,"fracture":{"style":"NOWHERE","zoom":0.195,"multiplier":18}},"seed":10056,"bezier":true}]""", "");
        generator(pack, "cliffs", "BILINEAR_STARCAST_9", 12, 0.5D, """
                [{"style":{"style":"GLOB","zoom":0.4,"exponent":0.7,"fracture":{"style":"NOWHERE","zoom":0.1,"multiplier":5}},"negative":true,"seed":10056}]""",
                ",\"cliffHeightMax\":80,\"cliffHeightMin\":35,\"cliffHeightGenerator\":{\"seed\":2348,\"style\":{\"style\":\"CELLULAR_HEIGHT\"},\"zoom\":0.4}");
        generator(pack, "dunes", "BILINEAR_STARCAST_9", 23, 1D, """
                [{"style":{"style":"SIMPLEX","zoom":2},"seed":77,"fracture":[{"style":{"style":"PERLIN"},"seed":5,"opacity":2.5}]}]""", "");
        generator(pack, "spikes", "STARCAST_12", 42, 0D, """
                [{"style":{"style":"CELLULAR","zoom":0.5},"seed":4}]""", "");
        biome(pack, "peaks", "[{\"generator\":\"mountain\",\"min\":40,\"max\":120},{\"generator\":\"spikes\",\"min\":0,\"max\":20}]",
                ",\"children\":[\"meadow\"],\"childShrinkFactor\":1.5");
        biome(pack, "meadow", "[{\"generator\":\"plain\",\"min\":2,\"max\":14},{\"generator\":\"cliffs\",\"min\":0,\"max\":9}]", "");
        biome(pack, "dunes", "[{\"generator\":\"dunes\",\"min\":3,\"max\":30},{\"generator\":\"magnet\",\"min\":-4,\"max\":6}]", "");
        biome(pack, "spires", "[{\"generator\":\"spikes\",\"min\":10,\"max\":60},{\"generator\":\"plain\",\"min\":1,\"max\":4}]", "");
        biome(pack, "sea", "[{\"generator\":\"mountain\",\"min\":-32,\"max\":-10}]", "");
        return pack;
    }

    private static void generator(Path pack, String key, String function, double scale, double detail,
                                  String composite, String extra) throws Exception {
        Files.writeString(pack.resolve("generators/" + key + ".json"), "{\"surfaceDetail\":" + detail
                + ",\"interpolator\":{\"function\":\"" + function + "\",\"horizontalScale\":" + scale + "}"
                + ",\"seed\":" + Math.abs(key.hashCode()) + ",\"composite\":" + composite.strip() + extra + "}");
    }

    private static void biome(Path pack, String key, String generators, String extra) throws Exception {
        Files.writeString(pack.resolve("biomes/" + key + ".json"), "{\"generators\":" + generators + extra + "}");
    }

    private static final class Statics implements AutoCloseable {
        private final MockedStatic<B> blocks = mockStatic(B.class);
        private final MockedStatic<IrisImageMapRuntime> imageRuntimes = mockStatic(IrisImageMapRuntime.class);

        private Statics() {
            NativeBlockState block = mock(NativeBlockState.class);
            when(block.isFluid()).thenReturn(true);
            blocks.when(() -> B.getState(anyString())).thenReturn(block);
            blocks.when(() -> B.getStateOrNull(anyString(), eq(false))).thenReturn(block);
            IrisImageMapRuntime images = mock(IrisImageMapRuntime.class);
            when(images.has(IrisImageMapApplication.BIOME)).thenReturn(false);
            when(images.sampleTerrainHeight(anyDouble(), anyDouble(), anyDouble()))
                    .thenAnswer(invocation -> invocation.<Double>getArgument(2));
            imageRuntimes.when(() -> IrisImageMapRuntime.compile(any(Engine.class))).thenReturn(images);
        }

        @Override
        public void close() {
            imageRuntimes.close();
            blocks.close();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        private final EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        private final IrisData data;
        private final IrisComplex complex;

        private Fixture(Path pack) {
            data = IrisData.openRuntime(pack.toFile());
            IrisDimension dimension = data.getDimensionLoader().load("main");
            doReturn(data).when(engine).getData();
            doReturn(dimension).when(engine).getDimension();
            doReturn(384).when(engine).getHeight();
            doReturn(-64).when(engine).getMinHeight();
            doReturn(320).when(engine).getMaxHeight();
            when(hooks.isMainThread()).thenReturn(false);
            doReturn(hooks).when(engine).getPlatformHooks();
            doReturn(new SeedManager(1337L)).when(engine).getSeedManager();
            complex = new IrisComplex(engine);
            doReturn(complex).when(engine).getComplex();
        }

        @Override
        public void close() {
            complex.close();
            data.close();
        }
    }
}
