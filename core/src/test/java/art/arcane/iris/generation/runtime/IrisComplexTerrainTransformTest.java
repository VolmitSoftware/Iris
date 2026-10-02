package art.arcane.iris.generation.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.transform.IrisTerrainTransform;
import art.arcane.iris.generation.terrain.transform.TerrainTransformRegistry;
import art.arcane.iris.generation.terrain.transform.TerrainTransformRuntime;
import art.arcane.iris.generation.terrain.transform.TerrainTransformer;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class IrisComplexTerrainTransformTest {
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
    public void absentProviderRetainsTheOriginalStreamsAndStackHeights() throws Exception {
        try (Fixture fixture = new Fixture(pack(), false)) {
            ProceduralStream<Double> heights = ProceduralStream.ofDouble((x, z) -> 80D);
            ProceduralStream<Integer> rounded = heights.round();
            ProceduralStream<Double> tops = ProceduralStream.ofDouble((x, z) -> 90D);
            ProceduralStream<Double> slopes = ProceduralStream.ofDouble((x, z) -> 4D);
            fixture.complex.setHeightStream(heights);
            fixture.complex.setRoundedHeighteightStream(rounded);
            fixture.complex.setHeightFluidStream(tops);
            fixture.complex.setSlopeStream(slopes);

            assertNull(fixture.complex.getTerrainTransform());
            assertSame(heights, fixture.complex.getHeightStream());
            assertSame(rounded, fixture.complex.getRoundedHeighteightStream());
            assertSame(tops, fixture.complex.getHeightFluidStream());
            assertSame(slopes, fixture.complex.getSlopeStream());
            assertSame(slopes, fixture.complex.getRawSlopeStream());
            fixture.installStack();
            assertEquals(40, fixture.engine.getHeight(0, 0, true));
            assertEquals(50, fixture.engine.getHeight(0, 0, false));
        }
    }

    @Test
    public void capturedStreamsSwitchAtSamplingTimeWhenContentBegins() throws Exception {
        try (Fixture fixture = new Fixture(pack(), true)) {
            fixture.complex.setHeightStream(ProceduralStream.ofDouble((x, z) -> 80D + 2D * x + 3D * z));
            fixture.complex.setRoundedHeighteightStream(ProceduralStream.ofDouble((x, z) -> 81D).round());
            fixture.complex.setHeightFluidStream(ProceduralStream.ofDouble((x, z) -> 90D));
            fixture.complex.setSlopeStream(ProceduralStream.ofDouble((x, z) -> 9D));
            ProceduralStream<Double> heights = fixture.complex.getHeightStream();
            ProceduralStream<Integer> rounded = fixture.complex.getRoundedHeighteightStream();
            ProceduralStream<Double> tops = fixture.complex.getHeightFluidStream();
            ProceduralStream<Double> slopes = fixture.complex.getSlopeStream();
            ChunkContext context = fixture.context();

            try (IrisContext.Scope ignored = IrisContext.open(fixture.engine, 7L, context)) {
                assertEquals(80D, heights.getDouble(0, 0), 0D);
                assertEquals(Integer.valueOf(81), rounded.get(0, 0));
                assertEquals(90D, tops.getDouble(0, 0), 0D);
                assertEquals(9D, slopes.getDouble(0, 0), 0D);
                assertEquals(0, fixture.materializations.get());

                context.beginContent();

                assertEquals(120D, heights.getDouble(0, 0), 0D);
                assertEquals(Integer.valueOf(120), rounded.get(0, 0));
                assertEquals(125D, tops.getDouble(0, 0), 0D);
                assertEquals(0D, slopes.getDouble(0, 0), 0D);
            }
        }
    }

    @Test
    public void absentProviderRunsTerrainTransitionAndContentInTheirOriginalOrder() throws Exception {
        try (Fixture fixture = new Fixture(pack(), false)) {
            EngineMode mode = mock(EngineMode.class, CALLS_REAL_METHODS);
            doReturn(fixture.engine).when(mode).getEngine();
            doReturn(fixture.complex).when(mode).getComplex();
            when(fixture.engine.getPlatformHooks().shouldDisableChunkContextCache(fixture.engine)).thenReturn(true);
            List<String> stages = new ArrayList<>();
            NativeBlockState terrain = mock(NativeBlockState.class);
            NativeBlockState transitioned = mock(NativeBlockState.class);
            EngineStage natural = (x, z, blocks, biomes, multicore, context) -> {
                assertTrue(context.isNaturalTerrain());
                blocks.set(0, 1, 0, terrain);
                stages.add("terrain");
            };
            EngineStage transition = (x, z, blocks, biomes, multicore, context) -> {
                assertTrue(context.isNaturalTerrain());
                assertSame(terrain, blocks.get(0, 1, 0));
                blocks.set(0, 1, 0, transitioned);
                stages.add("transition");
            };
            EngineStage content = (x, z, blocks, biomes, multicore, context) -> {
                assertFalse(context.isNaturalTerrain());
                assertSame(transitioned, blocks.get(0, 1, 0));
                stages.add("content");
            };
            doReturn(new KList<>(natural)).when(mode).getTerrainStages();
            doReturn(transition).when(mode).getTransitionStage();
            doReturn(new KList<>(content)).when(mode).getStages();
            Hunk<NativeBlockState> blocks = Hunk.newArrayHunk(16, 8, 16);
            Hunk<NativeBiome> biomes = Hunk.newArrayHunk(16, 8, 16);

            mode.generate(0, 0, blocks, biomes, false, 7L);

            assertEquals(List.of("terrain", "transition", "content"), stages);
            assertSame(transitioned, blocks.get(0, 1, 0));
        }
    }

    @Test
    public void transformedHeightsOverrideTheStackOnlyOutsideNaturalGeneration() throws Exception {
        try (Fixture fixture = new Fixture(pack(), true)) {
            fixture.installStack();
            assertEquals(120, fixture.engine.getHeight(0, 0, true));
            assertEquals(125, fixture.engine.getHeight(0, 0, false));
            assertEquals(120, Engine.hostHeight(fixture.engine, 0, 0, true));
            assertEquals(125, Engine.hostHeight(fixture.engine, 0, 0, false));

            try (IrisContext.Scope ignored = IrisContext.open(fixture.engine, 7L, fixture.context())) {
                assertEquals(40, fixture.engine.getHeight(0, 0, true));
                assertEquals(50, fixture.engine.getHeight(0, 0, false));
            }
        }
    }

    @Test
    public void mainThreadColdQueriesUseRawHeightsWithoutMaterializingTerrain() throws Exception {
        try (Fixture fixture = new Fixture(pack(), true)) {
            fixture.mainThread.set(true);
            doReturn(7L).when(fixture.engine).getGenerationSessionId();
            fixture.complex.setHeightStream(ProceduralStream.ofDouble((x, z) -> 80D));
            fixture.complex.setHeightFluidStream(ProceduralStream.ofDouble((x, z) -> 90D));
            assertEquals(Integer.MIN_VALUE, fixture.complex.transformedHeight(0, 0, true));
            assertEquals(80D, fixture.complex.getHeightStream().getDouble(0, 0), 0D);
            assertEquals(90D, fixture.complex.getHeightFluidStream().getDouble(0, 0), 0D);
            fixture.ready.set(true);
            assertEquals(120D, fixture.complex.getHeightStream().getDouble(0, 0), 0D);
            assertEquals(125D, fixture.complex.getHeightFluidStream().getDouble(0, 0), 0D);
            assertEquals(0, fixture.materializations.get());
        }
    }

    @Test
    public void mainThreadContentQueriesMaterializeUncachedNeighborTerrain() throws Exception {
        try (Fixture fixture = new Fixture(pack(), true)) {
            fixture.mainThread.set(true);
            ChunkContext context = fixture.context();
            context.beginContent();

            try (IrisContext.Scope ignored = IrisContext.open(fixture.engine, 0L, context)) {
                assertEquals(120, fixture.complex.transformedHeight(16, 0, true));
                assertEquals(125, fixture.complex.transformedHeight(16, 0, false));
                assertEquals(2, fixture.materializations.get());
            }
        }
    }

    @Test
    public void originalSlopeIgnoresContentHeightOverrides() throws Exception {
        try (Fixture fixture = new Fixture(pack(), true)) {
            fixture.complex.setHeightStream(ProceduralStream.ofDouble((x, z) -> 80D + x + z));
            ProceduralStream<Double> originalSlope = fixture.complex.getRawSlopeStream();
            ChunkContext context = fixture.context();
            context.setTerrainHeight(0, 0, 200);
            context.setTerrainHeight(3, 0, 250);
            context.setTerrainHeight(0, 3, 300);
            context.beginContent();

            try (IrisContext.Scope ignored = IrisContext.open(fixture.engine, 7L, context)) {
                assertEquals(Math.sqrt(18D), originalSlope.getDouble(0, 0), 0.000001D);
                assertEquals(0, fixture.materializations.get());
            }
        }
    }

    @Test
    public void admittedMainThreadQueriesWaitUntilTheirComplexAndModeArePublished() throws Exception {
        try (Fixture fixture = new Fixture(pack(), true)) {
            fixture.mainThread.set(true);
            doReturn(null).when(fixture.engine).getComplex();
            try (IrisContext.Scope ignored = IrisContext.open(fixture.engine, 7L, null)) {
                assertEquals(Integer.MIN_VALUE, fixture.complex.transformedHeight(16, 0, true));
                doReturn(fixture.complex).when(fixture.engine).getComplex();
                assertEquals(Integer.MIN_VALUE, fixture.complex.transformedHeight(16, 0, true));
                assertEquals(0, fixture.materializations.get());

                doReturn(mock(EngineMode.class)).when(fixture.engine).getMode();
                assertEquals(120, fixture.complex.transformedHeight(16, 0, true));
                assertEquals(1, fixture.materializations.get());
            }
        }
    }

    private Path pack() throws Exception {
        Path pack = temporaryFolder.newFolder().toPath();
        Files.createDirectories(pack.resolve("dimensions"));
        Files.createDirectories(pack.resolve("regions"));
        Files.createDirectories(pack.resolve("biomes"));
        Files.createDirectories(pack.resolve("generators"));
        Files.writeString(pack.resolve("dimensions/main.json"), """
                {"focus":"plain","regions":["plain"],"landChance":1,
                 "carvingEnabled":false,"hydrology":{"rivers":{"enabled":false}}}
                """);
        Files.writeString(pack.resolve("regions/plain.json"), "{\"landBiomes\":[\"plain\"]}");
        Files.writeString(pack.resolve("generators/flat.json"), "{\"composite\":[]}");
        Files.writeString(pack.resolve("biomes/plain.json"),
                "{\"generators\":[{\"generator\":\"flat\",\"min\":10,\"max\":10}]}");
        return pack;
    }

    private static final class Fixture implements AutoCloseable {
        private final Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        private final AtomicBoolean mainThread = new AtomicBoolean();
        private final AtomicBoolean ready = new AtomicBoolean();
        private final AtomicInteger materializations = new AtomicInteger();
        private final MockedStatic<B> blocks = mockStatic(B.class);
        private final MockedStatic<TerrainTransformRegistry> registries = mockStatic(TerrainTransformRegistry.class);
        private final MockedConstruction<TerrainTransformRuntime> runtimes;
        private final IrisData data;
        private final IrisComplex complex;

        private Fixture(Path pack, boolean transformed) {
            runtimes = mockConstruction(TerrainTransformRuntime.class, (runtime, context) -> {
                when(runtime.height(anyInt(), anyInt(), anyBoolean())).thenAnswer(invocation -> {
                    materializations.incrementAndGet();
                    return invocation.<Boolean>getArgument(2) ? 120 : 125;
                });
                when(runtime.readyHeight(anyInt(), anyInt(), anyBoolean())).thenAnswer(invocation ->
                        ready.get() ? invocation.<Boolean>getArgument(2) ? 120 : 125 : Integer.MIN_VALUE);
            });
            NativeBlockState block = mock(NativeBlockState.class);
            when(block.isFluid()).thenReturn(true);
            blocks.when(() -> B.getState(anyString())).thenReturn(block);
            blocks.when(() -> B.getStateOrNull(anyString(), eq(false))).thenReturn(block);
            data = IrisData.openRuntime(pack.toFile());
            IrisDimension dimension = data.getDimensionLoader().load("main");
            if (transformed) {
                IrisTerrainTransform descriptor = new IrisTerrainTransform().setId("test").setVersion("1");
                dimension.setTerrainTransform(descriptor);
                TerrainTransformRegistry registry = mock(TerrainTransformRegistry.class);
                when(registry.resolve(descriptor)).thenReturn(mock(TerrainTransformer.class));
                registries.when(TerrainTransformRegistry::discover).thenReturn(registry);
            }
            doReturn(data).when(engine).getData();
            doReturn(dimension).when(engine).getDimension();
            doReturn(data.getBiomeLoader().load("plain")).when(engine).getFocus();
            doReturn(384).when(engine).getHeight();
            doReturn(-64).when(engine).getMinHeight();
            doReturn(320).when(engine).getMaxHeight();
            EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
            when(hooks.isMainThread()).thenAnswer(ignored -> mainThread.get());
            doReturn(hooks).when(engine).getPlatformHooks();
            doReturn(new SeedManager(1337L)).when(engine).getSeedManager();
            complex = new IrisComplex(engine);
            doReturn(complex).when(engine).getComplex();
        }

        private ChunkContext context() {
            return new ChunkContext(0, 0, complex, 7L, false, ChunkContext.PrefillPlan.NONE, null);
        }

        private void installStack() {
            DimensionStackContext stack = mock(DimensionStackContext.class);
            when(stack.getStackTerrainHeight(0, 0)).thenReturn(40);
            when(stack.getStackTopHeight(0, 0)).thenReturn(50);
            doReturn(stack).when(engine).getDimensionStackContext();
        }

        @Override
        public void close() {
            try {
                complex.close();
                data.close();
            } finally {
                runtimes.close();
                registries.close();
                blocks.close();
            }
        }
    }
}
