package art.arcane.iris.generation.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.context.ChunkedDoubleDataCache;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.HydrologyColumnSnapshot;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.generation.image.IrisImageMapApplication;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.stream.ChunkFillableDoubleStream2D;
import art.arcane.iris.generation.stream.ChunkFillableStream2D;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisComplexHeightStreamTest {
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
    public void sharedCornersRemainUnallocatedUntilTerrainSamplesThem() throws Exception {
        try (Fixture fixture = new Fixture(pack())) {
            assertNull(fixture.complex.getSharedCornerBounds());
            fixture.complex.getBaseTerrainHeightStream().getDouble(0, 0);
            assertNotNull(fixture.complex.getSharedCornerBounds());
        }
    }

    @Test
    public void mappedBiomesSkipProceduralSelectionAndNaturalHeight() throws Exception {
        try (Fixture fixture = new Fixture(pack())) {
            IrisBiome mapped = new IrisBiome();
            when(fixture.images.sampleBiome(anyDouble(), anyDouble())).thenReturn(mapped);
            assertSame(mapped, fixture.complex.getBaseBiomeStream().get(-16, 32));
            assertSame(mapped, fixture.complex.getBaseBiomeStream().get(-16.75, 10, 32.5));
            assertSame(mapped, fixture.complex.getNaturalTrueBiomeStream().get(-16, 32));
            assertSame(mapped, fixture.complex.getNaturalTrueBiomeStream().get(-16.75, 10, 32.5));
            assertNull(fixture.complex.getSharedCornerBounds());

            when(fixture.images.sampleBiome(anyDouble(), anyDouble())).thenReturn(null);
            assertEquals("plain", fixture.complex.getBaseBiomeStream().get(16, 32).getLoadKey());
            assertEquals("plain", fixture.complex.getNaturalTrueBiomeStream().get(16, 32).getLoadKey());
            assertNotNull(fixture.complex.getSharedCornerBounds());
        }
    }

    @Test
    public void workerPrefillUsesOneThreadCheckAndRetainsRoundedValues() throws Exception {
        try (Fixture fixture = new Fixture(pack())) {
            AtomicInteger samples = fixture.installNaturalHeight();
            ChunkedDoubleDataCache cache = new ChunkedDoubleDataCache(fixture.complex.getRawHeightStream(), -16, 32);
            int[] rounded = new int[256];
            clearInvocations(fixture.hooks);
            cache.fillRounded(rounded);
            verify(fixture.hooks, atMost(2)).isMainThread();
            assertEquals(256, samples.get());
            for (int index = 0; index < 256; index++) {
                int x = index & 15;
                int z = index >> 4;
                double expected = height(-16 + x, 32 + z);
                assertBits(expected, cache.getDouble(x, z));
                assertEquals((int) Math.round(expected), rounded[index]);
            }
            clearInvocations(fixture.hooks);
            cache.fill();
            verify(fixture.hooks, atMost(2)).isMainThread();
            assertEquals(256, samples.get());
        }
    }

    @Test
    public void mainThreadPrefillNeverWaitsOrConsumesCachedHydrology() throws Exception {
        try (Fixture fixture = new Fixture(pack())) {
            fixture.installNaturalHeight();
            IrisHydrologyRuntime hydrology = mock(IrisHydrologyRuntime.class);
            HydrologyColumnSample planned = mock(HydrologyColumnSample.class);
            when(planned.terrainHeight()).thenReturn(100);
            when(hydrology.sample(anyDouble(), anyDouble())).thenAnswer(invocation -> {
                if (fixture.mainThread.get()) {
                    throw new AssertionError("Main-thread sampling entered blocking hydrology");
                }
                return Optional.of(planned);
            });
            when(hydrology.sampleSnapshot(anyInt(), anyInt())).thenAnswer(invocation -> {
                ProvisionalSampling.mark();
                return HydrologyColumnSnapshot.unavailable();
            });
            fixture.complex.setHydrologyRuntime(hydrology);
            ProceduralStream<Double> stream = fixture.complex.getRawHeightStream();
            ChunkFillableDoubleStream2D bulk = (ChunkFillableDoubleStream2D) stream;
            double[] values = new double[256];
            fixture.mainThread.set(true);
            assertThrows(ProvisionalSampling.Unmemoizable.class, () -> ProvisionalSampling.memoizable(() -> {
                bulk.fillChunkDoubles(-16, 32, values);
                return values[0];
            }));
            assertBits(height(-16, 32), values[0]);
            fixture.mainThread.set(false);
            bulk.fillChunkDoubles(-16, 32, values);
            assertBits(100D, values[0]);
            assertBits(100D, values[255]);
            verify(hydrology, times(256)).sample(anyDouble(), anyDouble());

            fixture.mainThread.set(true);
            bulk.fillChunkDoubles(-16, 32, values);
            assertBits(height(-16, 32), values[0]);
            assertBits(height(-16.75, 32.5), stream.getDouble(-16.75, 10, 32.5));
            assertBits(height(-16.75, 32.5), stream.get(-16.75, 10, 32.5));
            Object[] boxed = new Object[256];
            ((ChunkFillableStream2D) stream).fillChunkRaw(-16, 32, boxed);
            assertBits(height(-1, 47), (Double) boxed[255]);
            verify(hydrology, times(256)).sample(anyDouble(), anyDouble());
        }
    }

    @Test
    public void unalignedPrefillAndThreeDimensionalReadsRetainColumnCoordinates() throws Exception {
        try (Fixture fixture = new Fixture(pack())) {
            fixture.installNaturalHeight();
            ProceduralStream<Double> stream = fixture.complex.getRawHeightStream();
            double[] values = new double[256];
            ((ChunkFillableDoubleStream2D) stream).fillChunkDoubles(-17, 31, values);
            for (int index = 0; index < 256; index++) {
                assertBits(height(-17 + (index & 15), 31 + (index >> 4)), values[index]);
            }
            assertBits(height(-16, 32), stream.getDouble(-16.75, 10, 32.5));
            assertBits(height(-16, 32), stream.get(-16.75, 10, 32.5));
        }
    }

    @Test
    public void workerPrefillDoesNotMemoizeProvisionalNaturalValues() throws Exception {
        try (Fixture fixture = new Fixture(pack())) {
            AtomicBoolean ready = new AtomicBoolean();
            AtomicInteger samples = new AtomicInteger();
            fixture.complex.setNaturalHeightStream(ProceduralStream.ofDouble((x, z) -> {
                samples.incrementAndGet();
                if (!ready.get()) {
                    ProvisionalSampling.mark();
                    return -1D;
                }
                return height(x, z);
            }));
            ChunkFillableDoubleStream2D stream = (ChunkFillableDoubleStream2D) fixture.complex.getRawHeightStream();
            double[] values = new double[256];
            stream.fillChunkDoubles(-16, 32, values);
            for (double value : values) {
                assertBits(-1D, value);
            }
            ready.set(true);
            int before = samples.get();
            stream.fillChunkDoubles(-16, 32, values);
            assertEquals(before + 256, samples.get());
            for (int index = 0; index < 256; index++) {
                assertBits(height(-16 + (index & 15), 32 + (index >> 4)), values[index]);
            }
            stream.fillChunkDoubles(-16, 32, values);
            assertEquals(before + 256, samples.get());
        }
    }

    private Path pack() throws Exception {
        Path pack = temporaryFolder.newFolder().toPath();
        Files.createDirectories(pack.resolve("dimensions"));
        Files.createDirectories(pack.resolve("regions"));
        Files.createDirectories(pack.resolve("biomes"));
        Files.createDirectories(pack.resolve("generators"));
        Files.writeString(pack.resolve("dimensions/main.json"), """
                {"regions":["plain"],"landChance":1,
                 "carvingEnabled":false,"hydrology":{"rivers":{"enabled":false}}}
                """);
        Files.writeString(pack.resolve("regions/plain.json"), "{\"landBiomes\":[\"plain\"]}");
        Files.writeString(pack.resolve("generators/flat.json"), "{\"composite\":[]}");
        Files.writeString(pack.resolve("biomes/plain.json"),
                "{\"generators\":[{\"generator\":\"flat\",\"min\":10,\"max\":10}]}");
        return pack;
    }

    private static double height(double x, double z) {
        return x * 0.125D - z * 0.03125D;
    }

    private static void assertBits(double expected, double actual) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
    }

    private static final class Fixture implements AutoCloseable {
        private final Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        private final EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        private final AtomicBoolean mainThread = new AtomicBoolean();
        private final MockedStatic<B> blocks = mockStatic(B.class);
        private final MockedStatic<IrisImageMapRuntime> imageRuntimes = mockStatic(IrisImageMapRuntime.class);
        private final IrisImageMapRuntime images = mock(IrisImageMapRuntime.class);
        private final IrisData data;
        private final IrisComplex complex;

        private Fixture(Path pack) {
            NativeBlockState block = mock(NativeBlockState.class);
            when(block.isFluid()).thenReturn(true);
            blocks.when(() -> B.getState(anyString())).thenReturn(block);
            blocks.when(() -> B.getStateOrNull(anyString(), eq(false))).thenReturn(block);
            imageRuntimes.when(() -> IrisImageMapRuntime.compile(engine)).thenReturn(images);
            when(images.has(IrisImageMapApplication.BIOME)).thenReturn(true);
            when(images.sampleTerrainHeight(anyDouble(), anyDouble(), anyDouble()))
                    .thenAnswer(invocation -> invocation.<Double>getArgument(2));
            data = IrisData.openRuntime(pack.toFile());
            IrisDimension dimension = data.getDimensionLoader().load("main");
            doReturn(data).when(engine).getData();
            doReturn(dimension).when(engine).getDimension();
            doReturn(384).when(engine).getHeight();
            doReturn(-64).when(engine).getMinHeight();
            doReturn(320).when(engine).getMaxHeight();
            when(hooks.isMainThread()).thenAnswer(ignored -> mainThread.get());
            doReturn(hooks).when(engine).getPlatformHooks();
            doReturn(new SeedManager(1337L)).when(engine).getSeedManager();
            complex = new IrisComplex(engine);
            doReturn(complex).when(engine).getComplex();
        }

        private AtomicInteger installNaturalHeight() {
            AtomicInteger samples = new AtomicInteger();
            complex.setNaturalHeightStream(ProceduralStream.ofDouble((x, z) -> {
                samples.incrementAndGet();
                return height(x, z);
            }));
            return samples;
        }

        @Override
        public void close() {
            try {
                complex.close();
                data.close();
            } finally {
                imageRuntimes.close();
                blocks.close();
            }
        }
    }
}
